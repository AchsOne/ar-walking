#!/usr/bin/env kotlin

import java.io.File
import java.util.regex.Pattern

fun main() {
    println("🗺️  AR Walking - Landmark Übersicht")
    println("=".repeat(50))

    val routeJsonFile = File("app/src/main/assets/route.json")
    if (!routeJsonFile.exists()) {
        println("❌ route.json nicht gefunden!")
        return
    }

    val content = routeJsonFile.readText()

    val landmarkPattern =
        Pattern.compile("\"landmarks\"\s*:\s*\[(.*?)]", Pattern.DOTALL)
    val landmarkMatcher = landmarkPattern.matcher(content)

    val allLandmarks = mutableSetOf<Triple<String, String, String>>()
    var landmarkCount = 0

    while (landmarkMatcher.find()) {
        val landmarksSection = landmarkMatcher.group(1)

        val singleLandmarkPattern = Pattern.compile(
            "\\{[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^}]*\"nameDe\"\\s*:\\s*\"([^\"]*)\"[^}]*\"type\"\\s*:\\s*\"([^\"]*)\"[^}]*\\}"
        )
        val singleMatcher = singleLandmarkPattern.matcher(landmarksSection)

        while (singleMatcher.find()) {
            val id = singleMatcher.group(1)
            val name = singleMatcher.group(2)
            val type = singleMatcher.group(3)
            if (name.isNotEmpty()) {
                allLandmarks.add(Triple(id, name, type))
                landmarkCount++
            }
        }
    }

    println("📍 Gefundene Landmarks in route.json:")
    println("   Gesamt: $landmarkCount Landmarks\n")

    val landmarksByType = allLandmarks.groupBy { it.third }

    landmarksByType.forEach { (type, landmarks) ->
        println("🏷️  Typ: $type (${landmarks.size} Stück)")
        landmarks.forEach { (id, name, _) ->
            println("   • $name")
            println("     ID: $id")
        }
        println()
    }

    println("📸 Empfohlene Trainingsbilder:")
    println("   Erstelle Fotos für diese wichtigen Landmarks:")

    val importantLandmarks = allLandmarks.filter { (_, name, _) ->
        val lower = name.lowercase()
        lower.contains("prof") || lower.contains("büro") || lower.contains("eingang") ||
        lower.contains("treppe") || lower.contains("aufzug") || lower.contains("tür")
    }

    importantLandmarks.forEach { (id, name, _) ->
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

main()
