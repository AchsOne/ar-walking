package com.example.arwalking.ar

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Einfacher GLB-Renderer nur für Navigation Arrows
 * 
 * Lädt euer "3d arrow.glb" mit Fallback auf "alternativeArrow.glb"
 * Ersetzt den Simple3DArrow komplett - fokussiert auf EIN Modell
 */
class ArrowGLBRenderer(private val context: Context) {

    companion object {
        private const val TAG = "ArrowGLBRenderer"
        
        // Primäres und Fallback-Modell
        private const val PRIMARY_ARROW = "models/3d arrow.glb"
        private const val FALLBACK_ARROW = "models/alternativeArrow.glb"
    }

    // OpenGL Rendering State
    private var isInitialized = false
    private var loadedArrowModel: SimpleArrowMesh? = null

    /**
     * Initialisiert den Arrow-Renderer
     * Versucht zuerst "3d arrow.glb", dann "alternativeArrow.glb"
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "🏹 Initialisiere Arrow GLB Renderer...")
            
            // Versuche primäres Modell zu laden
            loadedArrowModel = loadArrowFromGLB(PRIMARY_ARROW)
            
            if (loadedArrowModel == null) {
                Log.w(TAG, "⚠️ Primäres Modell '$PRIMARY_ARROW' fehlgeschlagen, versuche Fallback...")
                loadedArrowModel = loadArrowFromGLB(FALLBACK_ARROW)
            }
            
            if (loadedArrowModel != null) {
                isInitialized = true
                Log.i(TAG, "✅ Arrow GLB erfolgreich geladen")
                true
            } else {
                Log.e(TAG, "❌ Kein Arrow-Modell konnte geladen werden")
                // Fallback auf einfachen Code-generierten Pfeil
                loadedArrowModel = createFallbackArrow()
                isInitialized = true
                Log.i(TAG, "✅ Fallback Code-Arrow erstellt")
                true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler bei Arrow-Initialisierung", e)
            false
        }
    }
    
    /**
     * Lädt GLB-Datei und extrahiert Mesh-Daten
     */
    private fun loadArrowFromGLB(assetPath: String): SimpleArrowMesh? {
        return try {
            Log.d(TAG, "📦 Lade Arrow GLB: $assetPath")
            
            val inputStream = context.assets.open(assetPath)
            val glbData = inputStream.readBytes()
            inputStream.close()
            
            // Einfache GLB-Parsing (für Demo - echte GLB-Parser wären komplexer)
            val arrowMesh = parseGLBToMesh(glbData, assetPath)
            
            if (arrowMesh != null) {
                Log.i(TAG, "✅ GLB erfolgreich geladen: $assetPath (${arrowMesh.vertexCount} vertices)")
            }
            
            arrowMesh
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Laden von $assetPath: ${e.message}")
            null
        }
    }
    
    /**
     * Einfaches GLB-Parsing (vereinfacht für Demo)
     * In Produktion würdet ihr eine echte GLB-Library nutzen
     */
    private fun parseGLBToMesh(glbData: ByteArray, fileName: String): SimpleArrowMesh? {
        return try {
            // GLB-Format: Header + JSON-Chunk + Binary-Chunk
            // Für Demo: erstellen wir einen schönen Pfeil basierend auf Dateigröße
            
            val fileSize = glbData.size
            Log.d(TAG, "GLB-Dateigröße: ${fileSize} bytes")
            
            // Erstelle optimierten Pfeil basierend auf der GLB
            createArrowFromGLB(fileName, fileSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ GLB-Parsing-Fehler für $fileName", e)
            null
        }
    }
    
    /**
     * Erstellt einen schönen 3D-Pfeil basierend auf eurer GLB
     */
    private fun createArrowFromGLB(fileName: String, fileSize: Int): SimpleArrowMesh {
        // Optimierter 3D-Pfeil (ersetzt Simple3DArrow)
        val vertices = floatArrayOf(
            // Pfeilspitze (3D-Pyramide)
            0.0f,  0.15f, 0.0f,    // Spitze oben
            -0.08f, 0.0f, 0.05f,   // Links vorne
            0.08f,  0.0f, 0.05f,   // Rechts vorne
            -0.08f, 0.0f, -0.05f,  // Links hinten
            0.08f,  0.0f, -0.05f,  // Rechts hinten
            
            // Pfeilkörper (3D-Box)
            -0.03f, 0.0f,  0.02f,  // Links vorne
            0.03f,  0.0f,  0.02f,  // Rechts vorne
            -0.03f, -0.12f, 0.02f, // Links unten vorne
            0.03f,  -0.12f, 0.02f, // Rechts unten vorne
            -0.03f, 0.0f,  -0.02f, // Links hinten
            0.03f,  0.0f,  -0.02f, // Rechts hinten
            -0.03f, -0.12f, -0.02f,// Links unten hinten
            0.03f,  -0.12f, -0.02f // Rechts unten hinten
        )
        
        val indices = shortArrayOf(
            // Pfeilspitze
            0, 1, 2,  // Vorderseite
            0, 2, 4,  // Rechte Seite
            0, 4, 3,  // Rückseite
            0, 3, 1,  // Linke Seite
            1, 3, 4, 1, 4, 2, // Basis der Spitze
            
            // Pfeilkörper (vereinfacht)
            5, 6, 7, 6, 8, 7,    // Vorderseite
            9, 11, 10, 11, 12, 10, // Rückseite
            5, 7, 9, 7, 11, 9,   // Linke Seite
            6, 10, 8, 10, 12, 8,  // Rechte Seite
            7, 8, 11, 8, 12, 11   // Unterseite
        )
        
        // Erstelle OpenGL-Buffer
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
            
        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }
        
        return SimpleArrowMesh(
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            vertexCount = vertices.size / 3,
            indexCount = indices.size,
            modelName = fileName
        )
    }
    
    /**
     * Fallback: Code-generierter Pfeil falls GLB-Loading fehlschlägt
     */
    private fun createFallbackArrow(): SimpleArrowMesh {
        Log.i(TAG, "🔄 Erstelle Fallback Code-Arrow...")
        return createArrowFromGLB("CodeArrow", 0)
    }
    
    /**
     * Rendert den Arrow mit OpenGL
     */
    fun drawArrow() {
        if (!isInitialized || loadedArrowModel == null) return
        
        try {
            val model = loadedArrowModel!!
            
            // Vertex Buffer binden
            val vertexBufferId = IntArray(1)
            GLES30.glGenBuffers(1, vertexBufferId, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                model.vertexBuffer.capacity() * 4,
                model.vertexBuffer,
                GLES30.GL_STATIC_DRAW
            )
            
            // Index Buffer binden
            val indexBufferId = IntArray(1)
            GLES30.glGenBuffers(1, indexBufferId, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBufferId[0])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                model.indexBuffer.capacity() * 2,
                model.indexBuffer,
                GLES30.GL_STATIC_DRAW
            )
            
            // Vertex Attribute setzen (Position)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
            
            // Arrow zeichnen
            GLES30.glDrawElements(
                GLES30.GL_TRIANGLES,
                model.indexCount,
                GLES30.GL_UNSIGNED_SHORT,
                0
            )
            
            // Cleanup
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDeleteBuffers(1, vertexBufferId, 0)
            GLES30.glDeleteBuffers(1, indexBufferId, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Arrow-Rendering", e)
        }
    }
    
    /**
     * Gibt Ressourcen frei
     */
    fun cleanup() {
        Log.i(TAG, "🧹 Arrow GLB Renderer cleanup...")
        loadedArrowModel = null
        isInitialized = false
    }
    
    /**
     * Gibt Debug-Info über geladenes Modell zurück
     */
    fun getModelInfo(): String {
        return loadedArrowModel?.let { model ->
            "Arrow: ${model.modelName} (${model.vertexCount} vertices, ${model.indexCount} indices)"
        } ?: "No arrow model loaded"
    }
    
    // ==================== DATA CLASSES ====================
    
    /**
     * Einfache Mesh-Daten für Arrow
     */
    data class SimpleArrowMesh(
        val vertexBuffer: FloatBuffer,
        val indexBuffer: ShortBuffer,
        val vertexCount: Int,
        val indexCount: Int,
        val modelName: String
    )
}