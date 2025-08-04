package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lokale Speicherung für Trainingsbilder als Fallback wenn Server nicht erreichbar ist
 */
class LocalImageStorage(private val context: Context) {
    
    private val TAG = "LocalImageStorage"
    
    // Verzeichnis für lokale Speicherung
    private val localStorageDir: File by lazy {
        File(context.filesDir, "training_images").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    // Verzeichnis für Warteschlange (Bilder die noch hochgeladen werden müssen)
    private val queueDir: File by lazy {
        File(context.filesDir, "upload_queue").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Speichert Bild lokal und fügt es zur Upload-Warteschlange hinzu
     */
    suspend fun saveImageLocally(
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "${landmarkId}_${timestamp}.jpg"
            
            // Speichere Bild lokal
            val imageFile = File(localStorageDir, filename)
            val success = saveBitmapToFile(bitmap, imageFile)
            
            if (success) {
                // Erstelle Metadaten-Datei für Upload-Warteschlange
                val metadataFile = File(queueDir, "${filename}.meta")
                val metadata = """
                    landmarkId=$landmarkId
                    landmarkName=$landmarkName
                    description=$description
                    timestamp=${System.currentTimeMillis()}
                    filename=$filename
                    uploaded=false
                """.trimIndent()
                
                metadataFile.writeText(metadata)
                
                Log.i(TAG, "Bild lokal gespeichert: $filename")
                true
            } else {
                Log.e(TAG, "Fehler beim lokalen Speichern des Bildes")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim lokalen Speichern: ${e.message}")
            false
        }
    }
    
    /**
     * Speichert Bitmap in Datei
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Fehler beim Speichern der Bitmap: ${e.message}")
            false
        }
    }
    
    /**
     * Gibt alle Bilder in der Upload-Warteschlange zurück
     */
    suspend fun getPendingUploads(): List<PendingUpload> = withContext(Dispatchers.IO) {
        try {
            val pendingUploads = mutableListOf<PendingUpload>()
            
            queueDir.listFiles { _, name -> name.endsWith(".meta") }?.forEach { metaFile ->
                try {
                    val metadata = metaFile.readText()
                    val lines = metadata.lines()
                    val metaMap = lines.associate { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else "" to ""
                    }
                    
                    val filename = metaMap["filename"] ?: return@forEach
                    val imageFile = File(localStorageDir, filename)
                    
                    if (imageFile.exists() && metaMap["uploaded"] != "true") {
                        pendingUploads.add(
                            PendingUpload(
                                landmarkId = metaMap["landmarkId"] ?: "",
                                landmarkName = metaMap["landmarkName"] ?: "",
                                description = metaMap["description"] ?: "",
                                timestamp = metaMap["timestamp"]?.toLongOrNull() ?: 0L,
                                imageFile = imageFile,
                                metadataFile = metaFile
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fehler beim Lesen der Metadaten: ${e.message}")
                }
            }
            
            pendingUploads.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Upload-Warteschlange: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Markiert Upload als erfolgreich
     */
    suspend fun markAsUploaded(pendingUpload: PendingUpload): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = pendingUpload.metadataFile.readText()
            val updatedMetadata = metadata.replace("uploaded=false", "uploaded=true")
            pendingUpload.metadataFile.writeText(updatedMetadata)
            
            Log.i(TAG, "Upload als erfolgreich markiert: ${pendingUpload.landmarkId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Markieren als hochgeladen: ${e.message}")
            false
        }
    }
    
    /**
     * Löscht lokale Dateien nach erfolgreichem Upload
     */
    suspend fun cleanupAfterUpload(pendingUpload: PendingUpload): Boolean = withContext(Dispatchers.IO) {
        try {
            var success = true
            
            if (pendingUpload.imageFile.exists()) {
                success = pendingUpload.imageFile.delete() && success
            }
            
            if (pendingUpload.metadataFile.exists()) {
                success = pendingUpload.metadataFile.delete() && success
            }
            
            if (success) {
                Log.i(TAG, "Lokale Dateien nach Upload bereinigt: ${pendingUpload.landmarkId}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Bereinigen: ${e.message}")
            false
        }
    }
    
    /**
     * Gibt Anzahl der wartenden Uploads zurück
     */
    suspend fun getPendingUploadCount(): Int = withContext(Dispatchers.IO) {
        try {
            queueDir.listFiles { _, name -> 
                name.endsWith(".meta") 
            }?.count { metaFile ->
                try {
                    val metadata = metaFile.readText()
                    !metadata.contains("uploaded=true")
                } catch (e: Exception) {
                    false
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Zählen der wartenden Uploads: ${e.message}")
            0
        }
    }
    
    /**
     * Bereinigt alte erfolgreich hochgeladene Dateien
     */
    suspend fun cleanupOldFiles(maxAgeHours: Int = 24): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
            var deletedCount = 0
            
            queueDir.listFiles { _, name -> name.endsWith(".meta") }?.forEach { metaFile ->
                try {
                    val metadata = metaFile.readText()
                    if (metadata.contains("uploaded=true")) {
                        val lines = metadata.lines()
                        val timestamp = lines.find { it.startsWith("timestamp=") }
                            ?.substringAfter("=")?.toLongOrNull() ?: 0L
                        
                        if (timestamp < cutoffTime) {
                            val filename = lines.find { it.startsWith("filename=") }
                                ?.substringAfter("=") ?: ""
                            val imageFile = File(localStorageDir, filename)
                            
                            if (imageFile.exists()) imageFile.delete()
                            metaFile.delete()
                            deletedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fehler beim Bereinigen alter Dateien: ${e.message}")
                }
            }
            
            Log.i(TAG, "$deletedCount alte Dateien bereinigt")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Bereinigen alter Dateien: ${e.message}")
            0
        }
    }
}

/**
 * Datenklasse für wartende Uploads
 */
data class PendingUpload(
    val landmarkId: String,
    val landmarkName: String,
    val description: String,
    val timestamp: Long,
    val imageFile: File,
    val metadataFile: File
)