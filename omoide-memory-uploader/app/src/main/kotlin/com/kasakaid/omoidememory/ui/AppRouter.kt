package com.kasakaid.omoidememory.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
                onNavigateToDriveDelete = { navController.navigate("drive_delete") },
            )
        }
        composable("selection") {
            FileSelectionRoute(
                // navController.popBackStack で ジェスチャ対応もしている
                toMainScreen = { navController.popBackStack() },
            )
        }
        composable("drive_delete") {
            DriveFileDeleteRoute(
                toMainScreen = { navController.popBackStack() },
            )
        }
    }
}
