package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.domain.model.FilePathFinder
import com.kasakaid.omoidememory.service.query.shared.PhotoQueryService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class AlbumDownloadQueryService(
    private val photoQueryService: PhotoQueryService,
    private val filePathFinder: FilePathFinder,
) {
    private val bufferFactory = DefaultDataBufferFactory()

    /**
     * 指定された写真IDリスト（[photoIds]）に該当する写真群を取得し、それらを1つのZIPアーカイブとして圧縮した [DataBuffer] を生成します。
     *
     * @param photoIds ダウンロード対象の写真IDリスト
     * @return ZIPファイルの内容がラップされた [DataBuffer]
     */
    suspend fun downloadAlbumZip(photoIds: List<UUID>): DataBuffer {
        val photos = photoQueryService.findPhotosByIds(photoIds)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            photos.forEach { photo ->
                val path = filePathFinder.findPath(photo.serverPath) ?: return@forEach
                val bytes = try {
                    Files.readAllBytes(path)
                } catch (_: Exception) {
                    return@forEach
                }
                zos.putNextEntry(ZipEntry(photo.fileName))
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        return bufferFactory.wrap(baos.toByteArray())
    }
}
