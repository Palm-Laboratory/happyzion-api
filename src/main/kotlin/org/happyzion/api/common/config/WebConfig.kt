package org.happyzion.api.common.config

import org.happyzion.api.common.security.AdminAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val corsProperties: CorsProperties,
    private val adminAuthInterceptor: AdminAuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api/v1/admin/**")
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOrigins = corsProperties.allowedOrigins
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        if (allowedOrigins.isEmpty()) {
            return
        }

        registry.addMapping("/api/v1/admin/uploads")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("POST", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Upload-Token")
            .allowCredentials(false)
            .maxAge(600)

        registry.addMapping("/api/v1/admin/site/main-video")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("POST", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Upload-Token")
            .allowCredentials(false)
            .maxAge(600)
    }
}
