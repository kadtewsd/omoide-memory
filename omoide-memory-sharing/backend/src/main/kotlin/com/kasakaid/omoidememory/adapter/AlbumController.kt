package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.command.AlbumCommandService
import com.kasakaid.omoidememory.service.command.CreateAlbumCommand
import com.kasakaid.omoidememory.service.query.AlbumDownloadQueryService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/albums")
@CrossOrigin
class AlbumController(
    private val albumCommandService: AlbumCommandService,
    private val albumDownloadQueryService: AlbumDownloadQueryService,
) {
    @PostMapping
    suspend fun createAlbum(
        @RequestBody request: CreateAlbumRequest,
    ): ResponseEntity<CreateAlbumResponse> {
        val album =
            albumCommandService.createAlbum(
                CreateAlbumCommand(
                    albumName = request.albumName,
                    photoIds = request.photoIds,
                ),
            )
        return ResponseEntity.ok(
            CreateAlbumResponse(
                albumId = album.id,
                albumName = album.name,
                count = album.photoIds.size,
            ),
        )
    }

    @PostMapping("/download")
    fun downloadAlbumZip(
        @RequestBody request: CreateAlbumRequest,
    ): Mono<ResponseEntity<DataBuffer>> {
        val encodedFileName = URLEncoder.encode("${request.albumName}.zip", StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_OCTET_STREAM
                contentDisposition =
                    ContentDisposition
                        .builder("attachment")
                        .filename(encodedFileName, StandardCharsets.UTF_8)
                        .build()
            }

        return albumDownloadQueryService
            .downloadAlbumZip(request.photoIds)
            .map { buffer ->
                ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(buffer)
            }
    }
}
