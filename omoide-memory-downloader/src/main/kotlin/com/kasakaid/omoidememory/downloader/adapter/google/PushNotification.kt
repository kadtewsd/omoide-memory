package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.Either
import arrow.core.Either.Companion.catch
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO

class PushNotification(
    iconPath: Path,
    saPath: String,
    val deviceToken: DeviceToken,
) {
    /**
     * 指定されたパスの画像ファイルを読み込み、
     * 正方形に中央クロップ＆アイコンサイズへリサイズして Base64 文字列（JPEG）に変換します。
     * FCM ペイロード全体の上限が 4096 バイトのため、Base64 が大きすぎる場合は 48x48 で再圧縮します。
     */
    val pushIcon: Either<PushIconGenerateError, PushIcon> =
        catch {
            if (!Files.exists(iconPath) || !Files.isRegularFile(iconPath)) {
                throw FileNotFoundException("PUSH 通知用アイコンファイルが存在しません: $iconPath")
            }
            val src =
                Files.newInputStream(iconPath).use { ImageIO.read(it) }
                    ?: throw IOException("画像ファイルの読み込みに失敗しました: $iconPath")
            val (_, b64) = src.encodeImageToBase64(targetSize = TARGET_ICON_SIZE)
            if (b64.length > 2800) {
                src.encodeImageToBase64(targetSize = FALLBACK_ICON_SIZE).second
            } else {
                b64
            }
        }.mapLeft { e ->
            PushIconGenerateError(e)
        }

    /**
     * 画像の中央を正方形にクロップし、指定された正方形サイズにリサイズした上で JPEG 形式の Base64 文字列へ変換します。
     *
     * 背景を白地で初期化し、バイリニア補間およびアンチエイリアスを適用して描画品質を担保します。
     *
     * @param targetSize リサイズ後のアイコンの一辺のピクセル数（幅および高さ）
     * @return リサイズ後の JPEG バイト配列と Base64 エンコード文字列の [Pair]
     */
    private fun BufferedImage.encodeImageToBase64(targetSize: Int): Pair<ByteArray, String> {
        val minDim = minOf(width, height)
        val x = (width - minDim) / 2
        val y = (height - minDim) / 2
        val cropped = getSubimage(x, y, minDim, minDim)

        val resized = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, targetSize, targetSize)
            g.drawImage(cropped, 0, 0, targetSize, targetSize, null)
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(resized, "jpg", baos)
        val bytes = baos.toByteArray()
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return bytes to base64
    }

    val accessToken: Either<AccessTokenRetrieveError, AccessToken> =
        catch {
            val credentials =
                com.google.auth.oauth2.ServiceAccountCredentials
                    .fromStream(FileInputStream(saPath))
                    .createScoped(listOf(FCM_SCOPE))
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        }.mapLeft { e ->
            AccessTokenRetrieveError(e)
        }

    companion object {
        private const val TARGET_ICON_SIZE = 64
        private const val FALLBACK_ICON_SIZE = 48
        private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}

class PushIconGenerateError(
    val e: Throwable,
)

typealias DeviceToken = String
typealias PushIcon = String
typealias AccessToken = String

class AccessTokenRetrieveError(
    val e: Throwable,
)
