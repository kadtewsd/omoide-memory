package com.kasakaid.omoidememory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kasakaid.omoidememory.ui.fileselection.DoneFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.ExcludedFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.FileUploadState
import com.kasakaid.omoidememory.ui.fileselection.PendingFileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.TargetFileSelectionRoute
import com.kasakaid.omoidememory.ui.maintenance.CrashDetailScreen
import com.kasakaid.omoidememory.ui.maintenance.CrashReportViewerScreen
import com.kasakaid.omoidememory.ui.maintenance.DbMaintenanceScreen
import com.kasakaid.omoidememory.ui.maintenance.MaintenanceScreen

@Composable
fun AppRouter() {
    val navController = rememberNavController()

    // ここは「どの画面を表示するか」の分岐ロジックだけに専念！
    NavHost(
        navController = navController,
        // main が画面の最初になる、と言う設定
        startDestination = "main",
    ) {
        composable("main") { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val skippedIdsState = savedStateHandle.getStateFlow<List<Long>?>("skipped_ids", null).collectAsState()

            MainScreen(
                skippedIds = skippedIdsState.value,
                onClearSkippedIds = { savedStateHandle.set<List<Long>?>("skipped_ids", null) },
                onNavigateToSelection = { navController.navigate(FileUploadState.WAITING_FOR_UPLOAD.route) },
                onNavigateToMaintenance = { navController.navigate("maintenance") },
                onNavigateToUploadedMaintenance = { navController.navigate(FileUploadState.UPLOAD_DONE.route) },
            )
        }
        composable(FileUploadState.WAITING_FOR_UPLOAD.route) {
            TargetFileSelectionRoute(
                title = "アップロードする写真を選択",
                onBack = { navController.popBackStack() },
                navController = navController,
            )
        }
        composable(FileUploadState.UPLOAD_TRIGGERED.route) {
            PendingFileSelectionRoute(
                title = "アップロード選択済",
                onBack = { navController.popBackStack() },
                navController = navController,
            )
        }
        composable(FileUploadState.UPLOAD_EXCLUDED.route) {
            ExcludedFileSelectionRoute(
                title = "除外した写真",
                onBack = { navController.popBackStack() },
                navController = navController,
            )
        }
        composable(FileUploadState.UPLOAD_DONE.route) {
            DoneFileSelectionRoute(
                title = "アップロード済みの写真",
                onBack = { skippedIds ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("skipped_ids", skippedIds)
                    navController.popBackStack()
                },
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
