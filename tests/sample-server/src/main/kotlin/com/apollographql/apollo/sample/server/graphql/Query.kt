package com.apollographql.apollo.sample.server.graphql

import com.apollographql.apollo.sample.server.WebSocketRegistry
import com.apollographql.apollo3.annotations.ApolloObject
import com.apollographql.apollo3.api.ExecutionContext


@ApolloObject
class Query {
  fun random(): Int = 42
  fun time(): Int = 0

  fun receivedMessages(executionContext: ExecutionContext): List<String> {
    val webSocketRegistry = executionContext[WebSocketRegistry]!!
    return webSocketRegistry.getAllMessages()
  }
}
