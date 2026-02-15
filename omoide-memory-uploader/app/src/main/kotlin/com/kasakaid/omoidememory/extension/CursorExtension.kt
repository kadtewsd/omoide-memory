package com.kasakaid.omoidememory.extension

import android.database.Cursor

/**
 * Cursor を拡張して関数ライクに扱う拡張
 */
object CursorExtension {
    /**
     * シーケンスに変換する
     */
    fun Cursor.asSequence(): Sequence<Cursor> {
        return generateSequence {
            if (this.moveToNext()) this else null
        }
    }
}