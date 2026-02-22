package com.kasakaid.omoidememory.domain

import java.nio.file.Path

/**
 * ローカルファイルを表すドメインモデル
 */
class LocalFile(
    val path: Path,
    val name: String,
)
