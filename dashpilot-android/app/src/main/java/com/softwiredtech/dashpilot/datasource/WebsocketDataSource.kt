package com.softwiredtech.dashpilot.datasource

import com.google.gson.Gson
import com.softwiredtech.dashpilot.datamodel.WorldMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import okhttp3.*
import okio.ByteString

class WebsocketDataSource() : IDataSource {
    private val _incoming = MutableSharedFlow<String>()
    override val incomingMessages: Flow<WorldMessage> =
        _incoming.map { json -> gson.fromJson(json, WorldMessage::class.java) }
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val messages: SharedFlow<String> = _messages

    private val gson = Gson()

    override fun connect(address: String) {
        val request = Request.Builder()
            .url("ws://${address}")
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
            _messages.tryEmit(bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            t.printStackTrace()
        }
    }
}
