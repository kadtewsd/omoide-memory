package com.kasakaid.omoidememory.domain.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class AlbumStatus {
    DRAFT,
    CONFIRMED,
}

typealias AlbumId = UUID

class AlbumContent(
    val id: UUID,
    val albumId: AlbumId,
    val photoId: UUID,
    val capturedAt: OffsetDateTime?,
) {
    companion object {
        operator fun invoke(
            albumId: AlbumId,
            photoId: UUID,
            capturedAt: OffsetDateTime?,
        ): AlbumContent =
            AlbumContent(
                id = UUID.randomUUID(),
                albumId = albumId,
                photoId = photoId,
                capturedAt = capturedAt,
            )
    }
}

data class Album(
    val id: UUID,
    val name: String,
    val status: AlbumStatus,
    val familyId: String,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
) {
    fun confirm(): Album =
        copy(
            status = AlbumStatus.CONFIRMED,
        )

    fun clearContentsAndChangePeriod(
        periodFrom: LocalDate,
        periodTo: LocalDate,
    ): Album = copy(periodFrom = periodFrom, periodTo = periodTo)

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
                familyId = familyId,
                periodFrom = today.withDayOfMonth(1),
                periodTo = today.with(TemporalAdjusters.lastDayOfMonth()),
            )
        }
    }
}
