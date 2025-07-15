package com.example.arwalking

data class RouteWrapper(
    val route: RouteData
)

data class RouteData(
    val path: List<PathItem>
)

data class PathItem(
    val routeParts: List<RoutePart>
)

data class RoutePart(
    val instruction: String,
    val landmarks: List<Landmark>
)

data class Landmark(
    val id: String,
    val type: String,
    val x: String,
    val y: String,
    val nameDe: String?,
    val nameEn: String?
)