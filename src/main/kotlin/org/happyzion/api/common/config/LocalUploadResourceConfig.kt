package org.happyzion.api.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
@Profile("local")
class LocalUploadResourceConfig(
    private val uploadProperties: UploadProperties,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val rootUri = Path.of(uploadProperties.rootPath)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString()
            .let { uri -> if (uri.endsWith("/")) uri else "$uri/" }

        registry.addResourceHandler("/upload/**")
            .addResourceLocations(rootUri)
            .setCachePeriod(0)
    }
}
