package com.kasakaid.omoidememory.utility

import com.kasakaid.omoidememory.adapter.filter.MdcFilterKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import reactor.util.context.Context
import kotlin.collections.map

object CoroutineHelper {
    suspend fun <Y, T> List<T>.mapWithCoroutine(
        semaphore: Semaphore = Semaphore(10),
        uniqueIdGetter: (T) -> String = { it.toString() },
        block: suspend (T) -> Y,
    ): List<Y> =
        coroutineScope {
            map {
                async {
                    semaphore.withPermit {
                        // コンテキストに MDC を注入して withContext で requestId をログに出せるようにする
                        val reactorContext = ReactorContext(Context.of(MdcFilterKey.MDC_DATA_KEY, mapOf("requestId" to uniqueIdGetter(it))))
                        withContext(reactorContext) {
                            block(it)
                        }
                    }
                }
            }.awaitAll()
        }

    suspend fun <K, V, Y> Collection<Map.Entry<K, V>>.forEachIndexedWithCoroutine(
        semaphore: Semaphore,
        uniqueIdGetter: (K, V) -> String = { key, _ -> key.toString() },
        block: suspend (Int, Map.Entry<K, V>) -> Unit,
    ) {
        coroutineScope {
            forEachIndexed { index, entry ->
                launch {
                    semaphore.withPermit {
                        // コンテキストに MDC を注入して withContext で requestId をログに出せるようにする
                        val reactorContext =
                            ReactorContext(
                                Context.of(
                                    MdcFilterKey.MDC_DATA_KEY,
                                    mapOf("requestId" to uniqueIdGetter(entry.key, entry.value)),
                                ),
                            )
                        withContext(reactorContext) {
                            block(index, entry)
                        }
                    }
                }
            }
        }
    }
}
