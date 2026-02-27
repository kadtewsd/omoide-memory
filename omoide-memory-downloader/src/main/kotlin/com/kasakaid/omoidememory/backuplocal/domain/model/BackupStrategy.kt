package com.kasakaid.omoidememory.backuplocal.domain.model

import java.nio.file.Path

/**
 * ファイルバックのためのパスを保持する
 */
class BackupStrategy(
    // 元となるファイルの絶対パス
    val sourceAbsolutePath: Path,
    localRoot: Path,
    externalRoot: Path,
) {
    /**
     * バックアップ先のファイルパス
     */
    val externalStorageAbsolutePath = externalRoot.resolve(localRoot.relativize(sourceAbsolutePath))
}
