plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.apollo.published)
  id("com.google.devtools.ksp")
}

apolloLibrary(
    javaModuleName = "com.apollographql.apollo3.debug"
)

dependencies {
  implementation(project(":apollo-normalized-cache"))
  implementation(project(":apollo-execution"))
  implementation(libs.androidx.startup.runtime)

  ksp(project(":apollo-ksp"))
  ksp(apollo.apolloKspProcessor(file("src/main/resources/schema.graphqls"), "apolloDebug", "com.apollographql.apollo3.debug.internal.graphql"))

  testImplementation(libs.kotlin.test.junit)
}

android {
  compileSdk = libs.versions.android.sdkversion.compile.get().toInt()
  namespace = "com.apollographql.apollo3.debug"

  defaultConfig {
    minSdk = libs.versions.android.sdkversion.min.get().toInt()
  }
}
