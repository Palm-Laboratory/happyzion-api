package org.happyzion.api.sms.infrastructure.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "happyzion.aligo")
data class AligoProperties(
    val baseUrl: String = "https://apis.aligo.in",
    val userId: String = "",
    val apiKey: String = "",
    val sender: String = "",
    val testmode: Boolean = true,
    val connectTimeoutMs: Long = 3000,
    val readTimeoutMs: Long = 10000,
)
