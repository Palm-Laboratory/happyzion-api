package org.happyzion.api.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf(
        "https://happyzion.com",
        "https://www.happyzion.com",
    ),
)
