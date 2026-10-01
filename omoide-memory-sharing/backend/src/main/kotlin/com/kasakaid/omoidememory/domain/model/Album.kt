package com.kasakaid.omoidememory.domain.model

import java.util.UUID

class Album(
    val id: UUID,
    val name: String,
    val photoIds: List<UUID>,
    val familyId: String,
) {
    fun renew(
        name: String,
        photoIds: List<UUID>,
    ): Album =
        Album(
            id = this.id,
            name = name,
            photoIds = photoIds,
            familyId = this.familyId,
        )
}
