package org.happyzion.api.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "happyzion.uploads")
data class UploadProperties(
    val rootPath: String = "/opt/happyzion/uploads",
    val publicBaseUrl: String = "https://api.happyzion.com/upload",
)
