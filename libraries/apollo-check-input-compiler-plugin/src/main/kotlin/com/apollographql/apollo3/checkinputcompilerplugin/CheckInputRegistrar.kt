package com.apollographql.apollo3.checkinputcompilerplugin

import com.apollographql.apollo3.annotations.codegen.ApolloCodegenInputObject
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.resolve.providers.getSymbolByTypeRef
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalCompilerApi::class)
internal class CheckInputCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    FirExtensionRegistrarAdapter.registerExtension(CheckInputFirExtensionRegistrar(messageCollector))
  }
}

internal class CheckInputFirExtensionRegistrar(private val messageCollector: MessageCollector) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +{ session: FirSession -> CheckInputFirAdditionalCheckersExtension(session, messageCollector) }
  }
}

internal class CheckInputFirAdditionalCheckersExtension(
    session: FirSession,
    messageCollector: MessageCollector,
) : FirAdditionalCheckersExtension(session) {
  override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
    override val callCheckers: Set<FirCallChecker> = setOf(CheckInputFirCallChecker(session, messageCollector))
  }
}

internal class CheckInputFirCallChecker(
    private val session: FirSession,
    private val messageCollector: MessageCollector,
) : FirCallChecker() {
  override fun check(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    // messageCollector.logi("${expression.source!!.getElementTextInContextForDebug()}")
    val functionCall = expression as? FirFunctionCall ?: return
    val returnTypeRef = (functionCall.toResolvedCallableSymbol() as? FirConstructorSymbol)?.resolvedReturnTypeRef ?: return
    val returnTypeSymbol: FirClassLikeSymbol<*> = session.symbolProvider.getSymbolByTypeRef(returnTypeRef) ?: return
    val isCodegenOneOfInputObject = returnTypeSymbol.isCodegenOneOfInputObject()
    if (!isCodegenOneOfInputObject) return
    if (functionCall.argumentList.arguments.size != 1) {
      reporter.reportOn(expression.source, CheckInputErrors.CONSTRUCTOR_INVOCATION_WRONG_NUMBER_OF_ARGS, context)
      return
    }
    val argument = functionCall.argumentList.arguments.first()
    val argumentResolvedType = argument.resolvedType.classId?.asFqNameString() ?: return
    if (argumentResolvedType == "com.apollographql.apollo3.api.Optional.Absent") {
      reporter.reportOn(argument.source, CheckInputErrors.CONSTRUCTOR_INVOCATION_ARG_IS_ABSENT, context)
    }
  }
}

private fun FirClassLikeSymbol<*>.codegenInputObjectAnnotation(): FirAnnotation? =
    annotations.firstOrNull { it.resolvedType.classId!!.asFqNameString() == ApolloCodegenInputObject::class.java.name }

private fun FirClassLikeSymbol<*>.isCodegenOneOfInputObject(): Boolean {
  val isOneOfExpression = codegenInputObjectAnnotation()?.argumentMapping?.mapping?.get(Name.identifier("isOneOf")) as? FirConstExpression<*>
  return isOneOfExpression?.value == true
}

private fun MessageCollector.logi(message: String) {
  report(CompilerMessageSeverity.INFO, "CheckInputCompilerPlugin - $message")
}
