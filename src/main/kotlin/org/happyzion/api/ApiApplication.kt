package org.happyzion.api

import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.config.CorsProperties
import org.happyzion.api.common.config.UploadProperties
import org.happyzion.api.common.config.YouTubeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(
    value = [
        AdminProperties::class,
        CorsProperties::class,
        UploadProperties::class,
        YouTubeProperties::class,
    ],
)
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
