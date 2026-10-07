package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.domain.model.AlbumStatus
import java.util.UUID

class AlbumResource(
    val albumName: String,
    val photoIds: List<UUID>,
    val status: AlbumStatus,
)

class AlbumResponse(
    val albumId: UUID,
    val albumName: String,
    val status: AlbumStatus,
    val count: Int,
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
