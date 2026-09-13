package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.downloader.adapter.google.PushNotification
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PostProcessNotificationTest {
    @Test
    fun `Desktopのpush_icon_pngが正しく読み込み・リサイズされFCMペイロード上限内に収まること`() {
        val path = Path.of(System.getProperty("user.home"), "Desktop", "push_icon.png")
        if (!Files.exists(path)) {
            println("push_icon.png does not exist at $path, skipping test")
            return
        }

        val pushNotification =
            PushNotification(
                iconPath = path,
                saPath = "dummy",
                deviceToken = "dummy",
            )
        val base64 = pushNotification.pushIcon.getOrNull()
        assertNotNull(base64)
        println("Generated Base64 length: ${base64!!.length}")
        // Base64 が FCM の 4KB ペイロード制限に十分収まること（3000文字以下）
        assertTrue(base64.length < 3000, "FCM payload icon Base64 must be under 3000 chars")
    }
}
