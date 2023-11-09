package com.apollographql.apollo3.network.websocket

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.exception.ApolloRetryException
import com.apollographql.apollo3.exception.ApolloWebSocketClosedException
import com.apollographql.apollo3.exception.DefaultApolloException
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [SubscribableWebSocket] is the link between the lower level [WebSocket] and GraphQL
 *
 * [startOperation] starts a new operation and calls [WebSocketOperationListener] when the server sends messages.
 *
 * [SubscribableWebSocket] does not connect to the server until [connect] is called. This allows to start adding
 * listeners but only trigger them once network becomes available or after an exponential backoff period.
 *
 * Once the lower level [WebSocket] is closed the [SubscribableWebSocket] stays in the [State.Disconnected]
 * until a new [WebSocket] can be connected
 */
internal class SubscribableWebSocket(
    webSocketEngine: WebSocketEngine,
    url: String,
    headers: List<HttpHeader>,
    private val idleTimeoutMillis: Long,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onDisposed: () -> Unit,
    private val dispatcher: CoroutineDispatcher,
    private val wsProtocol: WsProtocol,
    private val reopenWhen: suspend (Throwable, Long) -> Boolean,
    private val pingIntervalMillis: Long,
    private val connectionAcknowledgeTimeoutMillis: Long,
    private val attempt: Long,
) : WebSocketListener {
  // webSocket is thread safe, no need to lock
  private var webSocket: WebSocket = webSocketEngine.newWebSocket(url, headers, this@SubscribableWebSocket)
  private val scope = CoroutineScope(dispatcher + SupervisorJob())

  // locked fields, these fields may be accessed from different threads and require locking
  private val lock = reentrantLock()
  private var timeoutJob: Job? = null
  private var state: State = State.Initial
  private var activeListeners = mutableMapOf<String, ActiveOperationListener>()
  private var idleJob: Job? = null
  private var pingJob: Job? = null
  // end of locked fields

  fun connect() {
    webSocket.connect()
  }

  private suspend fun disconnect(throwable: Throwable) {
    lock.withLock {
      pingJob?.cancel()
      pingJob = null
      if (state != State.Disconnected) {
        state = State.Disconnected
      } else {
        return
      }
    }
    // Tell upstream that this socket is disconnected
    // No new listener is added after this
    onDisconnected()

    val listeners = lock.withLock {
      activeListeners.values
    }

    /**
     * If there are no listeners, no need to call reopen at all
     *
     * Note that there is no concept of "normal" or "error" termination in that case. Whether
     * the TCP socket times out, the client idle timeout fires or the server closes the connection
     * it's all the same since there are no listeners.
     */
    val reopen = if (listeners.isNotEmpty()) {
      reopenWhen.invoke(throwable, attempt)
    } else {
      false
    }

    onDisposed()

    listeners.forEach {
      val cause = if (reopen) {
        ApolloRetryException(attempt, throwable)
      } else {
        throwable
      }
      it.operationListener.onError(cause)
    }
  }

  override fun onOpen() {
    lock.withLock {
      when (state) {
        State.Initial -> {
          scope.launch(dispatcher) {
            webSocket.send(wsProtocol.connectionInit())
          }
          timeoutJob = scope.launch(dispatcher) {
            delay(connectionAcknowledgeTimeoutMillis)
            webSocket.close(CLOSE_GOING_AWAY, "Timeout while waiting for connection_ack")
            disconnect(DefaultApolloException("Timeout while waiting for ack"))
          }
          state = State.AwaitAck
        }

        else -> {
          // spurious "open" event
        }
      }
    }
  }

  private fun listenerFor(id: String): WebSocketOperationListener? = lock.withLock {
    activeListeners.get(id)?.let {
      if (it.terminated) {
        null
      } else {
        it.operationListener
      }
    }
  }

  override fun onMessage(text: String) {
    when (val message = wsProtocol.parseServerMessage(text)) {
      ConnectionAckServerMessage -> {
        timeoutJob?.cancel()
        timeoutJob = null
        onConnected()
        lock.withLock {
          state = State.Connected
          if (pingIntervalMillis > 0) {
            pingJob = scope.launch {
              while (true) {
                delay(pingIntervalMillis)
                wsProtocol.ping()?.let { webSocket.send(it) }
              }
            }
          }
        }
      }

      is ConnectionErrorServerMessage -> {
        scope.launch {
          webSocket.close(CLOSE_GOING_AWAY, "Connection Error")
          disconnect(DefaultApolloException("Received connection_error"))
        }
      }

      is ResponseServerMessage -> {
        listenerFor(message.id)?.let {
          it.onResponse(message.response)
          if (message.complete) {
            it.onComplete()
          }
        }
      }

      is CompleteServerMessage -> {
        listenerFor(message.id)?.onComplete()
      }

      is OperationErrorServerMessage -> {
        listenerFor(message.id)?.onError(DefaultApolloException("Server send an error ${message.payload}"))
      }

      is ParseErrorServerMessage -> {
        // This is an unknown or malformed message
        // It's not 100% clear what we should do here. Should we terminate the operation?
        println("Cannot parse message: '${message.errorMessage}'")
      }

      PingServerMessage -> {
        scope.launch {
          val pong = wsProtocol.pong()
          if (pong != null) {
            webSocket.send(pong)
          }
        }
      }

      PongServerMessage -> {
        // nothing to do
      }

      ConnectionKeepAliveServerMessage ->  {
        // nothing to do?
      }

      is GeneralErrorServerMessage -> {
        lock.withLock {
          activeListeners.values.forEach {
            it.terminated = true
          }
        }
        scope.launch { disconnect(message.exception) }
      }
    }
  }

  override fun onMessage(data: ByteArray) {
    onMessage(data.decodeToString())
  }

  override fun onError(throwable: Throwable) {
    scope.launch(dispatcher) {
      disconnect(throwable)
    }
  }

  override fun onClosed(code: Int?, reason: String?) {
    scope.launch(dispatcher) {
      disconnect(ApolloWebSocketClosedException(code ?: CLOSE_NORMAL, reason))
    }
  }

  fun <D : Operation.Data> startOperation(request: ApolloRequest<D>, listener: WebSocketOperationListener): StartedOperation {
    val added = lock.withLock {
      idleJob?.cancel()
      idleJob = null

      if (activeListeners.containsKey(request.requestUuid.toString())) {
        false
      } else {
        activeListeners.put(request.requestUuid.toString(), ActiveOperationListener(listener, false))
        true
      }
    }

    if (!added) {
      listener.onError(DefaultApolloException("There is already a subscription with that id"))
      return StartedOperationNoOp
    }

    scope.launch { webSocket.send(wsProtocol.operationStart(request)) }

    return DefaultStartedOperation(request)
  }

  fun closeConnection(throwable: Throwable) {
    scope.launch { disconnect(throwable) }
  }

  inner class DefaultStartedOperation<D: Operation.Data>(val request: ApolloRequest<D>): StartedOperation {
    override fun stop() {
      lock.withLock {
        val id = request.requestUuid.toString()
        if (activeListeners.remove(id) != null) {
          scope.launch { webSocket.send(wsProtocol.operationStop(request)) }
        }

        if (activeListeners.isEmpty()) {
          idleJob?.cancel()
          idleJob = scope.launch {
            delay(idleTimeoutMillis)
            disconnect(DefaultApolloException("WebSocket is idle"))
          }
        }
      }
    }
  }

  internal fun cancel() {
    webSocket.close(CLOSE_GOING_AWAY, "Cancelled")
    scope.cancel()
  }
}

internal interface StartedOperation {
  /**
   * Sends a message to the server to stop the operation and removes the listener.
   * No further call to the listener are made
   */
  fun stop()
}

private enum class State {
  Initial,
  AwaitAck,
  Connected,
  Disconnected

}

private val StartedOperationNoOp = object : StartedOperation {
  override fun stop() {}
}

private fun WebSocket.send(clientMessage: ClientMessage) {
  when (clientMessage) {
    is TextClientMessage -> send(clientMessage.text)
    is DataClientMessage -> send(clientMessage.data)
  }
}

/**
 * @param terminated a state that signals that a general error happened and all future WebSocket messages
 * must be ignored. This is to make it robust to [WsProtocol] that send spurious messages.
 *
 * [WebSocketOperationListener] is called directly from the [WebSocketListener.onMessage] thread except for
 * [GeneralErrorServerMessage] that needs to suspend for [reopenWhen].
 * In that state, all websocket messages are ignored
 */
private class ActiveOperationListener(val operationListener: WebSocketOperationListener, var terminated: Boolean)

