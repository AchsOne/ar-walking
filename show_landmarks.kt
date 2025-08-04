#!/usr/bin/env kotlin

/**
 * Kleines Tool zum Anzeigen der verfügbaren Landmarks aus der route.json
 * 
 * Führe aus mit: kotlinc -script show_landmarks.kt
 */

import java.io.File
import java.util.regex.Pattern

fun main() {
    println("🗺️  AR Walking - Landmark Übersicht")
    println("=" * 50)
    
    val routeJsonFile = File("app/src/main/assets/route.json")
    
    if (!routeJsonFile.exists()) {
        println("❌ route.json nicht gefunden!")
        return
    }
    
    val content = routeJsonFile.readText()
    
    // Extrahiere alle Landmarks aus der JSON
    val landmarkPattern = Pattern.compile(""""landmarks":\s*\[(.*?)\]""", Pattern.DOTALL)
    val landmarkMatcher = landmarkPattern.matcher(content)
    
    val allLandmarks = mutableSetOf<String>()
    var landmarkCount = 0
    
    while (landmarkMatcher.find()) {
        val landmarksSection = landmarkMatcher.group(1)
        
        // Extrahiere einzelne Landmark-Objekte
        val singleLandmarkPattern = Pattern.compile("""\{[^}]*"id":\s*"([^"]+)"[^}]*"nameDe":\s*"([^"]*)"[^}]*"type":\s*"([^"]*)"[^}]*\}""")
        val singleMatcher = singleLandmarkPattern.matcher(landmarksSection)
        
        while (singleMatcher.find()) {
            val id = singleMatcher.group(1)
            val name = singleMatcher.group(2)
            val type = singleMatcher.group(3)
            
            if (name.isNotEmpty()) {
                allLandmarks.add("$id|$name|$type")
                landmarkCount++
            }
        }
    }
    
    println("📍 Gefundene Landmarks in route.json:")
    println("   Gesamt: $landmarkCount Landmarks")
    println()
    
    // Gruppiere nach Typ
    val landmarksByType = allLandmarks.groupBy { it.split("|")[2] }
    
    landmarksByType.forEach { (type, landmarks) ->
        println("🏷️  Typ: $type (${landmarks.size} Stück)")
        landmarks.forEach { landmark ->
            val parts = landmark.split("|")
            val id = parts[0]
            val name = parts[1]
            println("   • $name")
            println("     ID: $id")
        }
        println()
    }
    
    println("📸 Empfohlene Trainingsbilder:")
    println("   Erstelle Fotos für diese wichtigen Landmarks:")
    
    val importantLandmarks = allLandmarks.filter { 
        val name = it.split("|")[1].lowercase()
        name.contains("prof") || name.contains("büro") || name.contains("eingang") || 
        name.contains("treppe") || name.contains("aufzug") || name.contains("tür")
    }
    
    importantLandmarks.forEach { landmark ->
        val parts = landmark.split("|")
        val id = parts[0]
        val name = parts[1]
        println("   📷 ${name}.jpg (ID: $id)")
    }
    
    println()
    println("💾 Speicherort für Bilder:")
    println("   app/src/main/assets/landmark_images/")
    println()
    println("🔧 Nächste Schritte:")
    println("   1. Mache Fotos von den wichtigen Landmarks")
    println("   2. Benenne sie nach dem Schema: {landmark_id}.jpg")
    println("   3. Kopiere sie in das landmark_images Verzeichnis")
    println("   4. Teste das Feature-Mapping in der App")
}

operator fun String.times(n: Int): String = this.repeat(n)

main()