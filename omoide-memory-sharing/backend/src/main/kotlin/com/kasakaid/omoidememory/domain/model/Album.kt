package com.kasakaid.omoidememory.domain.model

import java.time.OffsetDateTime
import java.util.UUID

enum class AlbumStatus {
    DRAFT,
    CONFIRMED,
}

class AlbumContent(
    val photoId: UUID,
    val capturedAt: OffsetDateTime?,
)

class Album(
    val id: UUID,
    val name: String,
    val status: AlbumStatus,
    val contents: List<AlbumContent>,
    val familyId: String,
) {
    val photoIds: List<UUID> get() = contents.map { it.photoId }

    fun confirm(): Album =
        Album(
            id = id,
            name = name,
            status = AlbumStatus.CONFIRMED,
            contents = contents,
            familyId = familyId,
        )

    companion object {
        fun initial(
            name: String,
            familyId: String,
        ): Album =
            Album(
                id = UUID.randomUUID(),
                name = name,
                status = AlbumStatus.DRAFT,
                contents = emptyList(),
                familyId = familyId,
            )
    }
}
