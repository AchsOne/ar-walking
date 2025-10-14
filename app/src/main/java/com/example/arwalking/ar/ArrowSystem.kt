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
import android.net.Uri
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory

/**
 * Clean arrow rendering + control wrappers.
 *
 * ArrowRenderer: builds and manages the 3D arrow node (procedural), with stable materials.
 * ArrowController: manages anchor/anchorNode and positions the arrow accordingly.
 */

data class ArrowConfig(
    // Enhanced arrow proportions for better aesthetics
    val stemSize: Vector3 = Vector3(0.08f, 0.08f, 0.50f),
    val headSize: Vector3 = Vector3(0.25f, 0.08f, 0.30f),
    // More vibrant green tones with good contrast
    val colorStem: Color = Color(0.15f, 0.75f, 0.25f),      // Darker green for shaft
    val colorHead: Color = Color(0.20f, 0.90f, 0.35f),      // Brighter green for head
    val colorTip: Color = Color(0.25f, 1.0f, 0.45f)         // Very bright green for tip
)

class ArrowRenderer(private val context: Context, private val config: ArrowConfig = ArrowConfig()) {
    val rootNode: Node = Node()
    private var modelNode: Node? = null

    init { buildProcedural() }

    fun ensureRenderable() {
        if (rootNode.children.isEmpty()) {
            Log.d("ArrowRenderer", "ensureRenderable: no children -> build procedural arrow")
            buildProcedural()
        }
    }

    private fun loadChevronGlbOrFallback() {
        // Current Sceneform dependency does not support direct GLB loading in this project setup.
        // Falling back to procedural chevron. To use GLB, provide an SFB asset or update Sceneform.
        buildProcedural()
        return
    }

    fun buildProcedural() {
        // Build a simple, stable 3D chevron arrow pointing forward
        MaterialFactory.makeOpaqueWithColor(context, config.colorHead).thenAccept { arrowMat ->
            try {
                // Clear any existing children first
                val childrenToRemove = rootNode.children.toList()
                childrenToRemove.forEach { it.setParent(null) }
                
                // Single 3D chevron - simple and stable
                // Create the main chevron body using a rotated cube to form triangle shape
                val chevronBody = ShapeFactory.makeCube(
                    Vector3(0.15f, 0.08f, 0.25f), // substantial size for visibility
                    Vector3(0f, 0f, 0f), // centered
                    arrowMat
                )
                
                val chevronNode = Node().apply {
                    renderable = chevronBody
                    // Rotate to create diamond/chevron pointing forward
                    localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), 45f)
                }
                rootNode.addChild(chevronNode)
                
                // Add a pointed tip for clear direction indication
                val tip = ShapeFactory.makeCube(
                    Vector3(0.08f, 0.05f, 0.12f), // smaller tip
                    Vector3(0f, 0f, 0.18f), // positioned at front
                    arrowMat
                )
                
                val tipNode = Node().apply {
                    renderable = tip
                    localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), 45f)
                }
                rootNode.addChild(tipNode)
                
                // Scale for optimal visibility but keep small
                rootNode.localScale = Vector3(0.9f, 0.9f, 0.9f)
                
                // Disable shadows to improve stability
                chevronBody.isShadowCaster = false
                chevronBody.isShadowReceiver = false
                tip.isShadowCaster = false
                tip.isShadowReceiver = false
                
                Log.d("ArrowRenderer", "Simple stable 3D chevron arrow created")
            } catch (e: Exception) {
                Log.e("ArrowRenderer", "Failed to build chevron arrow: ${e.message}", e)
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

    fun setUnlitIfPossible() {
        // For GLB materials we keep defaults; for procedural, nothing to do here
    }
}

class ArrowController(private val context: Context) {
    private var anchor: Anchor? = null
    private var anchorNode: AnchorNode? = null
    private val renderer = ArrowRenderer(context)

    fun attachTo(scene: Scene) {
        if (renderer.rootNode.parent == null) {
            Log.d("ArrowController", "Attaching renderer root to scene")
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

    fun placeFallback(scene: Scene, yawDeg: Float, localPos: Vector3 = Vector3(0f, 0f, -1.5f)) {
        // Stable fallback positioning relative to camera
        if (renderer.rootNode.parent != scene.camera) {
            renderer.setParent(scene.camera)
        }
        renderer.setLocalPose(localPos, yawDeg)
        
        // Ensure renderable exists in fallback mode
        renderer.ensureRenderable()
    }

    fun placeAnchor(scene: Scene, newAnchor: Anchor, yawDeg: Float) {
        // Improved anchor management for stability
        try {
            // Clean up old anchor properly
            if (anchor != null && anchor != newAnchor) {
                anchorNode?.anchor?.detach()
                anchor = null
            }
        } catch (_: Exception) {}
        
        anchor = newAnchor
        
        if (anchorNode == null) {
            // Create new anchor node
            anchorNode = AnchorNode(newAnchor).apply { 
                setParent(scene)
                // Ensure stable positioning
                isEnabled = true
            }
        } else {
            // Update existing anchor node
            anchorNode!!.anchor = newAnchor
            if (anchorNode!!.parent != scene) {
                anchorNode!!.setParent(scene)
            }
        }
        
        // Ensure renderer is properly attached
        if (renderer.rootNode.parent != anchorNode) {
            renderer.setParent(anchorNode)
        }
        
        // Position arrow slightly above ground with stable offset
        renderer.setLocalPose(Vector3(0f, 0.12f, 0f), yawDeg)
        
        // Ensure renderable exists
        renderer.ensureRenderable()
    }

    fun updateYaw(yawDeg: Float) {
        // Update rotation while preserving position for stability
        val currentPos = renderer.rootNode.localPosition
        renderer.setLocalPose(currentPos, yawDeg)
        
        // Ensure renderable stability
        renderer.ensureRenderable()
    }
}
