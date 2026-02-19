package com.kasakaid.omoidememory.r2dbc.transaction

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.r2dbc.R2DBCDSLContext
import com.kasakaid.omoidememory.r2dbc.addDSLContext
import com.kasakaid.omoidememory.r2dbc.getR2DBCContext
import com.kasakaid.omoidememory.r2dbc.logger
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.springframework.stereotype.Component
import kotlin.coroutines.cancellation.CancellationException

@Component
class TransactionExecutor(
    private val r2DBCDSLContext: R2DBCDSLContext,
) {

    suspend fun <LEFT : TransactionRollbackException, RIGHT> executeWithPerLineLeftRollback(
        requestId: String,
        block: suspend CoroutineScope.() -> Either<LEFT, RIGHT>,
    ): Either<TransactionAttemptFailure, RIGHT> {
        val propagatedDSLContext = currentCoroutineContext().getR2DBCContext()
        val outer = propagatedDSLContext?.dsl() ?: r2DBCDSLContext.get()
        return outer.transactionCoroutine { inner ->
            try {
                inner.dsl().transactionCoroutine { config ->
                    withContext(MDCContext(mapOf("requestId" to requestId))) {
                        withContext(
                            context = currentCoroutineContext().addDSLContext(dslContext = config.dsl()),
                            block = block,
                        ).fold(
                            ifLeft = {
                                throw it // ロールバックさせる
                            },
                            ifRight = { it.right() },
                        )
                    }
                }
            } catch (e: CancellationException) {
                logger.error { "[requestId=$requestId] ${OneLineLogFormatter.format(e)}" }
                throw e
            } catch (e: TransactionRollbackException) {
                logger.warn { "明示的なロールバック [requestId=$requestId] ${OneLineLogFormatter.format(e)}" }
                e.left()
            } catch (e: Throwable) {
                logger.error { "[requestId=$requestId] ${OneLineLogFormatter.format(e)}" }
                TransactionAttemptFailure.Unmanaged(e).left()
            }
        }
    }
}