package com.kasakaid.omoidememory.domain.model

import java.util.UUID

enum class AlbumStatus {
    DRAFT,
    CONFIRMED,
}

class Album(
    val id: UUID,
    val name: String,
    val status: AlbumStatus,
    val photoIds: List<UUID>,
    val familyId: String,
) {
    fun renew(
        name: String,
        status: AlbumStatus,
        photoIds: List<UUID>,
    ): Album =
        Album(
            id = this.id,
            name = name,
            status = status,
            photoIds = photoIds,
            familyId = this.familyId,
        )
}
