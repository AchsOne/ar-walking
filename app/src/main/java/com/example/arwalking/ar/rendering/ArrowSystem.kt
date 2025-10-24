package com.example.arwalking.ar.rendering

import android.content.Context
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.NodeParent
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory

data class ArrowConfig(
    // Geometrie des Chevrons
    val limbLength: Float = 0.48f,        // Länge je „Arm"
    val limbRadius: Float = 0.07f,        // Dicke/Radius je „Arm"
    val chevronAngleDeg: Float = 92f,     // Winkel zwischen den Armen (~90° wie im Bild)

    // Layer-Offsets (für Outline/Bevel-Optik)
    val layerGapZ: Float = 0.028f,        // Abstand der Layer zueinander
    val layerGapY: Float = -0.006f,       // leicht nach unten versetzen (Schatten)
    val backScale: Float = 1.08f,         // Back-Layer minimal größer (weißer Rand)
    val midScale: Float = 1.02f,          // Mid-Layer minimal größer als Top

    // Farben (Lime-Gelbgrün wie im Screenshot)
    val colBack: Color = Color(0.95f, 0.98f, 0.90f), // fast Weiß für Outline
    val colMid:  Color = Color(0.70f, 0.78f, 0.32f), // etwas dunkler
    val colTop:  Color = Color(0.78f, 0.86f, 0.38f), // heller Vorder-Layer

    // Stack-Einstellungen (drei Chevrons untereinander)
    val stackCount: Int = 3,
    val stackOffset: Vector3 = Vector3(0f, 0f, -0.24f), // Versatz je Chevron in die Tiefe (−Z)
    val stackDepthStep: Float = -0.01f,                 // leichter Z-Versatz pro Chevron (pseudo Parallax)

    // Gesamtskalierung
    val globalScale: Float = 0.8f
)

data class ArrowAbbiegenConfig(
    // Geometrie des Chevrons - identisch zu ArrowConfig
    val limbLength: Float = 0.48f,
    val limbRadius: Float = 0.07f,
    val chevronAngleDeg: Float = 92f,

    // Layer-Offsets
    val layerGapZ: Float = 0.028f,
    val layerGapY: Float = -0.006f,
    val backScale: Float = 1.08f,
    val midScale: Float = 1.02f,

    // Farben (Leuchtendes Cyan/Blau mit Glow)
    val colBack: Color = Color(0.75f, 0.85f, 0.95f),  // etwas dunklerer Blau-Weiß-Rand
    val colMid:  Color = Color(0.10f, 0.35f, 0.65f),  // dunkleres Cyan-Blau
    val colTop:  Color = Color(0.20f, 0.55f, 0.85f),  // dunkleres helles Blau

    // Glow-Layer (zusätzlicher äußerer Layer für Leuchteffekt)
    val glowScale: Float = 1.15f,                      // noch größer als Back-Layer
    val colGlow: Color = Color(0.35f, 0.65f, 0.95f, 0.6f), // dunkleres leuchtendes Blau (gleiche Alpha)

    // Stack-Einstellungen
    val stackCount: Int = 3,
    val stackOffset: Vector3 = Vector3(0f, 0f, -0.24f),
    val stackDepthStep: Float = -0.01f,

    // Gesamtskalierung
    val globalScale: Float = 0.8f
)

class ArrowRenderer(
    private val context: Context,
    private val config: ArrowConfig = ArrowConfig()
) {
    val rootNode: Node = Node()
    // Geometry container rotated so that chevrons point forward (-Z) while root stays identity
    private val geom: Node = Node().apply {
        localRotation = Quaternion.identity()
    }
    init {
        rootNode.addChild(geom)
        buildProcedural()
    }

    fun ensureRenderable() {
        if (geom.children.isEmpty()) buildProcedural()
    }

    private fun clearChildren() {
        geom.children.toList().forEach { it.setParent(null) }
    }

    // --- Hilfsfunktion: „Capsule" aus Zylinder + 2 Kugel-Kappen ---
    private fun makeCapsule(length: Float, radius: Float, material: com.google.ar.sceneform.rendering.Material): Node {
        val parent = Node()

        // Zylinder (Default-Achse = Y). Wir drehen später alles auf Z + Heading.
        val cyl = ShapeFactory.makeCylinder(radius, length, Vector3(0f, 0f, 0f), material).apply {
            isShadowCaster = false; isShadowReceiver = false
        }
        val cylNode = Node().apply {
            renderable = cyl
        }
        parent.addChild(cylNode)

        // Kugel-Kappen
        val cap = ShapeFactory.makeSphere(radius, Vector3(0f, 0f, 0f), material).apply {
            isShadowCaster = false; isShadowReceiver = false
        }
        val capTop = Node().apply {
            renderable = cap
            localPosition = Vector3(0f, +length / 2f, 0f)
        }
        val capBottom = Node().apply {
            renderable = cap
            localPosition = Vector3(0f, -length / 2f, 0f)
        }
        parent.addChild(capTop)
        parent.addChild(capBottom)

        return parent
    }

    // Ein Layer = zwei Kapseln, zu einem Chevron zusammengesetzt
    private fun makeChevronLayer(
        length: Float,
        radius: Float,
        angleDeg: Float,
        material: com.google.ar.sceneform.rendering.Material
    ): Node {
        val layer = Node()

        val half = angleDeg / 2f
        val h1 = Math.toRadians(half.toDouble()).toFloat()   // Heading +half
        val h2 = Math.toRadians(-half.toDouble()).toFloat()  // Heading -half

        // Positionsversatz der Limb-Center vom Eckpunkt (0,0,0)
        // Wir bauen die Kapsel entlang Z, also:
        // 1) Zylinder-Achse Y -> auf Z drehen (X-90°)
        // 2) um Y um heading drehen
        val centerOffset = length / 2f

        // Limb 1
        val limb1 = makeCapsule(length, radius, material)
        limb1.localRotation = Quaternion.multiply(
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), Math.toDegrees(h1.toDouble()).toFloat()),
            Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)
        )
        limb1.localPosition = Vector3(
            (Math.sin(h1.toDouble()) * centerOffset).toFloat(),
            0f,
            (Math.cos(h1.toDouble()) * centerOffset).toFloat()
        )
        layer.addChild(limb1)

        // Limb 2
        val limb2 = makeCapsule(length, radius, material)
        limb2.localRotation = Quaternion.multiply(
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), Math.toDegrees(h2.toDouble()).toFloat()),
            Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)
        )
        limb2.localPosition = Vector3(
            (Math.sin(h2.toDouble()) * centerOffset).toFloat(),
            0f,
            (Math.cos(h2.toDouble()) * centerOffset).toFloat()
        )
        layer.addChild(limb2)

        return layer
    }

    // Baut einen Chevron als 3-Layer-Gruppe (Back/Mid/Top)
    private fun makeChevronGroup(
        mBack: com.google.ar.sceneform.rendering.Material,
        mMid: com.google.ar.sceneform.rendering.Material,
        mTop: com.google.ar.sceneform.rendering.Material
    ): Node {
        val group = Node()

        // BACK (weißer Rand / „Sticker"-Look)
        val back = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mBack
        ).apply {
            localScale = Vector3(config.backScale, config.backScale, config.backScale)
            localPosition = Vector3(0f, config.layerGapY * 2f, -config.layerGapZ * 2f)
        }
        group.addChild(back)

        // MID (dunkler)
        val mid = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mMid
        ).apply {
            localScale = Vector3(config.midScale, config.midScale, config.midScale)
            localPosition = Vector3(0f, config.layerGapY, -config.layerGapZ)
        }
        group.addChild(mid)

        // TOP (heller, sichtbar vorne)
        val top = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mTop
        )
        group.addChild(top)

        return group
    }

    fun buildProcedural() {
        MaterialFactory.makeOpaqueWithColor(context, config.colBack).thenAccept { mBack ->
            MaterialFactory.makeOpaqueWithColor(context, config.colMid).thenAccept { mMid ->
                MaterialFactory.makeOpaqueWithColor(context, config.colTop).thenAccept { mTop ->
                    try {
                        clearChildren()

                        // Drei Chevrons als Stack erzeugen
                        for (i in 0 until config.stackCount) {
                            val chevron = makeChevronGroup(mBack, mMid, mTop)

                            // Position je Element im Stack
                            val offset = config.stackOffset
                            val pos = Vector3(
                                offset.x * i,
                                offset.y * i,
                                (offset.z + config.stackDepthStep) * i
                            )
                            chevron.localPosition = pos

                            geom.addChild(chevron)
                        }

                        // Gesamt-Scale kleiner
                        rootNode.localScale = Vector3(config.globalScale, config.globalScale, config.globalScale)

                        Log.d("ArrowRenderer", "Chevron stack (3x) created")
                    } catch (e: Exception) {
                        Log.e("ArrowRenderer", "Failed to build chevron stack: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun setParent(parent: NodeParent?) { rootNode.setParent(parent) }
    fun setWorldPose(position: Vector3, yawDeg: Float) {
        rootNode.worldPosition = position
        rootNode.worldRotation = ArrowOrientation.greenRotation(yawDeg)
    }

}

class ArrowAbbiegen(
    private val context: Context,
    private val config: ArrowAbbiegenConfig = ArrowAbbiegenConfig()
) {
    val rootNode: Node = Node()
    private val geom: Node = Node().apply {
        localRotation = Quaternion.identity()
    }
    init {
        rootNode.addChild(geom)
        buildProcedural()
    }

    fun ensureRenderable() {
        if (geom.children.isEmpty()) buildProcedural()
    }

    private fun clearChildren() {
        geom.children.toList().forEach { it.setParent(null) }
    }

    private fun makeCapsule(length: Float, radius: Float, material: com.google.ar.sceneform.rendering.Material): Node {
        val parent = Node()

        val cyl = ShapeFactory.makeCylinder(radius, length, Vector3(0f, 0f, 0f), material).apply {
            isShadowCaster = false; isShadowReceiver = false
        }
        val cylNode = Node().apply {
            renderable = cyl
        }
        parent.addChild(cylNode)

        val cap = ShapeFactory.makeSphere(radius, Vector3(0f, 0f, 0f), material).apply {
            isShadowCaster = false; isShadowReceiver = false
        }
        val capTop = Node().apply {
            renderable = cap
            localPosition = Vector3(0f, +length / 2f, 0f)
        }
        val capBottom = Node().apply {
            renderable = cap
            localPosition = Vector3(0f, -length / 2f, 0f)
        }
        parent.addChild(capTop)
        parent.addChild(capBottom)

        return parent
    }

    private fun makeChevronLayer(
        length: Float,
        radius: Float,
        angleDeg: Float,
        material: com.google.ar.sceneform.rendering.Material
    ): Node {
        val layer = Node()

        val half = angleDeg / 2f
        val h1 = Math.toRadians(half.toDouble()).toFloat()
        val h2 = Math.toRadians(-half.toDouble()).toFloat()

        val centerOffset = length / 2f

        val limb1 = makeCapsule(length, radius, material)
        limb1.localRotation = Quaternion.multiply(
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), Math.toDegrees(h1.toDouble()).toFloat()),
            Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)
        )
        limb1.localPosition = Vector3(
            (Math.sin(h1.toDouble()) * centerOffset).toFloat(),
            0f,
            (Math.cos(h1.toDouble()) * centerOffset).toFloat()
        )
        layer.addChild(limb1)

        val limb2 = makeCapsule(length, radius, material)
        limb2.localRotation = Quaternion.multiply(
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), Math.toDegrees(h2.toDouble()).toFloat()),
            Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)
        )
        limb2.localPosition = Vector3(
            (Math.sin(h2.toDouble()) * centerOffset).toFloat(),
            0f,
            (Math.cos(h2.toDouble()) * centerOffset).toFloat()
        )
        layer.addChild(limb2)

        return layer
    }

    // Baut einen Chevron als 4-Layer-Gruppe (Glow/Back/Mid/Top) für Leuchteffekt
    private fun makeChevronGroup(
        mGlow: com.google.ar.sceneform.rendering.Material,
        mBack: com.google.ar.sceneform.rendering.Material,
        mMid: com.google.ar.sceneform.rendering.Material,
        mTop: com.google.ar.sceneform.rendering.Material
    ): Node {
        val group = Node()

        // GLOW (äußerster Layer für Leuchteffekt)
        val glow = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mGlow
        ).apply {
            localScale = Vector3(config.glowScale, config.glowScale, config.glowScale)
            localPosition = Vector3(0f, config.layerGapY * 3f, -config.layerGapZ * 3f)
        }
        group.addChild(glow)

        // BACK (blau-weißer Rand)
        val back = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mBack
        ).apply {
            localScale = Vector3(config.backScale, config.backScale, config.backScale)
            localPosition = Vector3(0f, config.layerGapY * 2f, -config.layerGapZ * 2f)
        }
        group.addChild(back)

        // MID (mittleres Cyan-Blau)
        val mid = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mMid
        ).apply {
            localScale = Vector3(config.midScale, config.midScale, config.midScale)
            localPosition = Vector3(0f, config.layerGapY, -config.layerGapZ)
        }
        group.addChild(mid)

        // TOP (leuchtendes helles Blau)
        val top = makeChevronLayer(
            config.limbLength,
            config.limbRadius,
            config.chevronAngleDeg,
            mTop
        )
        group.addChild(top)

        return group
    }

    fun buildProcedural() {
        MaterialFactory.makeTransparentWithColor(context, config.colGlow).thenAccept { mGlow ->
            MaterialFactory.makeOpaqueWithColor(context, config.colBack).thenAccept { mBack ->
                MaterialFactory.makeOpaqueWithColor(context, config.colMid).thenAccept { mMid ->
                    MaterialFactory.makeOpaqueWithColor(context, config.colTop).thenAccept { mTop ->
                        try {
                            clearChildren()

                            // Drei Chevrons als Stack erzeugen
                            for (i in 0 until config.stackCount) {
                                val chevron = makeChevronGroup(mGlow, mBack, mMid, mTop)

                                val offset = config.stackOffset
                                val pos = Vector3(
                                    offset.x * i,
                                    offset.y * i,
                                    (offset.z + config.stackDepthStep) * i
                                )
                                chevron.localPosition = pos

                                geom.addChild(chevron)
                            }

                            rootNode.localScale = Vector3(config.globalScale, config.globalScale, config.globalScale)

                            Log.d("ArrowAbbiegen", "Blue glowing chevron stack (3x) created")
                        } catch (e: Exception) {
                            Log.e("ArrowAbbiegen", "Failed to build blue chevron stack: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    fun setParent(parent: NodeParent?) { rootNode.setParent(parent) }
    fun setWorldPose(position: Vector3, yawDeg: Float) {
        rootNode.worldPosition = position
        rootNode.worldRotation = ArrowOrientation.blueRotation(yawDeg)
    }

    fun setUnlitIfPossible() { /* procedural -> no-op */ }
}

class ArrowRenderer3D(private val context: Context) {
    private var anchor: Anchor? = null
    private var anchorNode: AnchorNode? = null
    private val arrowRenderer = ArrowRenderer(context)
    private val arrowAbbiegen = ArrowAbbiegen(context)

    // Destination pin
    private var locationPinNode: Node? = null

    // Step tracking for persistence
    private var currentStepKey: String? = null
    private var isTurning = false

    // Local origin (camera-aligned) yaw at reset time; null until set
    private var resetYawDeg: Float? = null

    fun ensureResetYaw(yawDeg: Float): Float {
        if (resetYawDeg == null) resetYawDeg = yawDeg
        return resetYawDeg!!
    }

    fun clearResetYaw() { resetYawDeg = null }

    fun getResetYaw(): Float? = resetYawDeg

    fun updateFromPose(scene: Scene, yawDeg: Float) {
        // If frozen, keep both position and rotation unchanged
        if (isFrozen()) return
        if (isTurning) {
            // Blue arrow: keep relative to camera every frame until frozen
            val camera = scene.camera
            if (camera != null) {
                val pos = ArrowOrientation.bluePosition(camera)
                arrowAbbiegen.rootNode.worldPosition = pos
                arrowAbbiegen.rootNode.worldRotation = ArrowOrientation.blueRotation(yawDeg)
            }
        } else {
            // Green arrow: update heading relative to camera until frozen
            anchorNode?.let { an ->
                val wp = an.worldPosition
                val worldPos = Vector3(wp.x, wp.y + 0.12f, wp.z)
                arrowRenderer.setWorldPose(worldPos, yawDeg)
            }
        }
    }

    private var frozen: Boolean = false
    private var frozenLandmarkId: String? = null

    fun isFrozen(): Boolean = frozen
    fun getFrozenLandmarkId(): String? = frozenLandmarkId
    fun freezeForLandmark(landmarkId: String?) { frozen = true; frozenLandmarkId = landmarkId }
    fun unfreeze() { frozen = false; frozenLandmarkId = null }

    fun setArrowTypeFromLandmark(landmarkIds: List<String>) {
        val wasTurning = isTurning
        Log.d("ArrowRenderer3D", "setArrowTypeFromLandmark called with landmarks: ${landmarkIds.joinToString(", ")}")

        // Check if any landmark indicates a turn (ends with _L, _R, etc.)
        val hasTurnLandmark = landmarkIds.any { id ->
            id.endsWith("_L", ignoreCase = true) ||
            id.endsWith("_R", ignoreCase = true) ||
            id.contains("biegen", ignoreCase = true)
        }

        Log.d("ArrowRenderer3D", "Landmark turn check: hasTurnLandmark=$hasTurnLandmark")

        // Only show blue (turn) arrow when a turn landmark is present; otherwise enforce green arrow
        isTurning = hasTurnLandmark
        if (hasTurnLandmark) {
            Log.w("ArrowRenderer3D", "*** TURNING SET BY LANDMARK *** landmarks=${landmarkIds.joinToString(", ")}")
        }

        if (wasTurning != isTurning) {
            Log.w("ArrowRenderer3D", "*** ARROW TYPE CHANGED BY LANDMARK *** from ${if (wasTurning) "ArrowAbbiegen" else "ArrowRenderer"} to ${if (isTurning) "ArrowAbbiegen" else "ArrowRenderer"}")
        }
    }

    fun attachTo(scene: Scene) {
        // Do NOT attach renderer to scene until we have an anchor; just ensure materials are ready
        arrowRenderer.ensureRenderable()
        arrowAbbiegen.ensureRenderable()
    }

    fun isAnchored(): Boolean = anchor != null && anchorNode != null

    fun hideArrow() {
        // Detach arrow visuals but keep anchor (for sticky/timeout behavior)
        try {
            arrowRenderer.setParent(null)
            arrowAbbiegen.setParent(null)
        } catch (_: Exception) {}
    }

    fun clear() {
        try {
            // Detach visuals
            arrowRenderer.setParent(null)
            arrowAbbiegen.setParent(null)
            locationPinNode?.setParent(null)
            locationPinNode = null
            // Detach anchor
            anchorNode?.anchor?.detach()
            anchorNode?.setParent(null)
        } catch (_: Exception) {}
        anchorNode = null
        anchor = null
        currentStepKey = null
        isTurning = false
    }

    fun placeAnchor(scene: Scene, newAnchor: Anchor, yawDeg: Float) {
        try {
            if (anchor != null && anchor != newAnchor) {
                anchorNode?.anchor?.detach()
                anchor = null
            }
        } catch (_: Exception) {}

        anchor = newAnchor

        if (anchorNode == null) {
            anchorNode = AnchorNode(newAnchor).apply {
                setParent(scene)
                isEnabled = true
            }
        } else {
            anchorNode!!.anchor = newAnchor
            if (anchorNode!!.parent != scene) anchorNode!!.setParent(scene)
        }

        // Arrow type must NOT depend on yaw; only landmarks determine blue vs green.
        // Switch arrow type if turning state changed by landmark
        val shouldDetachOld = anchorNode != null && (arrowRenderer.rootNode.parent == anchorNode || arrowAbbiegen.rootNode.parent == anchorNode)
        if (shouldDetachOld) {
            // Detach old arrow before attaching new one
            try {
                arrowRenderer.setParent(null)
                arrowAbbiegen.setParent(null)
            } catch (_: Exception) {}
        }

        // Attach appropriate arrow type
            if (isTurning) {
            if (arrowAbbiegen.rootNode.parent != anchorNode) {
                arrowAbbiegen.setParent(anchorNode)
            }
            // SPECIAL HANDLING FOR BLUE ARROW: centralized position and rotation
            anchorNode?.let { an ->
                val scene = an.scene
                val camera = scene?.camera
                if (camera != null) {
                    val arrowPosition = ArrowOrientation.bluePosition(camera)
                    arrowAbbiegen.rootNode.worldPosition = arrowPosition
                    arrowAbbiegen.rootNode.worldRotation = ArrowOrientation.blueRotation(yawDeg)
                    Log.d("ArrowPos", "*** BLUE ARROW SPECIAL *** position=(${"%.2f".format(arrowPosition.x)}, ${"%.2f".format(arrowPosition.y)}, ${"%.2f".format(arrowPosition.z)}), yaw=${"%.1f".format(yawDeg)}°")
                } else {
                    // Fallback wenn keine Kamera verfügbar
                    val wp = an.worldPosition
                    val worldPos = Vector3(wp.x, wp.y + 0.12f, wp.z)
                    arrowAbbiegen.setWorldPose(worldPos, yawDeg)
                    Log.d("ArrowPos", "*** BLUE ARROW FALLBACK *** yaw=${"%.1f".format(yawDeg)}°")
                }
            }
            arrowAbbiegen.ensureRenderable()
        } else {
            if (arrowRenderer.rootNode.parent != anchorNode) {
                arrowRenderer.setParent(anchorNode)
            }
            // Fixed offset above ground in WORLD space; cancel any parent rotation
            anchorNode?.let { an ->
                val wp = an.worldPosition
                val worldPos = Vector3(wp.x, wp.y + 0.12f, wp.z)
                arrowRenderer.setWorldPose(worldPos, yawDeg)
                Log.d("ArrowPos", "ArrowRenderer worldRotation after place: ${arrowRenderer.rootNode.worldRotation}")
            }
            arrowRenderer.ensureRenderable()
        }
    }


    fun showArrow() {
        // Ensure arrow visible, hide pin
        locationPinNode?.setParent(null)
        if (isTurning) {
            if (anchorNode != null && arrowAbbiegen.rootNode.parent != anchorNode) {
                // Make sure only the correct arrow type is attached
                arrowRenderer.setParent(null)
                arrowAbbiegen.setParent(null)
                arrowAbbiegen.setParent(anchorNode)
            }
            // Do not override rotation here; keep orientation from placement
            arrowAbbiegen.ensureRenderable()
        } else {
            if (anchorNode != null && arrowRenderer.rootNode.parent != anchorNode) {
                // Make sure only the correct arrow type is attached
                arrowRenderer.setParent(null)
                arrowAbbiegen.setParent(null)
                arrowRenderer.setParent(anchorNode)
            }
            arrowRenderer.ensureRenderable()
        }
    }

    fun showLocationPin() {
        if (anchorNode == null) return
        // Build pin lazily (simple red cone)
        if (locationPinNode == null) locationPinNode = createRedConeNode(context)
        // Hide arrows
        arrowRenderer.setParent(null)
        arrowAbbiegen.setParent(null)
        // Attach pin
        if (locationPinNode!!.parent != anchorNode) locationPinNode!!.setParent(anchorNode)
        locationPinNode!!.localPosition = Vector3(0f, 0.15f, 0f) // Slightly higher for visibility
        locationPinNode!!.localRotation = Quaternion.identity() // no direction
    }

    fun setCurrentStepKey(key: String) { currentStepKey = key }
    fun getCurrentStepKey(): String? = currentStepKey
}


/**
 * Creates a simple red cone node to serve as a destination marker in the AR scene.
 *
 * The cone is opaque, colored red, and points DOWNWARD (Y-). It is built using either
 * ShapeFactory.makeCone (if available), or as a fallback by stacking two cylinders
 * of different radii to approximate a cone shape.
 *
 * @param context Android context for material creation.
 * @return Node containing the red cone geometry.
 */
fun createRedConeNode(context: Context): Node {
    val node = Node()
    val baseRadius = 0.15f
    val height = 0.4f
    val color = Color(0.9f, 0.1f, 0.1f, 1.0f)

    MaterialFactory.makeOpaqueWithColor(context, color).thenAccept { material ->
        try {
            // Try to use ShapeFactory.makeCone if available (Sceneform 1.17+)
            val coneRenderable = try {
                val method = ShapeFactory::class.java.getMethod(
                    "makeCone",
                    Float::class.java,
                    Float::class.java,
                    Vector3::class.java,
                    com.google.ar.sceneform.rendering.Material::class.java
                )
                method.invoke(
                    null,
                    baseRadius,
                    height,
                    Vector3(0f, 0f, 0f), // Pivot at tip
                    material
                ) as com.google.ar.sceneform.rendering.ModelRenderable
            } catch (e: Exception) {
                null
            }

            if (coneRenderable != null) {
                val coneNode = Node().apply {
                    renderable = coneRenderable
                    // Rotate 180° around X-axis to flip cone upside down
                    localRotation = Quaternion.axisAngle(Vector3(1f, 0f, 0f), 180f)
                }
                node.addChild(coneNode)
            } else {
                // Fallback: Approximate cone by stacking two cylinders (inverted)
                // Bottom (wider) part
                val cyl1 = ShapeFactory.makeCylinder(
                    baseRadius,
                    height * 0.7f,
                    Vector3(0f, -height * 0.35f, 0f), // Negative Y
                    material
                )
                // Top (narrower) part
                val cyl2 = ShapeFactory.makeCylinder(
                    baseRadius * 0.3f,
                    height * 0.3f,
                    Vector3(0f, -height * 0.85f, 0f), // Negative Y
                    material
                )

                val n1 = Node().apply { renderable = cyl1 }
                val n2 = Node().apply { renderable = cyl2 }
                node.addChild(n1)
                node.addChild(n2)
            }

            Log.d("ArrowSystem", "Created red cone destination marker (pointing down)")
        } catch (e: Exception) {
            Log.e("ArrowSystem", "Failed to create red cone node: ${e.message}", e)
        }
    }

    return node
}