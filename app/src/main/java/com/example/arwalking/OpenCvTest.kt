package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Schritt 1: OpenCV Basis-Test
 * Testet ob OpenCV funktioniert und Bilder geladen werden können
 */
class OpenCvTest(private val context: Context) {

    private val TAG = "OpenCvTest"

    /**
     * Test 1: Lade ein Landmark-Bild und konvertiere zu Mat
     */
    fun testLoadImage(landmarkId: String = "PT-1-926"): Boolean {
        return try {
            Log.i(TAG, "=== TEST 1: Lade Bild $landmarkId ===")

            // 1. Lade Bitmap aus Assets
            val bitmap = loadBitmapFromAssets(landmarkId)
            if (bitmap == null) {
                Log.e(TAG, "❌ Fehler: Konnte Bitmap nicht laden")
                return false
            }

            Log.i(TAG, "✅ Bitmap geladen: ${bitmap.width}x${bitmap.height}")

            // 2. Konvertiere zu Mat
            val mat = bitmapToMat(bitmap)
            Log.i(TAG, "✅ Mat erstellt: ${mat.cols()}x${mat.rows()}, Channels: ${mat.channels()}")

            // 3. Konvertiere zu Graustufen
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            Log.i(TAG, "✅ Graustufen-Konvertierung: ${grayMat.cols()}x${grayMat.rows()}, Channels: ${grayMat.channels()}")

            // 4. Cleanup
            mat.release()
            grayMat.release()

            Log.i(TAG, "✅ TEST 1 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 1 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Test 2: Liste alle verfügbaren Landmark-Bilder auf
     */
    fun testListLandmarkImages(): Boolean {
        return try {
            Log.i(TAG, "=== TEST 2: Liste Landmark-Bilder ===")

            val files = context.assets.list("landmark_images")

            if (files == null || files.isEmpty()) {
                Log.e(TAG, "❌ Keine Bilder im Ordner 'landmark_images' gefunden")
                return false
            }

            Log.i(TAG, "✅ Gefundene Bilder: ${files.size}")
            files.forEachIndexed { index, filename ->
                Log.i(TAG, "  ${index + 1}. $filename")
            }

            Log.i(TAG, "✅ TEST 2 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 2 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Test 3: Lade mehrere Bilder nacheinander
     */
    fun testLoadMultipleImages(): Boolean {
        return try {
            Log.i(TAG, "=== TEST 3: Lade mehrere Bilder ===")

            val files = context.assets.list("landmark_images") ?: emptyArray()
            val imageFiles = files.filter {
                it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg")
            }

            if (imageFiles.isEmpty()) {
                Log.e(TAG, "❌ Keine Bild-Dateien gefunden")
                return false
            }

            val testFiles = imageFiles.take(3) // Teste die ersten 3 Bilder

            testFiles.forEach { filename ->
                val landmarkId = filename.substringBeforeLast(".")
                val bitmap = loadBitmapFromAssets(landmarkId)

                if (bitmap != null) {
                    val mat = bitmapToMat(bitmap)
                    Log.i(TAG, "✅ $filename: ${mat.cols()}x${mat.rows()}")
                    mat.release()
                } else {
                    Log.e(TAG, "❌ $filename: Laden fehlgeschlagen")
                }
            }

            Log.i(TAG, "✅ TEST 3 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 3 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // ============= Hilfsmethoden =============

    private fun loadBitmapFromAssets(landmarkId: String): Bitmap? {
        return try {
            val filename = "$landmarkId.jpg"
            val inputStream = context.assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden von $landmarkId: ${e.message}")
            null
        }
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Führt alle Tests nacheinander aus
     */
    fun runAllTests(): Boolean {
        Log.i(TAG, "\n╔════════════════════════════════════════╗")
        Log.i(TAG, "║   OpenCV Basis-Tests                   ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        val test1 = testLoadImage()
        Thread.sleep(500)

        val test2 = testListLandmarkImages()
        Thread.sleep(500)

        val test3 = testLoadMultipleImages()

        Log.i(TAG, "\n╔════════════════════════════════════════╗")
        Log.i(TAG, "║   Test-Ergebnisse:                     ║")
        Log.i(TAG, "║   Test 1 (Bild laden): ${if (test1) "✅ PASS" else "❌ FAIL"}        ║")
        Log.i(TAG, "║   Test 2 (Liste):      ${if (test2) "✅ PASS" else "❌ FAIL"}        ║")
        Log.i(TAG, "║   Test 3 (Mehrere):    ${if (test3) "✅ PASS" else "❌ FAIL"}        ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        return test1 && test2 && test3
    }
}