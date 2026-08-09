package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.command.AlbumCommandService
import com.kasakaid.omoidememory.service.command.CreateAlbumCommand
import com.kasakaid.omoidememory.service.query.album.AlbumDetailDto
import com.kasakaid.omoidememory.service.query.album.AlbumDownloadQueryService
import com.kasakaid.omoidememory.service.query.album.AlbumQueryService
import com.kasakaid.omoidememory.service.query.album.AlbumSummaryDto
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
    private val albumQueryService: AlbumQueryService,
) {
    @GetMapping
    suspend fun getAlbums(): List<AlbumSummaryDto> = albumQueryService.getAlbums()

    @GetMapping("/{albumId}")
    suspend fun getAlbumDetail(
        @PathVariable albumId: UUID,
    ): AlbumDetailDto = albumQueryService.getAlbumDetail(albumId)

    @PostMapping
    suspend fun createAlbum(
        @RequestBody request: CreateAlbumRequest,
    ): CreateAlbumResponse {
        val album =
            albumCommandService.createAlbum(
                CreateAlbumCommand(
                    albumName = request.albumName,
                    photoIds = request.photoIds,
                ),
            )
        return CreateAlbumResponse(
            albumId = album.id,
            albumName = album.name,
            count = album.photoIds.size,
        )
    }

    @PostMapping("/download")
    suspend fun downloadAlbumZip(
        @RequestBody request: CreateAlbumRequest,
    ): ResponseEntity<DataBuffer> {
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

        val buffer = albumDownloadQueryService.downloadAlbumZip(request.photoIds)
        return ResponseEntity
            .ok()
            .headers(headers)
            .body(buffer)
    }
}
