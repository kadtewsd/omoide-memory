package com.kasakaid.omoidememory.ui

/**
 * アプリ起動時または外部連携（通知タップ・ディープリンク等）によって
 * 初期画面遷移を行うターゲット状態（振る舞い）を表す Enum。
 *
 * ## 設計背景
 * アプリケーション内の画面ルート（Route 文字列）の正本を一元管理します。
 * 単なる文字列（String）ではなく Enum で状態遷移を型定義することにより、
 * [AppRouter] において `when` によるパターンマッチで各状態に応じた固有の振る舞い
 * （画面遷移、引数の受け渡し、後処理など）を安全かつ網羅的に分岐決定できるようにします。
 * 今後、通知経由での新たなアクションや振る舞いが増えた場合にも、この Enum にケースを追加し
 * パターンマッチ側で振る舞いを定義することで容易に拡張できます。
 */
enum class InitialRoute(
    val route: String,
) {
    /**
     * メイン画面（通常起動時など、追加遷移を行わないデフォルト状態）
     */
    MAIN(route = "main"),

    /**
     * アップロード再開画面（アップロード中断・エラー通知タップ時の遷移）
     */
    PENDING(route = "pending"),

    /**
     * 写真選択画面（アップロード対象選択）
     */
    WAITING_FOR_UPLOAD(route = "selection"),

    /**
     * 除外写真画面
     */
    UPLOAD_EXCLUDED(route = "excluded"),

    /**
     * アップロード完了写真画面
     */
    UPLOAD_DONE(route = "uploaded_maintenance"),

    /**
     * メンテナンス画面
     */
    MAINTENANCE(route = "maintenance"),
    ;

    companion object {
        /**
         * ルート文字列から対応する [InitialRoute] を解決します。
         * null や未定義のルート文字列の場合は、デフォルトの [MAIN] を返します。
         *
         * @param route 画面ルート文字列（例: "pending", "main" など）
         * @return 対応する [InitialRoute]。未定義または null の場合は [MAIN]
         */
        fun fromRoute(route: String?): InitialRoute = entries.firstOrNull { it.route == route } ?: MAIN
    }
}
