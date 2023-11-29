package com.apollographql.apollo3.annotations.codegen

// TODO Make the codegen output it on Input objects
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ApolloCodegenInputObject(val graphQLName: String, val isOneOf: Boolean = false)
