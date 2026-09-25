package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.command.AlbumCommandService
import com.kasakaid.omoidememory.service.command.CreateAlbumCommand
import com.kasakaid.omoidememory.service.query.album.AlbumDetailDto
import com.kasakaid.omoidememory.service.query.album.AlbumDownloadJobManager
import com.kasakaid.omoidememory.service.query.album.AlbumQueryService
import com.kasakaid.omoidememory.service.query.album.AlbumSummaryDto
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/albums")
@CrossOrigin
class AlbumController(
    private val albumCommandService: AlbumCommandService,
    private val albumDownloadJobManager: AlbumDownloadJobManager,
    private val albumQueryService: AlbumQueryService,
) {
    private val bufferFactory = DefaultDataBufferFactory()

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

    @PostMapping("/{albumId}/download-jobs")
    fun startAlbumDownloadJob(
        @PathVariable albumId: UUID,
    ): ResponseEntity<StartAlbumDownloadResponse> {
        val jobId = albumDownloadJobManager.startJob(albumId)
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(
                StartAlbumDownloadResponse(
                    jobId = jobId,
                    albumId = albumId,
                    status = "PROCESSING",
                ),
            )
    }

    @GetMapping("/download-jobs/{jobId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getJobEvents(
        @PathVariable jobId: UUID,
    ): Flux<ServerSentEvent<Any>> = albumDownloadJobManager.getJobEvents(jobId)

    @GetMapping("/download-jobs/{jobId}/file")
    fun downloadJobFile(
        @PathVariable jobId: UUID,
    ): ResponseEntity<DataBuffer> {
        val result = albumDownloadJobManager.getJobFile(jobId)
        val encodedFileName = URLEncoder.encode("${result.albumName}.zip", StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_OCTET_STREAM
                contentDisposition =
                    ContentDisposition
                        .builder("attachment")
                        .filename(encodedFileName, StandardCharsets.UTF_8)
                        .build()
            }

        val buffer = bufferFactory.wrap(result.zipBytes)
        return ResponseEntity
            .ok()
            .headers(headers)
            .body(buffer)
    }
}
