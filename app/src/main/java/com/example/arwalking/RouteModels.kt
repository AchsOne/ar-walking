data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathItem>,
    val routeInfo: RouteInfo
)

data class PathItem(
    val xmlName: String,
    val routeParts: List<RoutePart>
)

data class RoutePart(
    val instruction: String,
    val instructionEn: String,
    val instructionDe: String,
    val landmarks: List<Landmark>
)

data class Landmark(
    val id: String,
    val nameDe: String,
    val nameEn: String,
    val type: String,
    val x: String,
    val y: String,
    val lsf: String? = null
)

data class RouteInfo(
    val routeLength: Double
)