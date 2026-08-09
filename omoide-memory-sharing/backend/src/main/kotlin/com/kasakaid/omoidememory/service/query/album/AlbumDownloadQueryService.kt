package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.service.query.MemoryFeedDtoConverter
import com.kasakaid.omoidememory.service.query.shared.PhotoQueryService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class AlbumDownloadQueryService(
    private val photoQueryService: PhotoQueryService,
) {
    private val bufferFactory = DefaultDataBufferFactory()

    /**
     * 指定された写真IDリスト（[photoIds]）に該当する写真群を取得し、それらを1つのZIPアーカイブとして圧縮した [DataBuffer] を生成します。
     *
     * 写真データの読み込みには [MemoryFeedDtoConverter.transformPhotoToDto] を再利用しています。
     * 変換後の [com.kasakaid.omoidememory.service.query.MemoryFeedDto.contentBase64] は `"data:image/jpeg;base64,<Base64エンコードデータ>"`
     * という Data URI 形式の文字列で返却されるため、ヘッダー情報（`"data:...;base64,"`）を [BASE64_HEADER_DELIMITER] で切り離した上で
     * バイナリデータへとデコードし、ZIPアーカイブのエントリとして書き出しています。
     *
     * @param photoIds ダウンロード対象の写真IDリスト
     * @return ZIPファイルの内容がラップされた [DataBuffer]
     */
    suspend fun downloadAlbumZip(photoIds: List<UUID>): DataBuffer {
        val photos = photoQueryService.findPhotosByIds(photoIds)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            photos
                .map { photo -> photo to MemoryFeedDtoConverter.transformPhotoToDto(photo, emptyList()) }
                .mapNotNull { (photo, dto) ->
                    dto.contentBase64?.let { contentBase64 ->
                        photo.fileName to Base64.getDecoder().decode(contentBase64.substringAfter(BASE64_HEADER_DELIMITER))
                    }
                }.map { (fileName, bytes) ->
                    zos.putNextEntry(ZipEntry(fileName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
        }

        return bufferFactory.wrap(baos.toByteArray())
    }
}

/**
 * Data URI 形式の Base64 文字列（`"data:<mimeType>;base64,<data>"`）から
 * 純粋な Base64 エンコードデータ本体を切り出すためのデリミタ文字列。
 */
private const val BASE64_HEADER_DELIMITER = "base64,"
