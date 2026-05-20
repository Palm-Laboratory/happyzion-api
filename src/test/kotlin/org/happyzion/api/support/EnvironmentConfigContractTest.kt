package org.happyzion.api.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EnvironmentConfigContractTest {

    @Test
    fun `local env example should document current required variables`() {
        val envExample = Path.of(".env.example")
        val content = Files.readString(envExample)

        assertThat(content).contains("SPRING_PROFILES_ACTIVE=local")
        assertThat(content).contains("DB_URL=")
        assertThat(content).contains("DB_USERNAME=")
        assertThat(content).contains("DB_PASSWORD=")
        assertThat(content).contains("YOUTUBE_API_KEY=")
        assertThat(content).contains("YOUTUBE_CHANNEL_ID=")
        assertThat(content).contains("ADMIN_JWT_SECRET=")
        assertThat(content).doesNotContain("ADMIN_SYNC_KEY=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_USERNAME=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_PASSWORD=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_DISPLAY_NAME=")
        assertThat(content).contains("CORS_ALLOWED_ORIGINS=")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_KEYS=")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID=")
        assertThat(content).contains("HAPPYZION_PII_HASH_KEY=")
    }

    @Test
    fun `production env example should document current required variables`() {
        val envProductionExample = Path.of(".env.production.example")
        val content = Files.readString(envProductionExample)

        assertThat(content).contains("SPRING_PROFILES_ACTIVE=prod")
        assertThat(content).contains("APP_IMAGE=")
        assertThat(content).contains("POSTGRES_DB=")
        assertThat(content).contains("POSTGRES_USER=")
        assertThat(content).contains("POSTGRES_PASSWORD=")
        assertThat(content).contains("DB_URL=jdbc:postgresql://db:5432/happyzion")
        assertThat(content).contains("DB_USERNAME=")
        assertThat(content).contains("DB_PASSWORD=")
        assertThat(content).contains("YOUTUBE_API_KEY=")
        assertThat(content).contains("YOUTUBE_CHANNEL_ID=")
        assertThat(content).contains("ADMIN_JWT_SECRET=")
        assertThat(content).doesNotContain("ADMIN_SYNC_KEY=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_USERNAME=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_PASSWORD=")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_DISPLAY_NAME=")
        assertThat(content).contains("CORS_ALLOWED_ORIGINS=")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_KEYS=")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID=")
        assertThat(content).contains("HAPPYZION_PII_HASH_KEY=")
    }

    @Test
    fun `production compose should forward current runtime envs to app`() {
        val prodCompose = Path.of("deploy/docker-compose.prod.yml")
        val content = Files.readString(prodCompose)

        assertThat(content).contains("DB_URL: \${DB_URL}")
        assertThat(content).contains("DB_USERNAME: \${DB_USERNAME}")
        assertThat(content).contains("DB_PASSWORD: \${DB_PASSWORD}")
        assertThat(content).contains("YOUTUBE_API_KEY: \${YOUTUBE_API_KEY}")
        assertThat(content).contains("YOUTUBE_CHANNEL_ID: \${YOUTUBE_CHANNEL_ID}")
        assertThat(content).contains("ADMIN_JWT_SECRET: \${ADMIN_JWT_SECRET}")
        assertThat(content).doesNotContain("ADMIN_SYNC_KEY: \${ADMIN_SYNC_KEY}")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_USERNAME: \${ADMIN_BOOTSTRAP_USERNAME}")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_PASSWORD: \${ADMIN_BOOTSTRAP_PASSWORD}")
        assertThat(content).doesNotContain("ADMIN_BOOTSTRAP_DISPLAY_NAME: \${ADMIN_BOOTSTRAP_DISPLAY_NAME}")
        assertThat(content).contains("CORS_ALLOWED_ORIGINS: \${CORS_ALLOWED_ORIGINS}")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_KEYS: \${HAPPYZION_PII_ENCRYPTION_KEYS}")
        assertThat(content).contains("HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID: \${HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID}")
        assertThat(content).contains("HAPPYZION_PII_HASH_KEY: \${HAPPYZION_PII_HASH_KEY}")
    }

    @Test
    fun `production profile should not expose generated api docs`() {
        val prodConfig = Path.of("src/main/resources/application-prod.yml")
        val content = Files.readString(prodConfig)

        assertThat(content).contains("springdoc:")
        assertThat(content).contains("api-docs:")
        assertThat(content).contains("swagger-ui:")
        assertThat(content).contains("enabled: false")
    }

    @Test
    fun `admin bootstrap service should not exist anymore`() {
        assertThat(Path.of("src/main/kotlin/org/happyzion/api/adminaccount/application/AdminAccountBootstrapService.kt"))
            .doesNotExist()
    }
}
