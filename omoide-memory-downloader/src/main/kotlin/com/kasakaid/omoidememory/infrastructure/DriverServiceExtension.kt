package com.kasakaid.omoidememory.infrastructure

/**
 * 指定された Drive API クエリで "device_token" ファイルを検索し、テキスト内容を返します。
 * サブクラスは accessInfo からクエリを組み立ててこのメソッドを呼び出します。
 *
 * @param query Drive API の files.list クエリ文字列
 * @return デバイストークン文字列。ファイルが存在しない・取得失敗の場合は null
 */
fun com.google.api.services.drive.Drive.fetchDeviceToken(query: String): String? {
    return runCatching {
        val result =
            this
                .files()
                .list()
                .setQ(query)
                .setFields("files(id)")
                .execute()
        val fileId = result.files?.firstOrNull()?.id ?: return@runCatching null
        val outputStream = java.io.ByteArrayOutputStream()
        files().get(fileId).executeMediaAndDownloadTo(outputStream)
        outputStream.toString(Charsets.UTF_8.name()).trim()
    }.getOrNull()
}
