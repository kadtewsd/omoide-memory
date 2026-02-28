package com.kasakaid.omoidememory.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmoideUploadPrefsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        // 第一引数の omoide_upload_prefs のファイルが /data/data/<package>/shared_prefs/omoide_upload_prefs でできる。ここに設定がはいる
        private val prefs: SharedPreferences = context.getSharedPreferences("omoide_upload_prefs", Context.MODE_PRIVATE)

        fun getAccountName(): String? = prefs.getString(OmoideUploadPrefs.ACCOUNT_NAME, null)

        fun setAccountName(name: String?) {
            prefs.edit { putString(OmoideUploadPrefs.ACCOUNT_NAME, name) }
        }

        /**
         * 自宅ないし安心なネットワークの SSID。公共の場でアップロードを発生させないようにする
         */
        fun getSecureWifiSsid(): String? = prefs.getString(OmoideUploadPrefs.SECURE_WIFI_SSID, null)

        fun saveSecureWifiSsid(ssid: String?) {
            prefs.edit { putString(OmoideUploadPrefs.SECURE_WIFI_SSID, ssid) }
        }

        fun getSecureWifiSsidFlow(): Flow<String?> =
            callbackFlow {
                trySend(getSecureWifiSsid())
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == OmoideUploadPrefs.SECURE_WIFI_SSID) {
                            trySend(getSecureWifiSsid())
                        }
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        /**
         * 自動アップロードが有効か無効か
         */
        fun isAutoUploadEnabled(): Boolean = prefs.getBoolean(OmoideUploadPrefs.AUTO_UPLOAD_ENABLED, false)

        fun setAutoUploadEnabled(enabled: Boolean) {
            prefs.edit { putBoolean(OmoideUploadPrefs.AUTO_UPLOAD_ENABLED, enabled) }
        }

        /**
         * 動画、写真をアップロードする際のベースとなる日付。この日付より前の日付は画面上にも現れないし、自動アップロードの対象にもならない
         */
        fun getUploadBaseLineInstant(): Flow<Instant?> =
            callbackFlow {
                // 1. 現在の値をまず送る
                val getCurrent = {
                    val millis = prefs.getLong(OmoideUploadPrefs.UPLOAD_BASELINE, -1L)
                    if (millis > 0) Instant.ofEpochMilli(millis) else null
                }
                trySend(getCurrent())

                // 2. Prefs の変更を監視するリスナーを登録
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                        if (key == OmoideUploadPrefs.UPLOAD_BASELINE) {
                            trySend(getCurrent()) // 値が変わったら再送！
                        }
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)

                // 3. Flow がキャンセルされたらリスナーを解除（メモリリーク防止）
                awaitClose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

        fun updateUploadBaseLineInstant(instant: Instant) {
            prefs.edit { putLong(OmoideUploadPrefs.UPLOAD_BASELINE, instant.toEpochMilli()).apply() }
        }
    }

private object OmoideUploadPrefs {
    const val ACCOUNT_NAME = "account_name"
    const val AUTO_UPLOAD_ENABLED = "auto_upload_enabled"
    const val SECURE_WIFI_SSID = "secure_wifi_ssid"
    const val UPLOAD_BASELINE = "upload_baseline_epoch_millis"
}
