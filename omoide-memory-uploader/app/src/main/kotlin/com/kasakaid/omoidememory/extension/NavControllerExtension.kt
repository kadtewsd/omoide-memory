package com.kasakaid.omoidememory.extension

import androidx.navigation.NavController
import com.kasakaid.omoidememory.ui.fileselection.FileUploadState

/**
 * ファイルの取り扱いに応じて遷移する先を決定する
 */
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

/**
 * 前の画面（BackStack）にスナックバー表示用のメッセージを受け渡しながら、前の画面に戻ります。
 *
 * Navigation Component では、画面遷移時にデータを受け渡す手段として [SavedStateHandle] を使用します。
 * [NavController.previousBackStackEntry] を通じて戻り先の画面が保持する [SavedStateHandle] を取得し、
 * キー `"snack_message"` にメッセージ文字列を設定した上で [NavController.popBackStack] を呼び出します。
 * 戻り先の画面（例: `MainScreen`）では、この `"snack_message"` を監視してスナックバーを表示します。
 *
 * @param message 戻り先画面のスナックバーで表示したいメッセージ。null の場合はメッセージを渡さずに戻ります。
 */
fun NavController.popBackStackWithSnackMessage(message: String?) {
    if (message != null) {
        previousBackStackEntry?.savedStateHandle?.set("snack_message", message)
    }
    popBackStack()
}
