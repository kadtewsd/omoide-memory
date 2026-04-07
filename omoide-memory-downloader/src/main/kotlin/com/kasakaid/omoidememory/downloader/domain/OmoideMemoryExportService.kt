package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.domain.LocationService
import com.kasakaid.omoidememory.domain.MediaMetadata
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.PhotoMetadata
import com.kasakaid.omoidememory.domain.SourceFile
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

@Service
class OmoideMemoryExportService(
    private val locationService: LocationService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun export(
        tempPath: Path,
        fileName: String,
        sourceFile: SourceFile,
        omoideBackupPath: Path,
        mediaType: MediaType,
        familyId: String,
    ): Either<DriveService.WriteError, OmoideMemory> =
        withContext(Dispatchers.IO) {
            either {
                val metadata: MediaMetadata =
                    tryIo(tempPath) {
                        mediaType.createMediaMetadata(LocalFile(path = tempPath, name = fileName)).right()
                    }.bind()

                logger.debug { "captureTime が判明。${metadata.capturedTime} ${fileName}のファイルパスを決める" }

                val finalTargetPath =
                    tryIo(tempPath) {
                        FileOrganizeService
                            .determineTargetPath(
                                fileName = fileName,
                                captureTime = metadata.capturedTime,
                                omoideBackupDirectory = omoideBackupPath,
                            ).right()
                    }.bind()

                logger.debug { "${fileName}のファイルパスが $finalTargetPath となったので、$tempPath を削除" }
                tryIo(finalTargetPath) {
                    FileOrganizeService.moveToTarget(sourcePath = tempPath, targetPath = finalTargetPath).right()
                }.bind()

                tryIo(setOf(tempPath, finalTargetPath)) {
                    Files.deleteIfExists(tempPath).right()
                }.bind()
                logger.debug { "一時ファイル $fileName を削除完了" }

                tryIo(setOf(tempPath, finalTargetPath)) {
                    val finalMetadata =
                        mediaType.createMediaMetadata(
                            LocalFile(path = finalTargetPath, name = finalTargetPath.fileName.toString()),
                        )
                    val locationName =
                        if (finalMetadata is PhotoMetadata) {
                            finalMetadata.gpsDirectory?.geoLocation?.let { geo ->
                                if (geo.isZero) {
                                    null
                                } else {
                                    locationService.getLocationName(geo.latitude, geo.longitude)
                                }
                            }
                        } else {
                            null
                        }

                    finalMetadata
                        .toMedia(
                            sourceFile = sourceFile,
                            familyId = familyId,
                            locationName = locationName,
                        ).mapLeft {
                            logger.error { "一時ファイル $fileName のメディア化失敗。" }
                            logger.error { OneLineLogFormatter.format(it.ex) }
                            logger.error { it.ex }
                            DriveService.WriteError(finalTargetPath)
                        }.onRight {
                            logger.debug { "${it.captureTime} で ${it.localPath.name} のエンティティ化が成功" }
                        }
                }.bind()
            }
        }

    private suspend fun <T> tryIo(
        path: Path,
        block: suspend () -> Either<*, T>,
    ): Either<DriveService.WriteError, T> =
        tryIo(setOf(path)) {
            block()
        }

    private suspend fun <T> tryIo(
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
}
