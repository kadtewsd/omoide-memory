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
import com.kasakaid.omoidememory.shared.tryIo
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.name

@Service
class OmoideMemoryFactory(
    private val locationService: LocationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * ソースファイルを omoideBackupPath 配下の正式な格納先へ配置し OmoideMemory を返す。
     *
     * ファイルの配置方法（移動 or コピー）は呼び出し元が [placeFile] として渡す。
     * - GDrive 側: ダウンロード済み一時ファイルを移動（[FileOrganizeService.moveToTarget]）
     * - ローカル取り込み: 取込元ファイルをコピー（[FileOrganizeService.copyToTarget]）
     */
    suspend fun createOmoideMemoryFrom(
        sourcePath: Path,
        sourceFile: SourceFile,
        omoideBackupPath: Path,
        mediaType: MediaType,
        familyId: String,
    ): Either<DriveService.WriteError, OmoideMemory> =
        withContext(Dispatchers.IO) {
            either {
                val fileName = sourcePath.fileName.toString()

                // メタデータ抽出は sourcePath に対して一度だけ行う
                val metadata: MediaMetadata =
                    tryIo(sourcePath) {
                        mediaType.createMediaMetadata(LocalFile(path = sourcePath, name = fileName)).right()
                    }.bind()

                logger.debug { "captureTime が判明。${metadata.capturedTime} ${fileName}のファイルパスを決める" }

                val finalTargetPath =
                    tryIo(sourcePath) {
                        FileOrganizeService
                            .determineTargetPath(
                                fileName = fileName,
                                captureTime = metadata.capturedTime,
                                omoideBackupDirectory = omoideBackupPath,
                            ).right()
                    }.bind()

                val locationName =
                    if (metadata is PhotoMetadata) {
                        metadata.gpsDirectory?.geoLocation?.let { geo ->
                            if (geo.isZero) null else locationService.getLocationName(geo.latitude, geo.longitude)
                        }
                    } else {
                        null
                    }

                metadata
                    .toMedia(
                        sourceFile = sourceFile,
                        familyId = familyId,
                        locationName = locationName,
                    ).mapLeft {
                        logger.error { "${fileName}のメディア化失敗。" }
                        logger.error { OneLineLogFormatter.format(it.ex) }
                        logger.error { it.ex }
                        DriveService.WriteError(finalTargetPath)
                    }.map { omoideMemory ->
                        omoideMemory.fixPath(finalTargetPath)
                    }.onRight {
                        logger.debug { "${it.captureTime} で ${it.localPath.name} のエンティティ化が成功" }
                    }.bind()
            }
        }
}
