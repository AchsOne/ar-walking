package com.example.arwalking.ar

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
    val limbLength: Float = 0.48f,        // Länge je „Arm“
    val limbRadius: Float = 0.07f,        // Dicke/Radius je „Arm“
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

class ArrowRenderer(
    private val context: Context,
    private val config: ArrowConfig = ArrowConfig()
) {
    val rootNode: Node = Node()
    // Geometry container rotated so that chevrons point forward (-Z) while root stays identity
    private val geom: Node = Node().apply {
        localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), 180f)
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

    // --- Hilfsfunktion: „Capsule“ aus Zylinder + 2 Kugel-Kappen ---
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

        // BACK (weißer Rand / „Sticker“-Look)
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
        // Force no rotation
        rootNode.worldRotation = Quaternion.identity()
    }
    fun setLocalPose(position: Vector3, yawDeg: Float) {
        rootNode.localPosition = position
        // Force no rotation
        rootNode.localRotation = Quaternion.identity()
    }
    fun setUnlitIfPossible() { /* procedural -> no-op */ }
}

class ArrowController(private val context: Context) {
    private var anchor: Anchor? = null
    private var anchorNode: AnchorNode? = null
    private val arrowRenderer = ArrowRenderer(context)

    // Destination pin
    private var locationPinNode: Node? = null

    // Step tracking for persistence
    private var currentStepKey: String? = null

    fun attachTo(scene: Scene) {
        // Do NOT attach renderer to scene until we have an anchor; just ensure materials are ready
        arrowRenderer.ensureRenderable()
    }

    fun isAnchored(): Boolean = anchor != null && anchorNode != null

    fun hideArrow() {
        // Detach arrow visuals but keep anchor (for sticky/timeout behavior)
        try { arrowRenderer.setParent(null) } catch (_: Exception) {}
    }

    fun clear() {
        try {
            // Detach visuals
            arrowRenderer.setParent(null)
            locationPinNode?.setParent(null)
            locationPinNode = null
            // Detach anchor
            anchorNode?.anchor?.detach()
            anchorNode?.setParent(null)
        } catch (_: Exception) {}
        anchorNode = null
        anchor = null
        currentStepKey = null
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

        if (arrowRenderer.rootNode.parent != anchorNode) {
            arrowRenderer.setParent(anchorNode)
        }

        // Fixed offset above ground in WORLD space; cancel any parent rotation
        anchorNode?.let { an ->
            val wp = an.worldPosition
            val worldPos = Vector3(wp.x, wp.y + 0.12f, wp.z)
            arrowRenderer.setWorldPose(worldPos, yawDeg)
            Log.d("ArrowPos", "Renderer worldRotation after place: ${arrowRenderer.rootNode.worldRotation}")
        }
        arrowRenderer.ensureRenderable()
    }

    fun updateYaw(yawDeg: Float) {
        // Keep arrow pointing straight ahead regardless of anchor/camera rotation
        try { arrowRenderer.rootNode.worldRotation = Quaternion.identity() } catch (_: Exception) {}
        arrowRenderer.ensureRenderable()
    }

    fun showArrow() {
        // Ensure arrow visible, hide pin
        locationPinNode?.setParent(null)
        if (anchorNode != null && arrowRenderer.rootNode.parent != anchorNode) {
            arrowRenderer.setParent(anchorNode)
        }
        arrowRenderer.ensureRenderable()
    }

    fun showLocationPin() {
        if (anchorNode == null) return
        // Build pin lazily (simple red cone)
        if (locationPinNode == null) locationPinNode = createRedConeNode(context)
        // Hide arrow
        arrowRenderer.setParent(null)
        // Attach pin
        if (locationPinNode!!.parent != anchorNode) locationPinNode!!.setParent(anchorNode)
        locationPinNode!!.localPosition = Vector3(0f, 0.15f, 0f) // Slightly higher for visibility
        locationPinNode!!.localRotation = Quaternion.identity() // no direction
    }

    fun setCurrentStepKey(key: String) { currentStepKey = key }
    fun getCurrentStepKey(): String? = currentStepKey
}

// --- Simple Red Cone for Destination ---
private fun createRedConeNode(context: Context): Node {
    val node = Node()
    
    // Bright red color for destination cone
    val redColor = Color(0.9f, 0.1f, 0.1f, 1.0f) // Bright red
    
    // Cone dimensions (tip pointing down)
    val coneHeight = 0.4f
    val coneBaseRadius = 0.15f
    
    MaterialFactory.makeOpaqueWithColor(context, redColor).thenAccept { redMaterial ->
        try {
            // Create cone shape using stacked cylinders (approximation)
            val segments = 8 // Number of segments to approximate cone
            
            for (i in 0 until segments) {
                val progress = i.toFloat() / segments.toFloat()
                val nextProgress = (i + 1).toFloat() / segments.toFloat()
                
                // Calculate radius at each segment (larger at top, smaller at bottom)
                val topRadius = coneBaseRadius * (1.0f - progress)
                val bottomRadius = coneBaseRadius * (1.0f - nextProgress)
                val segmentHeight = coneHeight / segments
                
                // Position: start from top (y=0) going down
                val yPosition = -progress * coneHeight
                
                // Use cylinder for each segment
                val avgRadius = (topRadius + bottomRadius) / 2.0f
                if (avgRadius > 0.01f) { // Skip very small segments at the tip
                    val cylinderSize = Vector3(avgRadius * 2, segmentHeight, avgRadius * 2)
                    val cylinder = ShapeFactory.makeCube(cylinderSize, Vector3(0f, yPosition, 0f), redMaterial)
                    node.addChild(Node().apply { renderable = cylinder })
                }
            }
            
            Log.d("RedCone", "Created red cone destination marker")
        } catch (e: Exception) {
            Log.e("RedCone", "Failed to build red cone: ${e.message}", e)
            // Fallback: simple red cube
            val fallbackSize = Vector3(0.2f, 0.3f, 0.2f)
            val fallback = ShapeFactory.makeCube(fallbackSize, Vector3(0f, -0.15f, 0f), redMaterial)
            node.addChild(Node().apply { renderable = fallback })
        }
    }

    return node
}

