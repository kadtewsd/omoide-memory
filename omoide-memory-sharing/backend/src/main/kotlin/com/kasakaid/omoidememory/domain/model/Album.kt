package com.kasakaid.omoidememory.domain.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters
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
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
) {
    val photoIds: List<UUID> get() = contents.map { it.photoId }

    fun confirm(): Album =
        Album(
            id = id,
            name = name,
            status = AlbumStatus.CONFIRMED,
            contents = contents,
            familyId = familyId,
            periodFrom = periodFrom,
            periodTo = periodTo,
        )

    fun clearContentsAndChangePeriod(
        periodFrom: LocalDate,
        periodTo: LocalDate,
    ): Album =
        Album(
            id = id,
            name = name,
            status = status,
            contents = emptyList(),
            familyId = familyId,
            periodFrom = periodFrom,
            periodTo = periodTo,
        )

    companion object {
        fun initial(
            name: String,
            familyId: String,
        ): Album {
            val today = LocalDate.now()
            return Album(
                id = UUID.randomUUID(),
                name = name,
                status = AlbumStatus.DRAFT,
                contents = emptyList(),
                familyId = familyId,
                periodFrom = today.withDayOfMonth(1),
                periodTo = today.with(TemporalAdjusters.lastDayOfMonth()),
            )
        }
    }
}
