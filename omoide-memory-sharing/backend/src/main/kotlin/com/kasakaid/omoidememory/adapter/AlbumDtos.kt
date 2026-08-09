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
