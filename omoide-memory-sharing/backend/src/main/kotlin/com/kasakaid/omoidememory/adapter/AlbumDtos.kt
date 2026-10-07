package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.domain.model.AlbumStatus
import java.time.OffsetDateTime
import java.util.UUID

class CreateAlbumResource(
    val albumName: String,
)

class AddContentResource(
    val photoId: UUID,
    val capturedAt: OffsetDateTime?,
)

class AlbumResponse(
    val albumId: UUID,
    val albumName: String,
    val status: AlbumStatus,
)

class StartAlbumDownloadResponse(
    val jobId: UUID,
    val albumId: UUID,
    val status: String,
)

class DownloadProgressEvent(
    val jobId: UUID,
    val processed: Int,
    val total: Int,
    val percentage: Int,
)

class DownloadCompletedEvent(
    val jobId: UUID,
    val downloadUrl: String,
    val fileName: String,
)
