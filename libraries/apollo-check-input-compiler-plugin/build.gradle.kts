plugins {
  id("org.jetbrains.kotlin.jvm")
}

apolloLibrary(
    javaModuleName = "com.apollographql.apollo3.checkinputcompilerplugin"
)

dependencies {
  api(project(":apollo-annotations"))
  compileOnly(libs.kotlin.compiler.embeddable)
}
