package com.kasakaid.omoidememory.ui

import arrow.core.fold

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
        // mapOf だと、true/false であと勝ちでキーができるので注意! Pair にするべき
        return listOf(
            isPermissionGranted to "権限",
            isGoogleSignIn to "アカウント設定",
            isWifiValid to "Wi-Fi",
        ).filterNot {
            it.first
        }.map {
            it.second
        }.let { errors ->
            if (errors.isEmpty()) null else "${errors.joinToString("・")}が完了していません"
        }
    }
}
