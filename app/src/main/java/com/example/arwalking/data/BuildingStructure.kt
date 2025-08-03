package com.example.arwalking.data

import com.example.arwalking.FeatureLandmark

/**
 * Datenstrukturen für die Organisation von Landmarks nach Gebäuden und Stockwerken
 */

data class BuildingFolder(
    val id: String,
    val name: String,
    val shortName: String,
    val floors: List<FloorFolder>,
    val totalImages: Int = 0
)

data class FloorFolder(
    val id: String,
    val name: String,
    val floorNumber: Int,
    val buildingId: String,
    val landmarks: List<FeatureLandmark>,
    val imageCount: Int = landmarks.size
)

/**
 * Vordefinierte Gebäudestruktur der Universität Regensburg
 */
object UniversityBuildings {
    
    fun getDefaultBuildingStructure(): List<BuildingFolder> {
        return listOf(
            BuildingFolder(
                id = "pt",
                name = "Philosophie",
                shortName = "PT",
                floors = listOf(
                    FloorFolder(
                        id = "pt_eg",
                        name = "Philosophie (PT) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "pt",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "pt_1og",
                        name = "Philosophie (PT) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "pt",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "pt_2og",
                        name = "Philosophie (PT) 2. Obergeschoss",
                        floorNumber = 2,
                        buildingId = "pt",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "rw",
                name = "Recht und Wirtschaft",
                shortName = "RW",
                floors = listOf(
                    FloorFolder(
                        id = "rw_eg",
                        name = "Recht und Wirtschaft (RW) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "rw",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "rw_1og",
                        name = "Recht und Wirtschaft (RW) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "rw",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "rw_2og",
                        name = "Recht und Wirtschaft (RW) 2. Obergeschoss",
                        floorNumber = 2,
                        buildingId = "rw",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "bio",
                name = "Biologie und Vorklinische Medizin",
                shortName = "BIO",
                floors = listOf(
                    FloorFolder(
                        id = "bio_eg",
                        name = "Biologie (BIO) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "bio",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "bio_1og",
                        name = "Biologie (BIO) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "bio",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "phy",
                name = "Physik",
                shortName = "PHY",
                floors = listOf(
                    FloorFolder(
                        id = "phy_eg",
                        name = "Physik (PHY) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "phy",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "phy_1og",
                        name = "Physik (PHY) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "phy",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "che",
                name = "Chemie und Pharmazie",
                shortName = "CHE",
                floors = listOf(
                    FloorFolder(
                        id = "che_eg",
                        name = "Chemie (CHE) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "che",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "che_1og",
                        name = "Chemie (CHE) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "che",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "mat",
                name = "Mathematik",
                shortName = "MAT",
                floors = listOf(
                    FloorFolder(
                        id = "mat_eg",
                        name = "Mathematik (MAT) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "mat",
                        landmarks = emptyList()
                    ),
                    FloorFolder(
                        id = "mat_1og",
                        name = "Mathematik (MAT) 1. Obergeschoss",
                        floorNumber = 1,
                        buildingId = "mat",
                        landmarks = emptyList()
                    )
                )
            ),
            BuildingFolder(
                id = "zv",
                name = "Zentralverwaltung",
                shortName = "ZV",
                floors = listOf(
                    FloorFolder(
                        id = "zv_eg",
                        name = "Zentralverwaltung (ZV) Erdgeschoss",
                        floorNumber = 0,
                        buildingId = "zv",
                        landmarks = emptyList()
                    )
                )
            )
        )
    }
    
    /**
     * Ordnet ein Landmark basierend auf seiner Position einem Gebäude/Stockwerk zu
     */
    fun assignLandmarkToBuilding(landmark: FeatureLandmark): Pair<String, Int>? {
        val building = landmark.position.building
        val floor = landmark.position.floor
        
        return if (building != null && floor != null) {
            Pair(building, floor)
        } else {
            // Fallback: Versuche aus dem Namen zu extrahieren
            extractBuildingFromName(landmark.name)
        }
    }
    
    private fun extractBuildingFromName(name: String): Pair<String, Int>? {
        val upperName = name.uppercase()
        
        // Extract floor information from name
        val floor = when {
            upperName.contains("1. OG") || upperName.contains("1OG") || upperName.contains("FIRST FLOOR") -> 1
            upperName.contains("2. OG") || upperName.contains("2OG") || upperName.contains("SECOND FLOOR") -> 2
            upperName.contains("3. OG") || upperName.contains("3OG") || upperName.contains("THIRD FLOOR") -> 3
            upperName.contains("ERDGESCHOSS") || upperName.contains("EG") || upperName.contains("GROUND FLOOR") -> 0
            else -> 0 // Default to ground floor
        }
        
        val building = when {
            upperName.contains("PT") || upperName.contains("PHILOSOPHIE") -> "pt"
            upperName.contains("RW") || upperName.contains("RECHT") || upperName.contains("WIRTSCHAFT") -> "rw"
            upperName.contains("BIO") || upperName.contains("BIOLOGIE") -> "bio"
            upperName.contains("PHY") || upperName.contains("PHYSIK") -> "phy"
            upperName.contains("CHE") || upperName.contains("CHEMIE") -> "che"
            upperName.contains("MAT") || upperName.contains("MATHEMATIK") -> "mat"
            upperName.contains("ZV") || upperName.contains("VERWALTUNG") -> "zv"
            else -> null
        }
        
        return if (building != null) Pair(building, floor) else null
    }
}