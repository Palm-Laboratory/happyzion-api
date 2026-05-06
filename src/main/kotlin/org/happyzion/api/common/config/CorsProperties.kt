package org.happyzion.api.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf(
        "https://happyzion.org",
        "https://www.happyzion.org",
    ),
)
