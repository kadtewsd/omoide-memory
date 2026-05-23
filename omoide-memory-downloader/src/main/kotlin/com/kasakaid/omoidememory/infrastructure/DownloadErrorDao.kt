package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.DOWNLOAD_ERROR
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class ErrorLog(
    val errorMessage: String,
    val stackTrace: String,
)

fun Throwable.toErrorLog(): ErrorLog =
    ErrorLog(
        errorMessage = this.message!!,
        stackTrace = this.stackTraceToString().take(1000),
    )

@Repository
class DownloadErrorDao(
    private val dslContext: DSLContext,
) {
    suspend fun save(
        fileName: String,
        errorLog: ErrorLog,
    ) {
        val current = OffsetDateTime.now()
        DOWNLOAD_ERROR.run {
            dslContext
                .insertInto(this)
                .set(FILE_NAME, fileName)
                .set(ERROR_MESSAGE, errorLog.errorMessage)
                .set(STACK_TRACE, errorLog.stackTrace)
                .set(CREATED_AT, current)
                .set(UPDATED_AT, current)
                .onDuplicateKeyUpdate()
                .set(ERROR_MESSAGE, errorLog.errorMessage)
                .set(STACK_TRACE, errorLog.stackTrace)
                .set(UPDATED_AT, current)
                .awaitFirstOrNull()
        }
    }

    suspend fun delete(fileName: String) {
        DOWNLOAD_ERROR.run {
            dslContext
                .deleteFrom(this)
                .where(FILE_NAME.eq(fileName))
                .awaitFirstOrNull()
        }
    }
}
