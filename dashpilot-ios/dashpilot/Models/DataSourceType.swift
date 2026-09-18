/// The transport a car-data session runs over (mirrors the Android
/// `DataSourceType` constants).
enum DataSourceType: String {
    case comma
    case dashkit
    case websocket
    case demo

    var displayName: String {
        switch self {
        case .comma: return "comma"
        case .dashkit: return "DashKit"
        case .websocket: return "WebSocket"
        case .demo: return "Demo"
        }
    }
}
