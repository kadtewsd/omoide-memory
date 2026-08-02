package com.kasakaid.omoidememory.extension

import androidx.navigation.NavController
import com.kasakaid.omoidememory.ui.fileselection.FileUploadState

fun NavController.navigate(
    state: FileUploadState,
    currentRoute: String,
) {
    if (state.route != currentRoute) {
        navigate(state.route) {
            popUpTo(currentRoute) { inclusive = true }
        }
    }
}
