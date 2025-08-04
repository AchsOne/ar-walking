package com.example.arwalking

import com.google.gson.annotations.SerializedName

/**
 * Datenmodelle für das Parsen der route.json
 */

data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathItem>,
    val routeInfo: RouteInfo? = null
)

data class PathItem(
    val xmlName: String,
    val xmlNameEn: String?,
    val xmlNameDe: String?,
    val xmlFile: String?,
    val levelInfo: LevelInfo?,
    val routeParts: List<RoutePart>
)

data class LevelInfo(
    val storeyNameEn: String?,
    val storeyName: String?,
    val storeyNameDe: String?,
    val width: String?,
    val height: String?,
    val id: String?,
    val storey: String?,
    val mapfile: String?,
    val calculatedxratio: String?,
    val calculatedyratio: String?,
    val transformationMatrix: TransformationMatrix?
)

data class TransformationMatrix(
    @SerializedName("EPSG3857")
    val epsg3857: EPSG3857?,
    val wlon: Double?,
    val ylon: Double?,
    val xlon: Double?,
    val ylat: Double?,
    val xlat: Double?,
    val wlat: Double?
)

data class EPSG3857(
    val wlon: Double?,
    val ylon: Double?,
    val xlon: Double?,
    val ylat: Double?,
    val xlat: Double?,
    val wlat: Double?
)

data class RoutePart(
    val iconID: String?,
    val nodes: List<NodeWrapper>?,
    val landmarks: List<RouteLandmarkData> = emptyList(),
    val instruction: String?,
    val instructionDe: String?,
    val instructionEn: String?,
    val landmarkFromInstruction: String? = null,
    val distance: Double? = null,
    val duration: Int? = null
)

data class NodeWrapper(
    val node: NodeData,
    val edge: EdgeData? = null
)

data class NodeData(
    val id: String,
    val name: String?,
    val label: String?,
    val x: String?,
    val y: String?,
    val type: String?,
    val isdestination: String?,
    val lsf: String?,
    val roomid: String?,
    val oldroomid: String?
)

data class EdgeData(
    val id: String?,
    val type: String?,
    val dx: String?,
    val dy: String?,
    val ax: String?,
    val ay: String?,
    val bx: String?,
    val by: String?,
    val cx: String?,
    val cy: String?,
    val lengthInMeters: String?
)

data class RouteLandmarkData(
    val id: String,
    val nameDe: String?,
    val nameEn: String?,
    val type: String?,
    val x: String?,
    val y: String?,
    val lsf: String? = null
)

data class RouteInfo(
    val routeLength: Double,
    val estimatedTime: Int? = null,
    val difficulty: String? = null
)