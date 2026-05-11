package org.happyzion.api.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path

class UploadRuntimeConfigContractTest {

    @Test
    fun `application yml should define upload multipart and base url defaults`() {
        val content = readMainResource("application.yml")
        val localContent = readMainResource("application-local.yml")

        assertThat(content).contains("spring:")
        assertThat(content).contains("servlet:")
        assertThat(content).contains("multipart:")
        assertThat(content).contains("enabled: true")
        assertThat(content).contains("max-file-size: \${HAPPYZION_MULTIPART_MAX_FILE_SIZE:200MB}")
        assertThat(content).contains("max-request-size: \${HAPPYZION_MULTIPART_MAX_REQUEST_SIZE:220MB}")
        assertThat(content).contains("file-size-threshold: 2MB")
        assertThat(content).contains("happyzion.uploads.root-path: \${HAPPYZION_UPLOAD_ROOT:/opt/happyzion/uploads}")
        assertThat(content).contains(
            "happyzion.uploads.public-base-url: \${HAPPYZION_UPLOAD_PUBLIC_BASE_URL:https://api.happyzion.org/upload}",
        )
        assertThat(content).contains("https://happyzion.org")
        assertThat(content).contains("https://www.happyzion.org")
        assertThat(content).doesNotContain("http://localhost:3000")
        assertThat(content).doesNotContain("http://127.0.0.1:3000")
        assertThat(localContent).contains("http://localhost:3000")
        assertThat(localContent).contains("http://127.0.0.1:3000")
        assertThat(localContent).contains("root-path: \${HAPPYZION_UPLOAD_ROOT:\${user.dir}/.local/uploads}")
        assertThat(localContent).contains("public-base-url: \${HAPPYZION_UPLOAD_PUBLIC_BASE_URL:http://localhost:8080/upload}")
    }

    @Test
    fun `upload properties class should exist and be enabled by the application`() {
        assertThat(Path.of("src/main/resources/application-prod.yml")).exists()

        val uploadPropertiesClass = loadUploadPropertiesClass()
        val annotation = uploadPropertiesClass.getAnnotation(ConfigurationProperties::class.java)

        assertThat(annotation).isNotNull
        assertThat(annotation.prefix).isEqualTo("happyzion.uploads")

        val apiApplication = Files.readString(Path.of("src/main/kotlin/org/happyzion/api/ApiApplication.kt"))
        assertThat(apiApplication).contains(uploadPropertiesClass.simpleName)
        assertThat(apiApplication).contains("@EnableConfigurationProperties(")
    }

    @Test
    fun `web config should be upload only cors and production origins should be represented in defaults`() {
        val webConfig = readMainSource("WebConfig.kt")
        val corsProperties = readMainSource("CorsProperties.kt")

        assertThat(webConfig).doesNotContain("addMapping(\"/api/**\")")
        assertThat(webConfig).doesNotContain("allowedHeaders(\"*\")")
        assertThat(webConfig).contains("addMapping(\"/api/v1/admin/uploads\")")
        assertThat(webConfig).contains("allowedMethods(\"POST\", \"OPTIONS\")")
        assertThat(webConfig).contains("allowedHeaders(\"Content-Type\", \"X-Upload-Token\")")
        assertThat(webConfig).contains("allowCredentials(false)")
        assertThat(webConfig).contains("maxAge(600)")

        assertThat(corsProperties).contains("https://happyzion.org")
        assertThat(corsProperties).contains("https://www.happyzion.org")
    }

    @Test
    fun `local upload resource config should serve uploaded files only for local profile`() {
        val localUploadResourceConfig = readMainSource("LocalUploadResourceConfig.kt")

        assertThat(localUploadResourceConfig).contains("@Profile(\"local\")")
        assertThat(localUploadResourceConfig).contains("Path.of(uploadProperties.rootPath)")
        assertThat(localUploadResourceConfig).contains("addResourceHandler(\"/upload/**\")")
        assertThat(localUploadResourceConfig).contains("addResourceLocations(rootUri)")
    }

    @Test
    fun `global exception handler should not expose internal exception messages for 500 responses`() {
        val globalExceptionHandler = Files.readString(
            Path.of("src/main/kotlin/org/happyzion/api/common/error/GlobalExceptionHandler.kt"),
        )

        assertThat(globalExceptionHandler).contains("@ExceptionHandler(NoResourceFoundException::class)")
        assertThat(globalExceptionHandler).contains("logger.error(\"Unhandled API exception\", ex)")
        assertThat(globalExceptionHandler).contains("message = \"서버 오류가 발생했습니다.\"")
        assertThat(globalExceptionHandler).doesNotContain("message = ex.message ?: \"서버 오류가 발생했습니다.\"")
    }

    private fun loadUploadPropertiesClass(): Class<*> {
        val candidates = listOf(
            "org.happyzion.api.common.config.UploadProperties",
            "org.happyzion.api.common.config.HappyZionUploadsProperties",
        )

        return candidates.firstNotNullOfOrNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        } ?: throw AssertionError(
            "Expected an upload properties class named UploadProperties or HappyZionUploadsProperties",
        )
    }

    private fun readMainResource(fileName: String): String = Files.readString(Path.of("src/main/resources", fileName))

    private fun readMainSource(fileName: String): String =
        Files.readString(Path.of("src/main/kotlin/org/happyzion/api/common/config", fileName))
}
