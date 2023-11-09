package com.apollographql.apollo3.network.websocket

internal interface WebSocketOperationListener {
  /**
   * A response was received
   *
   * [response] is the Kotlin representation of a GraphQL response.
   *
   * ```kotlin
   * mapOf(
   *   "data" to ...
   *   "errors" to listOf(...)
   * )
   * ```
   */
  fun onResponse(response: Any?)

  /**
   * The operation terminated successfully. No calls to listener will be made
   */
  fun onComplete()

  /**
   * The operation cannot be executed or failed. No calls to listener will be made.
   *
   * If [throwable] is an instance of [com.apollographql.apollo3.exception.ApolloRetryException],
   * the user [WebSocketNetworkTransport.reopenWhen] returned true
   */
  fun onError(throwable: Throwable)
}


