package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.domain.model.FilePathFinder
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_PHOTO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import com.kasakaid.omoidememory.service.query.shared.PhotoQueryService
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AlbumZipResult(
    val albumName: String,
    val zipBytes: ByteArray,
)

@Service
class AlbumDownloadQueryService(
    private val dslContext: DSLGenerator,
    private val photoQueryService: PhotoQueryService,
    private val filePathFinder: FilePathFinder,
) {
    /**
     * 指定された [albumId] に紐づく写真群を取得し、ZIPアーカイブバイナリとアルバム名を生成します。
     * [onProgress] コールバックによって進捗状況（処理済み件数、総件数）を通知します。
     *
     * @param albumId ダウンロード対象のアルバムID
     * @param onProgress 進捗コールバック（処理済み件数, 総件数）
     * @return アルバム名とZIPバイナリを保持する [AlbumZipResult]
     */
    suspend fun createAlbumZip(
        albumId: UUID,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): AlbumZipResult {
        val albumPhotoRecords =
            dslContext
                .invoke()
                .selectFrom(ALBUM_PHOTO)
                .where(ALBUM_PHOTO.ALBUM_ID.eq(albumId))
                .asFlow()
                .toList()

        if (albumPhotoRecords.isEmpty()) {
            throw NotFoundException("Album not found with id: $albumId")
        }

        val albumName = albumPhotoRecords.first().albumName ?: "album"
        val photoIds = albumPhotoRecords.mapNotNull { it.photoId }
        val photos = photoQueryService.findPhotosByIds(photoIds)
        val total = photos.size

        onProgress(0, total)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            photos.forEachIndexed { index, photo ->
                val path = filePathFinder.findPath(photo.serverPath)
                if (path != null) {
                    try {
                        val bytes = Files.readAllBytes(path)
                        zos.putNextEntry(ZipEntry(photo.fileName))
                        zos.write(bytes)
                        zos.closeEntry()
                    } catch (_: Exception) {
                        // 読み込み失敗時はスキップ
                    }
                }
                onProgress(index + 1, total)
            }
        }

        return AlbumZipResult(
            albumName = albumName,
            zipBytes = baos.toByteArray(),
        )
    }
}
