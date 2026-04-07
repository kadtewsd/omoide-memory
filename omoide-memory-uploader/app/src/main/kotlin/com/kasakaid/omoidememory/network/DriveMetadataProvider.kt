package com.kasakaid.omoidememory.network

import android.os.Build
import com.google.api.client.util.DateTime
import com.kasakaid.omoidememory.data.OmoideMemory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * なぜこれで直るのか？
 * 1. MissingBinding の解消: Hilt は GoogleDriveService で DriveMetadataProvider（interface）が要求されたとき、この Module を見て「あぁ、SaPermission... を渡せばいいんだな」と理解します。
 * 2. @Binds の文法: @Binds は「引数に実体、戻り値にインターフェース」を書くルールです。あなたの前のコードでは createMetadata メソッドに付けていたため、Hilt が混乱していました。
 * 3. インターフェースの純粋化: interface はあくまで振る舞いの定義に留め、DI の設定（Module）を切り離すことで、コードの依存関係がスッキリします。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DriveMetadataModule {
    @Binds
    @Singleton
    abstract fun bindDriveMetadataProvider(impl: SaPermissionDriveMetadataProvider): DriveMetadataProvider
}

interface DriveMetadataProvider {
    fun createMetadata(omoide: OmoideMemory): com.google.api.services.drive.model.File
}

object DefaultDriveMetadataProvider : DriveMetadataProvider {
    override fun createMetadata(omoide: OmoideMemory): com.google.api.services.drive.model.File =
        com.google.api.services.drive.model.File().apply {
            // 1. 基本情報
            name = omoide.name
            mimeType = omoide.mimeType

            // 3. 撮影日時をセット (Drive上での「作成日」として扱われる)
            createdTime = omoide.dateTaken?.let { DateTime(it) }

            // 4. 説明文に端末情報を入れる
            description = "Uploaded by OmoideMemory App\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Original Path: ${omoide.filePath}"

            // 5. アプリ専用の隠しプロパティ
            appProperties =
                mapOf(
                    "local_id" to omoide.id.toString(),
                    "origin_device_id" to Build.ID,
                )
        }
}

class SaPermissionDriveMetadataProvider
    @Inject
    constructor() : DriveMetadataProvider {
        override fun createMetadata(omoide: OmoideMemory): com.google.api.services.drive.model.File =
            DefaultDriveMetadataProvider.createMetadata(omoide).apply {
                // 2. 保存先（ビルド時に指定したフォルダID）
                parents = listOf(com.kasakaid.omoidememory.BuildConfig.OMOIDE_FOLDER_ID)
            }
    }
