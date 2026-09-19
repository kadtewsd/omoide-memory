package com.kasakaid.omoidememory.service.query

import java.util.Base64

/**
 * バイト配列（[ByteArray]）を Base64 文字列にエンコードします。
 *
 * @return Base64 エンコード文字列
 */
fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

/**
 * バイト配列（[ByteArray]）を Data URI スキーム形式の Base64 文字列（`data:<mimeType>;base64,<encoded>`）に変換します。
 *
 * @param mimeType メディアの MIME タイプ（例: "image/jpeg", "image/png"）
 * @return Data URI 形式の Base64 文字列
 */
fun ByteArray.toDataUriBase64(mimeType: String): String = "data:$mimeType;base64,${toBase64()}"
