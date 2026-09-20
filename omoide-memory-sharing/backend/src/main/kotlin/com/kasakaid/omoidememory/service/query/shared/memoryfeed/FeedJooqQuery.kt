package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.r2dbc.logging.withMdc
import com.kasakaid.omoidememory.service.query.shared.OmoideMemoryTable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL

fun DSLContext.createMemoryQuery(
    omoideMemory: OmoideMemoryTable,
    condition: OmoideCondition,
): SelectConditionStep<Record> {
    val dateCondition =
        when {
            condition.startInclusive != null && condition.endExclusive != null -> {
                omoideMemory.captureTime
                    .ge(condition.startInclusive)
                    .and(omoideMemory.captureTime.lt(condition.endExclusive))
            }

            else -> {
                DSL.noCondition()
            }
        }

    val cursorCondition =
        condition.cursor?.let { c ->
            omoideMemory.captureTime
                .lt(c.captureTime)
                .or(
                    omoideMemory.captureTime
                        .eq(c.captureTime)
                        .and(omoideMemory.id.lt(c.id)),
                )
        } ?: DSL.noCondition()

    val commentCondition =
        when (condition.filterMode) {
            FilterMode.COMMENT_ONLY -> {
                val commentDateCondition =
                    when {
                        condition.startInclusive != null && condition.endExclusive != null -> {
                            COMMENT_OMOIDE.COMMENTED_AT
                                .ge(condition.startInclusive)
                                .and(COMMENT_OMOIDE.COMMENTED_AT.lt(condition.endExclusive))
                        }

                        else -> {
                            DSL.noCondition()
                        }
                    }
                DSL.exists(
                    selectOne()
                        .from(COMMENT_OMOIDE)
                        .where(
                            COMMENT_OMOIDE.FILE_NAME
                                .eq(omoideMemory.fileName)
                                .and(commentDateCondition),
                        ),
                )
            }

            FilterMode.ALL -> {
                DSL.noCondition()
            }
        }

    return select(
        listOf(
            omoideMemory.id,
            omoideMemory.type,
            omoideMemory.fileName,
            omoideMemory.captureTime,
        ),
    ).from(omoideMemory.table)
        .where(
            dateCondition
                .and(cursorCondition)
                .and(commentCondition),
        )
}

suspend fun DSLContext.executeWithContentOrder(
    omoideMemoryTable: OmoideMemoryTable,
    condition: OmoideCondition,
    limit: Int,
): List<Record> =
    withMdc()
        .createMemoryQuery(omoideMemory = omoideMemoryTable, condition = condition)
        .orderBy(
            omoideMemoryTable.captureTime.desc(),
            omoideMemoryTable.id.desc(),
        ).limit(limit)
        .asFlow()
        .toList()
