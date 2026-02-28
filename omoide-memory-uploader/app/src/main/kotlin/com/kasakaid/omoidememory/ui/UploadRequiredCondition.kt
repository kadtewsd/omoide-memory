package com.kasakaid.omoidememory.ui

/**
 * アップロードに必要な条件をまとめたデータクラス。
 * [isPermissionGranted] ストレージなどの権限が許可されているか
 * [isGoogleSignIn] Google アカウントでサインインしているか
 * [isWifiValid] 設定された Wi-Fi に接続されているか
 */
data class UploadRequiredCondition(
    val isPermissionGranted: Boolean = false,
    val isGoogleSignIn: Boolean = false,
    val isWifiValid: Boolean = false,
) {
    /**
     * すべての条件を満たしているか
     */
    val canUpload: Boolean get() = isPermissionGranted && isGoogleSignIn && isWifiValid

    /**
     * 不足している条件に基づいてエラーメッセージを生成します。
     */
    fun getErrorMessage(): String? {
        if (canUpload) return null
        val errors = mutableListOf<String>()
        if (!isWifiValid) errors.add("Wi-Fi")
        if (!isPermissionGranted) errors.add("権限")
        if (!isGoogleSignIn) errors.add("アカウント設定")

        return if (errors.isEmpty()) null else "${errors.joinToString("・")}が完了していません"
    }
}
