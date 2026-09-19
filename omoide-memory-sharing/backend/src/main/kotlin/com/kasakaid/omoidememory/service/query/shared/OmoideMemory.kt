package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoidePhoto.Companion.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoideVideo.Companion.SYNCED_OMOIDE_VIDEO
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

interface OmoideMemory {
    val table: Table<*>
    val id: Field<UUID?>
    val type: Field<String>
    val fileName: Field<String?>
    val captureTime: Field<OffsetDateTime?>
}

class SyncedOmoideMemoryPhoto : OmoideMemory {
    private val targetTable: SyncedOmoidePhoto = SYNCED_OMOIDE_PHOTO
    override val table: Table<*> = targetTable
    override val id: Field<UUID?> = targetTable.ID
    override val type: Field<String> = DSL.inline("PHOTO").`as`("type")
    override val fileName: Field<String?> = targetTable.FILE_NAME
    override val captureTime: Field<OffsetDateTime?> = targetTable.CAPTURE_TIME
}

class SyncedOmoideMemoryVideo : OmoideMemory {
    private val targetTable: SyncedOmoideVideo = SYNCED_OMOIDE_VIDEO
    override val table: Table<*> = targetTable
    override val id: Field<UUID?> = targetTable.ID
    override val type: Field<String> = DSL.inline("VIDEO").`as`("type")
    override val fileName: Field<String?> = targetTable.FILE_NAME
    override val captureTime: Field<OffsetDateTime?> = targetTable.CAPTURE_TIME
}
