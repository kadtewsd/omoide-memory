package com.kasakaid.omoidememory.runnerlock

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.RUNNER_LOCK
import com.kasakaid.omoidememory.runnerlock.jdbc.JooqConfig
import com.kasakaid.omoidememory.runnerlock.jdbc.JooqConfig.dslContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import java.time.OffsetDateTime

/**
 * Simple DB based lock to prevent double execution of the same runner.
 *
 * The lock is represented by a single row in the `runner_lock` table with a
 * primary key `runner_name`. The `created_at` column records when the lock was
 * acquired. The table is defined as:
 *
 * ```sql
 * CREATE TABLE IF NOT EXISTS runner_lock (
 *   runner_name VARCHAR PRIMARY KEY,
 *   created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
 * );
 * ```
 *
 * The implementation deliberately avoids Spring DI and transaction handling – it
 * performs a single INSERT (which will fail on duplicate primary‑key) and a
 * straightforward DELETE. This satisfies the requirement of inserting before
 * runner start and deleting after runner termination.
 */
object RunnerLock {
    /**
     * Attempt to acquire a lock for the given runner name.
     *
     * @return true if the lock was obtained, false if a lock already exists.
     */
    fun tryLock(runnerName: String): Boolean =
        RUNNER_LOCK.run {
            return try {
                // Insert a new row; if runner_name already exists the DB will raise
                // a unique‑violation error which we translate into a false result.
                dslContext
                    .insertInto(RUNNER_LOCK)
                    .set(RUNNER_NAME, runnerName)
                    .set(CREATED_AT, OffsetDateTime.now())
                    .execute()
                true
            } catch (e: DataAccessException) {
                // Duplicate key – lock already held by another process.
                false
            }
        }

    /**
     * Release the lock for the given runner name.
     */
    fun release(runnerName: String) =
        RUNNER_LOCK.run {
            dslContext
                .deleteFrom(RUNNER_LOCK)
                .where(RUNNER_NAME.eq(runnerName))
                .execute()
        }
}
