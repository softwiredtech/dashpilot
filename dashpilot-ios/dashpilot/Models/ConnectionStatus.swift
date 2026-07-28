enum ConnectionStatus: Equatable {
    case disconnected
    case connecting
    case connected
    /// Terminal failure with a user-facing message (mirrors the Android
    /// `ConnectionStatus.Error`). Treated like `disconnected` for reconnects.
    case error(String)
}
