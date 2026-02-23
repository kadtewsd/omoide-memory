package com.kasakaid.omoidememory.downloader.service

sealed interface FileIOFinish {
    object Success : FileIOFinish

    class Skip(
        val reason: String,
    ) : FileIOFinish
}
