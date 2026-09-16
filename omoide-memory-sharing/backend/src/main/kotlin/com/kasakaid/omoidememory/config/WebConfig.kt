package com.kasakaid.omoidememory.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebConfig : WebFluxConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // CORS設定を適用するリソース
        registry
            .addMapping("/**")
            .allowedOriginPatterns(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://*.local:5173",
                "http://ganymede.local:5173",
                "http://192.168.*.*:5173",
            ).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // アクセスを許可するHTTPヘッダ
            .allowedHeaders("*") // Javascriptからの参照を許可するヘッダ
            .exposedHeaders("Authorization") // クッキーなどの認証情報の送信を許可するか
            .allowCredentials(true) // プリフライトリクエストの結果を保持する時間
            .maxAge(3600)
    }
}
