package com.kasakaid.omoidememory.ui.maintenance.requestprocess

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kasakaid.omoidememory.extension.sealedClasses
import com.kasakaid.omoidememory.ui.EnumWithLabel

/**
 * アップロード処理の各進行ポイント（ステップ）を表現する sealed interface。
 * 状態を NULL で表現せず、明示的な型として定義する。
 * 各状態は自身の表示色 (Color) をプロパティとして持ち、UI 側での冗長なパターンマッチを排除する。
 */
sealed interface UploadPoint : EnumWithLabel {
    val name: String
    val message: String

    /**
     * 次のステップポイント。
     * 各ステップは自身のプロパティとして次の遷移先を保持する。
     *
     * 【設計背景: NULL 不許可と自己参照の明示】
     * 終端ステップ（全件完了や対象 0 件終了など、次が存在しない最後のステップ）については、
     * NULL を許容する Nullable 型（UploadPoint?）を排除し、完全非 NULL として扱うため、
     * あえて「自分自身 (this)」を設定している。
     * これにより、型安全性を高めると同時に、終端状態でさらに遷移を試みた場合でも
     * 状態が不正に壊れるのを防ぎ、最後の状態を維持する。
     */
    val next: UploadPoint

    val color: Color
        @Composable
        @ReadOnlyComposable
        get

    /**
     * 進行中のステータス群 (tertiary カラーで統一)
     */
    sealed interface Doing : UploadPoint {
        override val color: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.tertiary
    }

    // --- Doing グループ ---
    data object Start : Doing {
        override val name: String = "START"
        override val label: String = "要求開始"
        override val message: String = "アップロード要求開始"
        override val next: UploadPoint get() = WorkerStarted
    }

    data object WorkerStarted : Doing {
        override val name: String = "WORKER_STARTED"
        override val label: String = "ワーカー起動"
        override val message: String = "ワーカー起動 (doWork)"
        override val next: UploadPoint get() = ForegroundSet
    }

    data object TargetEmpty : UploadPoint {
        override val name: String = "TARGET_EMPTY"
        override val label: String = "対象 0 件で終了"
        override val message: String = "アップロード対象が 0 件です"

        // 終端ステータスのため、NULL を不許可にする目的であえて「自分自身 (this)」を指定している
        override val next: UploadPoint get() = this
        override val color: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.secondary
    }

    data object ForegroundSet : Doing {
        override val name: String = "FOREGROUND_SET"
        override val label: String = "フォアグラウンド通知設定完了"
        override val message: String = "フォアグラウンド通知設定完了"
        override val next: UploadPoint get() = NetworkBound
    }

    data object NetworkBound : Doing {
        override val name: String = "NETWORK_BOUND"
        override val label: String = "Wi-Fi バインド完了"
        override val message: String = "Wi-Fi バインド完了"
        override val next: UploadPoint get() = QueryFetched
    }

    data object QueryFetched : Doing {
        override val name: String = "QUERY_FETCHED"
        override val label: String = "対象取得完了"
        override val message: String = "対象取得完了"
        override val next: UploadPoint get() = Uploading
    }

    data object Uploading : Doing {
        override val name: String = "UPLOADING"
        override val label: String = "アップロード中"
        override val message: String = "アップロード中"
        override val next: UploadPoint get() = AllCompleted
    }

    data object AllCompleted : UploadPoint {
        override val name: String = "ALL_COMPLETED"
        override val label: String = "全件完了"
        override val message: String = "全件完了"

        // 終端ステータスのため、NULL を不許可にする目的であえて「自分自身 (this)」を指定している
        override val next: UploadPoint get() = this
        override val color: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.primary
    }

    companion object {
        val entries: List<UploadPoint> = UploadPoint::class.sealedClasses()

        fun fromName(name: String): UploadPoint = entries.firstOrNull { it.name == name } ?: Start
    }
}

/**
 * アップロード実行セッションごとの進行状況・結果を記録する Room Entity。
 *
 * @param id サロゲートキー (自動採番)
 * @param createdAt アップロード処理開始日時 (エポックミリ秒)
 * @param lastPoint 最後に到達・更新されたステップポイント
 * @param totalRequestCount アップロード・削除する予定のトータルの件数
 * @param successCount アップロード成功件数
 * @param updatedAt 最終更新日時 (エポックミリ秒)
 * @param message 最後の処理ファイル名やエラーメッセージなどの詳細情報
 */
@Entity(tableName = "upload_reports")
data class UploadReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val createdAt: Long,
    val lastPoint: UploadPoint,
    val totalRequestCount: Int,
    val successCount: Int,
    val updatedAt: Long,
    val message: String?,
) {
    /**
     * 処理済み件数 (processedCount) を受け取り、進捗を更新します
     * 次のステップへいくまではまだです。
     */
    fun contentUploaded(processedCount: Int): UploadReport =
        copy(
            successCount = processedCount,
            updatedAt = System.currentTimeMillis(),
        )

    /**
     * 次のステップへ進行します。
     */
    fun next(): UploadReport =
        copy(
            updatedAt = System.currentTimeMillis(),
            lastPoint = lastPoint.next,
            message = lastPoint.next.message,
        )

    fun targetEmpty(): UploadReport =
        copy(
            lastPoint = UploadPoint.TargetEmpty,
            message = UploadPoint.TargetEmpty.message,
            updatedAt = System.currentTimeMillis(),
        )

    companion object {
        /**
         * アップロードセッション開始時の初期レポートオブジェクトを生成します。
         * 開始時のステップポイントは常に [UploadPoint.Start] となります。
         * 作成日時および更新日時はドメイン側で生成・保持されます。
         *
         * @param totalRequestCount アップロード・削除する予定のトータルの件数
         */
        fun initial(totalRequestCount: Int): UploadReport {
            val now = System.currentTimeMillis()
            return UploadReport(
                id = 0L,
                createdAt = now,
                lastPoint = UploadPoint.Start,
                totalRequestCount = totalRequestCount,
                successCount = 0,
                updatedAt = now,
                message = UploadPoint.Start.message,
            )
        }
    }
}
