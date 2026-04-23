package com.softwiredtech.dashpilot.datasource

import com.google.gson.Gson
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import okhttp3.*
import okio.ByteString

class WebsocketDataSource() : IDataSource {

    private val _incoming = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingMessages: Flow<CarState> =
        _incoming.map { json -> gson.fromJson(json, CarState::class.java) }
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null


    private val gson = Gson()

    override fun connect(address: String) {
        val request = Request.Builder()
            .url("ws://${address}:8080")
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _incoming.tryEmit(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            _incoming.tryEmit(bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            t.printStackTrace()
        }
    }
}
