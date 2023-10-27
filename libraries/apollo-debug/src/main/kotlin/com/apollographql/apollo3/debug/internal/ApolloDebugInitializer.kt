package com.apollographql.apollo3.debug.internal

import android.content.Context
import androidx.startup.Initializer

internal class ApolloDebugInitializer : Initializer<Unit> {
  override fun create(context: Context) {
    packageName = context.packageName
  }

  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

  companion object {
    lateinit var packageName: String
  }
}
