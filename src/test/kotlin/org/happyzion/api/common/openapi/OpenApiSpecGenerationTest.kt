package org.happyzion.api.common.openapi

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.menu.application.PublicMenuService
import org.happyzion.api.menu.interfaces.api.PublicMenuController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(classes = [OpenApiSpecTestApplication::class])
@AutoConfigureMockMvc
class OpenApiSpecGenerationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var publicMenuService: PublicMenuService

    @Test
    fun `generate public menu OpenAPI yaml`() {
        val yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
            .andReturn()
            .response
            .contentAsString

        assertThat(yaml).contains("/api/v1/public/menu:", "/api/v1/public/menu/resolve:")

        val outputPath = Path.of("build/openapi/openapi.yaml")
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, yaml)
    }
}

@SpringBootApplication(
    exclude = [
        DataSourceAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
    ],
)
@Import(PublicMenuController::class)
private class OpenApiSpecTestApplication
