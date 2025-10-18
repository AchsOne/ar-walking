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
    init { buildProcedural() }

    fun ensureRenderable() {
        if (rootNode.children.isEmpty()) buildProcedural()
    }

    private fun clearChildren() {
        rootNode.children.toList().forEach { it.setParent(null) }
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

                            rootNode.addChild(chevron)
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
        rootNode.worldRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
    }
    fun setLocalPose(position: Vector3, yawDeg: Float) {
        rootNode.localPosition = position
        rootNode.localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
    }
    fun setUnlitIfPossible() { /* procedural -> no-op */ }
}

class ArrowController(private val context: Context) {
    private var anchor: Anchor? = null
    private var anchorNode: AnchorNode? = null
    private val renderer = ArrowRenderer(context)

    fun attachTo(scene: Scene) {
        if (renderer.rootNode.parent == null) {
            renderer.setParent(scene)
        }
        renderer.ensureRenderable()
    }

    fun isAnchored(): Boolean = anchor != null && anchorNode != null

    fun clear() {
        try {
            renderer.setParent(null)
            anchorNode?.anchor?.detach()
            anchorNode?.setParent(null)
        } catch (_: Exception) {}
        anchorNode = null
        anchor = null
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

        if (renderer.rootNode.parent != anchorNode) {
            renderer.setParent(anchorNode)
        }

        // Fixed offset above ground; keep stable
        renderer.setLocalPose(Vector3(0f, 0.12f, 0f), yawDeg)
        renderer.ensureRenderable()
    }

    fun updateYaw(yawDeg: Float) {
        val currentPos = renderer.rootNode.localPosition
        renderer.setLocalPose(currentPos, yawDeg)
        renderer.ensureRenderable()
    }
}