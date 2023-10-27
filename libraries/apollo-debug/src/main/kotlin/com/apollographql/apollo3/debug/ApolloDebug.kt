package com.apollographql.apollo3.debug

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.debug.internal.ApolloDebugInitializer
import com.apollographql.apollo3.debug.internal.graphql.GraphQL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.PrintStream
import java.util.concurrent.Executors

object ApolloDebug {
  internal const val SOCKET_NAME_PREFIX = "apollo_debug_"

  private val apolloClients = mutableMapOf<ApolloClient, String>()
  private var server: ApolloDebugServer? = null

  fun registerApolloClient(apolloClient: ApolloClient, id: String = "client") {
    if (apolloClients.containsKey(apolloClient)) error("Client '$apolloClient' already registered")
    if (apolloClients.containsValue(id)) error("Name '$id' already registered")
    apolloClients[apolloClient] = id
    startOrStopAgent()
  }

  fun unregisterApolloClient(apolloClient: ApolloClient) {
    apolloClients.remove(apolloClient)
    startOrStopAgent()
  }

  private fun startOrStopAgent() {
    if (apolloClients.isEmpty()) {
      server?.stop()
    } else {
      if (server == null) {
        server = ApolloDebugServer(apolloClients).apply {
          start()
        }
      }
    }
  }
}

private class ApolloDebugServer(
  apolloClients: Map<ApolloClient, String>,
) {
  private var localServerSocket: LocalServerSocket? = null
  private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
  private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

  private val graphQL = GraphQL(apolloClients)

  fun start() {
    if (localServerSocket != null) error("Already started")
    val localServerSocket = LocalServerSocket("${ApolloDebug.SOCKET_NAME_PREFIX}${ApolloDebugInitializer.packageName}")
    this.localServerSocket = localServerSocket
    coroutineScope.launch {
      while (true) {
        val clientSocket = try {
          localServerSocket.accept()
        } catch (_: Exception) {
          // Server socket has been closed (stop() was called)
          break
        }
        launch { handleClient(clientSocket) }
      }
    }
  }

  private suspend fun handleClient(clientSocket: LocalSocket) {
    try {
      val bufferedReader = clientSocket.inputStream.bufferedReader()
      val printWriter = PrintStream(clientSocket.outputStream.buffered(), true)
      val httpRequest = readHttpRequest(bufferedReader)
      if (httpRequest.method == "OPTIONS") {
        printWriter.print("HTTP/1.1 204 No Content\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: *\r\nAccess-Control-Allow-Headers: *\r\n\r\n")
        return
      }
      printWriter.print("HTTP/1.1 200 OK\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\n\r\n")
      printWriter.print(graphQL.executeGraphQL(httpRequest.body ?: ""))
    } catch (e: CancellationException) {
      // Expected when the server is closed
      throw e
    } catch (_: Exception) {
      // I/O error or otherwise: ignore
    } finally {
      runCatching { clientSocket.close() }
    }
  }

  private class HttpRequest(
    val method: String,
    val path: String,
    val headers: List<Pair<String, String>>,
    val body: String?,
  )

  private fun readHttpRequest(bufferedReader: BufferedReader): HttpRequest {
    val (method, path) = bufferedReader.readLine().split(" ")
    val headers = mutableListOf<Pair<String, String>>()
    while (true) {
      val line = bufferedReader.readLine()
      if (line.isEmpty()) break
      val (key, value) = line.split(": ")
      headers.add(key to value)
    }
    val contentLength = headers.firstOrNull { it.first.equals("Content-Length", ignoreCase = true) }?.second?.toLongOrNull() ?: 0
    val body = if (contentLength <= 0) {
      null
    } else {
      val buffer = CharArray(contentLength.toInt())
      bufferedReader.read(buffer)
      String(buffer)
    }
    return HttpRequest(method, path, headers, body)
  }

  fun stop() {
    runCatching { localServerSocket?.close() }
    coroutineScope.cancel()
    dispatcher.close()
  }
}
