import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.library")
  alias(libs.plugins.apollo.published)
  id("com.google.devtools.ksp")
}

apolloLibrary(
    javaModuleName = "com.apollographql.apollo3.debugserver",
    withLinux = false,
)

kotlin {
  sourceSets {
    findByName("commonMain")?.apply {
      kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")

      dependencies {
        implementation(project(":apollo-normalized-cache"))
        implementation(project(":apollo-execution"))
      }
    }

    findByName("androidMain")?.apply {
      dependencies {
        implementation(libs.androidx.startup.runtime)
      }
    }
  }
}

dependencies {
  add("kspCommonMainMetadata", project(":apollo-ksp"))
  add("kspCommonMainMetadata", apollo.apolloKspProcessor(file("src/androidMain/resources/schema.graphqls"), "apolloDebugServer", "com.apollographql.apollo3.debugserver.internal.graphql"))
}

android {
  compileSdk = libs.versions.android.sdkversion.compile.get().toInt()
  namespace = "com.apollographql.apollo3.debugserver"

  defaultConfig {
    minSdk = libs.versions.android.sdkversion.min.get().toInt()
  }
}

tasks.withType<KotlinCompile> {
  dependsOn("kspCommonMainKotlinMetadata")
}

tasks.withType<Kotlin2JsCompile> {
  dependsOn("kspCommonMainKotlinMetadata")
}

tasks.withType<KotlinNativeCompile> {
  dependsOn("kspCommonMainKotlinMetadata")
}

tasks.all {
  if (name.endsWith("sourcesJar", ignoreCase = true)) {
    dependsOn("kspCommonMainKotlinMetadata")
  }
}
