package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.service.query.shared.PhotoQueryService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class AlbumDownloadQueryService(
    private val photoQueryService: PhotoQueryService,
) {
    private val bufferFactory = DefaultDataBufferFactory()

    fun downloadAlbumZip(photoIds: List<UUID>): Mono<DataBuffer> =
        photoQueryService
            .findPhotosByIds(photoIds)
            .collectList()
            .map { photos ->
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { zos ->
                    val addedNames = mutableSetOf<String>()
                    photos.forEach { photo ->
                        val serverPath = photo.serverPath
                        if (!serverPath.isNullOrEmpty()) {
                            val path = Paths.get(serverPath)
                            if (Files.exists(path)) {
                                var fileName = photo.fileName ?: path.fileName.toString()
                                if (addedNames.contains(fileName)) {
                                    fileName = "${photo.id}_$fileName"
                                }
                                addedNames.add(fileName)
                                val entry = ZipEntry(fileName)
                                zos.putNextEntry(entry)
                                Files.copy(path, zos)
                                zos.closeEntry()
                            }
                        }
                    }
                }
                bufferFactory.wrap(baos.toByteArray())
            }
}
