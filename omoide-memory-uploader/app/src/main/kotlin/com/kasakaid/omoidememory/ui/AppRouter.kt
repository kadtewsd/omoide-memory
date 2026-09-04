package com.kasakaid.omoidememory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kasakaid.omoidememory.extension.popBackStackWithSnackMessage
import com.kasakaid.omoidememory.ui.fileselection.DoneFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.ExcludedFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.FileUploadState
import com.kasakaid.omoidememory.ui.fileselection.TargetFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.upoadtriggered.UploadTriggeredSelectionRoute
import com.kasakaid.omoidememory.ui.maintenance.CrashDetailScreen
import com.kasakaid.omoidememory.ui.maintenance.CrashReportViewerScreen
import com.kasakaid.omoidememory.ui.maintenance.DbMaintenanceScreen
import com.kasakaid.omoidememory.ui.maintenance.MaintenanceScreen

/**
 * アプリケーション全体の画面遷移（Navigation）を一括制御するルーターコンポーザブル。
 *
 * ## `initialRoute` と画面スタックの設計背景（なぜ直感的な実装と異なるのか）
 *
 * 通常の起動では [MainScreen]（route: `"main"`）が最初に表示されますが、
 * 通知（Notification）タップ時や外部ディープリンク連携によって、起動直後から特定のサブ画面
 * （例: アップロードエラー通知から「アップロード再開画面 (`"pending"`)」を開くなど）を
 * 直接開きたいケースが存在します。
 *
 * 直感的には `NavHost(startDestination = initialRoute ?: "main")` のように
 * `startDestination` 自体を切り替えたくなりますが、あえてそうしていません。理由は以下の通りです：
 *
 * ### 1. 自然なバックスタック（戻る操作）の維持
 * - `startDestination` を直接サブ画面（例: `"pending"`）にしてしまうと、そのサブ画面が
 *   バックスタックの最下層（Root）になります。
 * - その結果、サブ画面でユーザーが「戻る」ボタンを押した際に、メイン画面（ホーム）に戻るのではなく、
 *   **アプリがいきなり終了してしまう** という不自然な UX になります。
 * - そこで、常に `"main"` をバックスタックの基底（Root）として構築した上で、指定された画面へと
 *   追加で [androidx.navigation.NavController.navigate] することで、**「指定画面で戻るを押すとメイン画面に戻る」**
 *   という標準的で期待通りのバックスタック遷移を実現しています。
 *
 * ### 2. `onRouteConsumed` によるワンショットイベント消費（多重遷移の防止）
 * - [initialRoute] は画面回転（構成変更: Configuration Change）や再描画（Recomposition）の際にも
 *   状態として保持されてしまう可能性があります。
 * - `LaunchedEffect(initialRoute)` 内で遷移完了後に即座に [onRouteConsumed] を呼び出して
 *   呼び出し元（[MainActivity]）の保持するルートを `null` にリセット（消費）させることで、
 *   回転時などに意図せず同じ画面へ再度遷移してしまう多重ナビゲーションのバグを確実に防いでいます。
 *
 * @param initialRoute 通知タップ等の外部要因によって初期表示したい画面のルート文字列。通常起動時は `null`。
 * @param onRouteConsumed [initialRoute] への遷移が実行された直後に呼ばれるコールバック。呼び出し元で状態を `null` にクリアするために使用。
 */
@Composable
fun AppRouter(
    initialRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    // 外部（通知等）から遷移先ルートが指定された場合、一度だけ対象画面へ遷移して消費する
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute)
            // 画面回転時などに再度 navigate が多重発火しないよう、消費完了を通知して状態を null に戻す
            onRouteConsumed()
        }
    }

    // ここは「どの画面を表示するか」の分岐ロジックだけに専念！
    // startDestination は常に "main" 固定。
    // 通知等から直接別画面を開く場合でも、バックスタックの底に "main" を残すことで
    // 戻るボタンを押した際にメイン画面に正常に戻れるようにする。
    NavHost(
        navController = navController,
        // main が画面の最初になる、と言う設定
        startDestination = "main",
    ) {
        composable("main") { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val snackMessageState = savedStateHandle.getStateFlow<String?>("snack_message", null).collectAsState()

            MainScreen(
                snackMessage = snackMessageState.value,
                onClearSnackMessage = { savedStateHandle.set<String?>("snack_message", null) },
                onNavigateToSelection = { navController.navigate(FileUploadState.WAITING_FOR_UPLOAD.route) },
                onNavigateToResume = { navController.navigate("pending") },
                onNavigateToMaintenance = { navController.navigate("maintenance") },
                onNavigateToUploadedMaintenance = { navController.navigate(FileUploadState.UPLOAD_DONE.route) },
            )
        }
        composable(FileUploadState.WAITING_FOR_UPLOAD.route) {
            TargetFileSelectionRoute(
                title = "アップロードする写真を選択",
                onBack = { message -> navController.popBackStackWithSnackMessage(message) },
                navController = navController,
            )
        }
        composable("pending") {
            UploadTriggeredSelectionRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(FileUploadState.UPLOAD_EXCLUDED.route) {
            ExcludedFileSelectionRoute(
                title = "除外した写真",
                onBack = { message -> navController.popBackStackWithSnackMessage(message) },
                navController = navController,
            )
        }
        composable(FileUploadState.UPLOAD_DONE.route) {
            DoneFileSelectionRoute(
                title = "アップロード済みの写真",
                onBack = { message -> navController.popBackStackWithSnackMessage(message) },
            )
        }
        composable("maintenance") {
            MaintenanceScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCrashReport = { navController.navigate("crash_report_viewer") },
                onNavigateToDbMaintenance = { navController.navigate("db_maintenance") },
            )
        }
        composable("crash_report_viewer") {
            CrashReportViewerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { fileName ->
                    navController.navigate("crash_detail/$fileName")
                },
            )
        }
        composable(
            route = "crash_detail/{fileName}",
            arguments = listOf(navArgument("fileName") { type = androidx.navigation.NavType.StringType }),
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            CrashDetailScreen(
                fileName = fileName,
                onBack = { navController.popBackStack() },
            )
        }
        composable("db_maintenance") {
            DbMaintenanceScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
