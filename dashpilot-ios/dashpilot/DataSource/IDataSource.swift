protocol IDataSource: AnyObject {
    func connect(address: String)
    func disconnect()
    var incomingMessages: AsyncStream<CarState> { get }
}
