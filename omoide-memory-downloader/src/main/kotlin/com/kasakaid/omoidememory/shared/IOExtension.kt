package com.kasakaid.omoidememory.shared

import arrow.core.Either
import arrow.core.left
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

suspend fun <T> tryIo(
    path: Path,
    block: suspend () -> Either<*, T>,
): Either<DriveService.WriteError, T> =
    tryIo(setOf(path)) {
        block()
    }

suspend fun <T> tryIo(
    paths: Set<Path>,
    block: suspend () -> Either<*, T>,
): Either<DriveService.WriteError, T> =
    try {
        block().mapLeft {
            DriveService.WriteError(paths)
        }
    } catch (e: Exception) {
        logger.error { "書き込みでエラーが発生: ${OneLineLogFormatter.format(e)}" }
        logger.error { e }
        DriveService.WriteError(paths).left()
    }
