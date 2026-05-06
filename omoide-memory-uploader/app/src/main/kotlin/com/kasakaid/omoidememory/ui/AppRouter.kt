package com.kasakaid.omoidememory.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.data.isOverLimit
import com.kasakaid.omoidememory.ui.fileselection.DoneFilter
import com.kasakaid.omoidememory.ui.fileselection.DoneFilterRow
import com.kasakaid.omoidememory.ui.fileselection.FileSelectionRoute
import com.kasakaid.omoidememory.ui.fileselection.FileSelectionViewModel
import com.kasakaid.omoidememory.ui.fileselection.SelectionActionRow
import com.kasakaid.omoidememory.ui.fileselection.SelectionMode
import com.kasakaid.omoidememory.ui.fileselection.SelectionModeRow
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
            val viewModel: FileSelectionViewModel = hiltViewModel()
            val isUploading by viewModel.isUploading.collectAsState()
            val isDeleting by viewModel.isDeleting.collectAsState()

            FileSelectionRoute(
                viewModel = viewModel,
                title = "アップロードする写真を選択",
                selectionMode = SelectionMode.TARGET,
                subHeader = {
                    SelectionModeRow(
                        selectionMode = SelectionMode.TARGET,
                        onSelectionModeChanged = { mode ->
                            if (mode == SelectionMode.EXCLUDED) {
                                navController.navigate("excluded") {
                                    popUpTo("selection") { inclusive = true }
                                }
                            }
                        },
                        filterDone = true,
                    )
                },
                bottomBarAction = { selectedFiles ->
                    SelectionActionRow(
                        selectedFiles = selectedFiles,
                        showOverLimitError = true,
                    ) {
                        Button(
                            onClick = { viewModel.startManualUpload(selectedFiles.map { it.id }) },
                            modifier = Modifier.weight(1f),
                            enabled =
                                !isUploading && !isDeleting &&
                                    selectedFiles.isNotEmpty() && !selectedFiles.isOverLimit(),
                        ) {
                            Text("送信")
                        }
                        Button(
                            onClick = { viewModel.markAsRemoved(selectedFiles.map { it.id }) },
                            modifier = Modifier.weight(1f),
                            enabled = !isUploading && !isDeleting && selectedFiles.isNotEmpty(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                ),
                        ) {
                            Text("除外")
                        }
                    }
                },
                toMainScreen = { navController.popBackStack() },
            )
        }
        composable("excluded") {
            val viewModel: FileSelectionViewModel = hiltViewModel()
            val isUploading by viewModel.isUploading.collectAsState()
            val isDeleting by viewModel.isDeleting.collectAsState()

            FileSelectionRoute(
                viewModel = viewModel,
                title = "除外した写真",
                selectionMode = SelectionMode.EXCLUDED,
                subHeader = {
                    SelectionModeRow(
                        selectionMode = SelectionMode.EXCLUDED,
                        onSelectionModeChanged = { mode ->
                            if (mode == SelectionMode.TARGET) {
                                navController.navigate("selection") {
                                    popUpTo("excluded") { inclusive = true }
                                }
                            }
                        },
                        filterDone = true,
                    )
                },
                bottomBarAction = { selectedFiles ->
                    SelectionActionRow(
                        selectedFiles = selectedFiles,
                        showOverLimitError = false,
                    ) {
                        Button(
                            onClick = { viewModel.revive(selectedFiles.map { it.id }) },
                            modifier = Modifier.weight(1f),
                            enabled = !isUploading && !isDeleting && selectedFiles.isNotEmpty(),
                        ) {
                            Text("復活")
                        }
                    }
                },
                toMainScreen = { navController.popBackStack() },
            )
        }
        composable("uploaded_maintenance") {
            val viewModel: FileSelectionViewModel = hiltViewModel()
            val isUploading by viewModel.isUploading.collectAsState()
            val isDeleting by viewModel.isDeleting.collectAsState()
            val doneFilter by viewModel.doneFilter.collectAsState()

            FileSelectionRoute(
                viewModel = viewModel,
                title = "アップロード済みの写真",
                selectionMode = SelectionMode.DONE,
                subHeader = {
                    SelectionModeRow(
                        selectionMode = SelectionMode.DONE,
                        onSelectionModeChanged = {},
                        filterDone = false,
                    )
                    DoneFilterRow(
                        doneFilter = doneFilter,
                        onDoneFilterChanged = { viewModel.setDoneFilter(it) },
                    )
                },
                bottomBarAction = { selectedFiles ->
                    SelectionActionRow(
                        selectedFiles = selectedFiles,
                        showOverLimitError = false,
                    ) {
                        if (doneFilter != DoneFilter.DELETED) {
                            Button(
                                onClick = {
                                    viewModel.deleteFromDrive(
                                        selectedFiles.filter { it.state == UploadState.DONE }.map { it.id },
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled =
                                    !isUploading && !isDeleting &&
                                        selectedFiles.any { it.state == UploadState.DONE },
                            ) {
                                Text("ドライブから削除")
                            }
                        }
                    }
                },
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
