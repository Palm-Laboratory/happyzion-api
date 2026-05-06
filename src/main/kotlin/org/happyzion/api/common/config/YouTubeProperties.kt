package org.happyzion.api.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "youtube")
data class YouTubeProperties(
    val apiKey: String = "",
    val channelId: String = "",
)
