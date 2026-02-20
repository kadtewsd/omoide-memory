package com.kasakaid.omoidememory.utility

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.collections.map

object CoroutineHelper {
    suspend fun <Y, T> List<T>.mapWithCoroutine(
        semaphore: Semaphore,
        block: suspend (T) -> Y,
    ): List<Y> =
        coroutineScope {
            map {
                async {
                    semaphore.withPermit {
                        block(it)
                    }
                }
            }
        }.awaitAll()
}
