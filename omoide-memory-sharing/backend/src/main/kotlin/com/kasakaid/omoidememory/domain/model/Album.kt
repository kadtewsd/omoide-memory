package com.kasakaid.omoidememory.domain.model

import java.util.UUID

class Album(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val photoIds: List<UUID>,
    val familyId: String = "OMOIDE_FAMILY",
)
