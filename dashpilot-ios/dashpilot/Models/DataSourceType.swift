/// The transport a car-data session runs over (mirrors the Android
/// `DataSourceType` constants).
enum DataSourceType: String {
    case comma
    case dashkit
    case websocket
}
