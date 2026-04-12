package com.kasakaid.omoidememory.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kasakaid.omoidememory.ui.fileselection.FileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.SelectionMode
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
        composable("main") {
            MainScreen(
                onNavigateToSelection = { navController.navigate("selection") },
                onNavigateToMaintenance = { navController.navigate("maintenance") },
                onNavigateToUploadedMaintenance = { navController.navigate("uploaded_maintenance") },
            )
        }
        composable("selection") {
            FileSelectionRoute(
                initialMode = SelectionMode.TARGET,
                // navController.popBackStack で ジェスチャ対応もしている
                toMainScreen = { navController.popBackStack() },
            )
        }
        composable("uploaded_maintenance") {
            FileSelectionRoute(
                initialMode = SelectionMode.DONE,
                toMainScreen = { navController.popBackStack() },
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
