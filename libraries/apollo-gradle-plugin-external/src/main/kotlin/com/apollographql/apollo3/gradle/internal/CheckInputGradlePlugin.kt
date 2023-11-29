package com.apollographql.apollo3.gradle.internal

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class CheckInputGradlePlugin : KotlinCompilerPluginSupportPlugin {
  override fun getCompilerPluginId(): String = "com.apollographql.apollo3.checkinputcompilerplugin"

  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> = kotlinCompilation.target.project.provider {
    emptyList()
  }

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
      groupId = "com.apollographql.apollo3",
      artifactId = "apollo-check-input-compiler-plugin",
      version = "4.0.0-beta.3-SNAPSHOT" // TODO gradle magic to not hardcode the version
  )

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
}
