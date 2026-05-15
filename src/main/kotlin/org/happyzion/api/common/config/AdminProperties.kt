package org.happyzion.api.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val syncKey: String = "",
    val actorSigningSecret: String = "",
)
