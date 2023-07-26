package com.apollographql.apollo3.cache.normalized.internal

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal actual class Lock {
  private val lock: ReentrantLock = reentrantLock() as ReentrantLock

  actual fun <T> read(block: () -> T): T {
    return lock.withLock(block)
  }

  actual fun <T> write(block: () -> T): T {
    return lock.withLock(block)
  }
}
