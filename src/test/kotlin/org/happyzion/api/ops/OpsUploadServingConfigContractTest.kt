package org.happyzion.api.ops

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OpsUploadServingConfigContractTest {

    @Test
    fun `production compose should expose upload settings and bind mount to app service`() {
        val appService = serviceBlock(readDeployFile("docker-compose.prod.yml"), "app")

        assertThat(appService).containsPattern(uploadEnvPattern("HAPPYZION_UPLOAD_ROOT"))
        assertThat(appService).containsPattern(uploadEnvPattern("HAPPYZION_UPLOAD_PUBLIC_BASE_URL"))
        assertThat(appService).containsPattern(uploadEnvPattern("HAPPYZION_MULTIPART_MAX_FILE_SIZE"))
        assertThat(appService).containsPattern(uploadEnvPattern("HAPPYZION_MULTIPART_MAX_REQUEST_SIZE"))
        assertThat(appService).containsPattern("""(?m)^\s*-\s*/opt/happyzion/uploads:/opt/happyzion/uploads\s*$""")
        assertThat(appService).containsPattern("""(?m)^\s*user:\s*["']1000:1000["']\s*$""")
    }

    @Test
    fun `production env example should document upload serving settings`() {
        val content = Files.readString(Path.of(".env.production.example"))

        assertThat(content).contains("HAPPYZION_UPLOAD_ROOT=/opt/happyzion/uploads")
        assertThat(content).contains("HAPPYZION_UPLOAD_PUBLIC_BASE_URL=https://api.happyzion.com/upload")
        assertThat(content).contains("HAPPYZION_MULTIPART_MAX_FILE_SIZE=200MB")
        assertThat(content).contains("HAPPYZION_MULTIPART_MAX_REQUEST_SIZE=220MB")
    }

    @Test
    fun `nginx http context file should only define shared upload rate limit and allowlist variables`() {
        val content = readNginxFile("happyzion-upload-http-context.conf")

        assertThat(content).doesNotContainPattern("""(?m)^\s*server\s*\{""")
        assertThat(content).doesNotContainPattern("""(?m)^\s*location\s+/upload/?\s*\{""")
        assertThat(content).containsPattern("""(?m)^\s*limit_req_zone\s+""")
        assertThat(content).containsPattern(
            """(?m)^\s*map\s+\${'$'}http_referer\s+\$[A-Za-z0-9_]*upload[A-Za-z0-9_]*\s*\{""",
        )
        assertThat(content).containsPattern(
            """(?m)^\s*map\s+\${'$'}http_origin\s+\$[A-Za-z0-9_]*upload[A-Za-z0-9_]*\s*\{""",
        )
    }

    @Test
    fun `nginx pre ssl file should be an http only server config`() {
        val content = readNginxFile("api.happyzion.com.pre-ssl.conf")

        assertThat(content).containsPattern("""(?m)^\s*server\s*\{""")
        assertThat(content).containsPattern("""(?m)^\s*listen\s+80\s*;""")
        assertThat(content).contains("proxy_pass http://127.0.0.1:8080;")
        assertThat(content).doesNotContain("ssl_certificate")
        assertThat(content).doesNotContainPattern("""(?m)^\s*limit_req_zone\s+""")
    }

    @Test
    fun `nginx server context should serve upload assets with limits and request guards`() {
        val content = readNginxFile("api.happyzion.com.conf")
        val uploadLocation = locationBlock(content, "/upload/")

        assertThat(content).containsPattern("""(?m)^\s*client_max_body_size\s+220m\s*;""")
        assertThat(content).containsPattern("""(?m)^\s*include\s+(?:/etc/nginx/)?mime\.types\s*;""")
        assertThat(content).containsPattern("""(?m)^\s*default_type\s+application/octet-stream\s*;""")
        assertThat(uploadLocation).containsPattern("""(?m)^\s*alias\s+/opt/happyzion/uploads/\s*;""")
        assertThat(uploadLocation).containsPattern("""(?m)^\s*limit_req\s+""")
        assertThat(uploadLocation).containsPattern("""(?m)^\s*try_files\s+""")
        assertThat(uploadLocation).containsPattern("""(?m)^\s*if\s+\(\s*\$[A-Za-z0-9_]*upload[A-Za-z0-9_]*\s*=\s*0\s*\)\s*\{""")
        assertThat(uploadLocation).contains("\$http_referer")
        assertThat(uploadLocation).contains("\$http_origin")
    }

    @Test
    fun `upload location should live only in nginx server context file`() {
        assertThat(readNginxFile("happyzion-upload-http-context.conf"))
            .doesNotContainPattern("""(?m)^\s*location\s+/upload/?\s*\{""")
        assertThat(readNginxFile("api.happyzion.com.conf"))
            .containsPattern("""(?m)^\s*location\s+/upload/?\s*\{""")
    }

    private fun readDeployFile(fileName: String): String = Files.readString(Path.of("deploy", fileName))

    private fun readNginxFile(fileName: String): String = Files.readString(Path.of("deploy/nginx", fileName))

    private fun serviceBlock(content: String, serviceName: String): String {
        val pattern = Regex("""(?ms)^  $serviceName:\R(.*?)(?=^\S|\z)""")
        return pattern.find(content)?.value ?: throw AssertionError("Expected compose service '$serviceName' to exist")
    }

    private fun locationBlock(content: String, path: String): String {
        val pattern = Regex("""(?ms)^\s*location\s+${Regex.escape(path)}\s*\{(.*?)(?=^\s*}\s*$)""")
        return pattern.find(content)?.value ?: throw AssertionError("Expected nginx location '$path' to exist")
    }

    private fun uploadEnvPattern(name: String): String =
        """(?m)^\s*(?:-\s*)?$name\s*[:=]\s*\$\{$name(?::[^}]*)?}\s*$"""
}
