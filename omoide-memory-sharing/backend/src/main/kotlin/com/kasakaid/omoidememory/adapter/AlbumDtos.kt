package com.kasakaid.omoidememory.adapter

import java.util.UUID

class CreateAlbumRequest(
    val albumName: String,
    val photoIds: List<UUID>,
)

class CreateAlbumResponse(
    val albumId: UUID,
    val albumName: String,
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
