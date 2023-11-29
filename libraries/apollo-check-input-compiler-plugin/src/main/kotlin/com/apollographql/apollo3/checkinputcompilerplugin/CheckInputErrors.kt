package com.apollographql.apollo3.checkinputcompilerplugin

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory

internal object CheckInputErrors {
  val CONSTRUCTOR_INVOCATION_WRONG_NUMBER_OF_ARGS by error0<PsiElement>()
  val CONSTRUCTOR_INVOCATION_ARG_IS_ABSENT by error0<PsiElement>()

  init {
    RootDiagnosticRendererFactory.registerFactory(
        object : BaseDiagnosticRendererFactory() {
          override val MAP = KtDiagnosticFactoryToRendererMap("CheckInputErrors").apply {
            put(CONSTRUCTOR_INVOCATION_WRONG_NUMBER_OF_ARGS, "@oneOf input class constructor must have exactly one argument")
            put(CONSTRUCTOR_INVOCATION_ARG_IS_ABSENT, "@oneOf input class constructor argument must be Present")
          }
        }
    )
  }
}
