package com.example.arwalking.data

import com.google.gson.annotations.SerializedName

/**
 * Main route data structure matching the JSON format
 */
data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathElement>
)

data class PathElement(
    val xmlName: String,
    val levelInfo: LevelInfo,
    val xmlNameEn: String,
    val xmlNameDe: String,
    val xmlFile: String,
    val routeParts: List<RoutePart>
)

data class LevelInfo(
    val storeyNameEn: String,
    val storeyName: String,
    val width: String,
    val storeyNameDe: String,
    val id: String,
    val calculatedxratio: String,
    val storey: String,
    val height: String,
    val mapfile: String,
    val calculatedyratio: String,
    val transformationMatrix: TransformationMatrix
)

data class TransformationMatrix(
    @SerializedName("EPSG3857") val epsg3857: EPSG3857,
    val wlon: Double,
    val ylon: Double,
    val xlon: Double,
    val ylat: Double,
    val xlat: Double,
    val wlat: Double
)

data class EPSG3857(
    val wlon: Double,
    val ylon: Double,
    val xlon: Double,
    val ylat: Double,
    val xlat: Double,
    val wlat: Double
)

data class RoutePart(
    val iconID: String,
    val nodes: List<NodeElement>,
    val instruction: String,
    val instructionEn: String,
    val landmarks: List<Landmark>,
    val landmarkFromInstruction: String? = null,
    val instructionDe: String
)

data class NodeElement(
    val node: Node,
    val edge: Edge? = null
)

data class Node(
    val isdestination: String? = null,
    val name: String? = null,
    val x: String,
    val y: String,
    val id: String,
    val label: String,
    val type: String,
    val lsf: String? = null,
    val roomid: String? = null,
    val oldroomid: String? = null
)

data class Edge(
    val dx: String,
    val cx: String,
    val dy: String,
    val bx: String,
    val cy: String,
    val ax: String,
    val by: String,
    val lengthInMeters: String,
    val ay: String,
    val id: String,
    val type: String
)

data class Landmark(
    val nameDe: String,
    val x: String,
    val y: String,
    val nameEn: String,
    val id: String,
    val type: String,
    val lsf: String? = null
)

/**
 * Processed navigation step for the app
 */
data class NavigationStep(
    val id: String,
    val instruction: String,
    val instructionDe: String,
    val landmarkId: String?,
    val arrowDirection: ArrowDirection,
    val landmarks: List<Landmark>,
    val position: Position
)

data class Position(
    val x: Float,
    val y: Float
)

enum class ArrowDirection {
    FORWARD,
    LEFT,
    RIGHT,
    BACK,
    UP,
    DOWN,
    NONE;

    companion object {
        fun fromInstruction(instruction: String): ArrowDirection {
            val lowerInstruction = instruction.lowercase()
            return when {
                lowerInstruction.contains("links") || lowerInstruction.contains("left") -> LEFT
                lowerInstruction.contains("rechts") || lowerInstruction.contains("right") -> RIGHT
                lowerInstruction.contains("zurück") || lowerInstruction.contains("back") -> BACK
                lowerInstruction.contains("hoch") || lowerInstruction.contains("up") ||
                    lowerInstruction.contains("treppe") || lowerInstruction.contains("stairs") -> UP
                lowerInstruction.contains("runter") || lowerInstruction.contains("down") -> DOWN
                lowerInstruction.contains("gerade") || lowerInstruction.contains("straight") ||
                    lowerInstruction.contains("durch") || lowerInstruction.contains("through") -> FORWARD
                else -> FORWARD
            }
        }
    }
}
