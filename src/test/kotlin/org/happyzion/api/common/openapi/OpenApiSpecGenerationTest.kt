package org.happyzion.api.common.openapi

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.adminaccount.application.AdminAccountAuthService
import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.adminaccount.application.AdminAccountManagementService
import org.happyzion.api.adminaccount.interfaces.api.AdminAccountController
import org.happyzion.api.adminaccount.interfaces.api.AdminAuthController
import org.happyzion.api.board.application.BoardAdminService
import org.happyzion.api.board.application.PublicBoardService
import org.happyzion.api.board.application.UploadAssetService
import org.happyzion.api.board.application.UploadTokenService
import org.happyzion.api.board.interfaces.api.BoardAdminController
import org.happyzion.api.board.interfaces.api.PublicBoardController
import org.happyzion.api.board.interfaces.api.UploadAdminController
import org.happyzion.api.common.config.OpenApiConfig
import org.happyzion.api.menu.application.MenuManagementService
import org.happyzion.api.menu.application.PublicMenuService
import org.happyzion.api.menu.interfaces.api.MenuAdminController
import org.happyzion.api.menu.interfaces.api.PublicMenuController
import org.happyzion.api.mission.application.MissionHistoryService
import org.happyzion.api.mission.interfaces.api.MissionHistoryAdminController
import org.happyzion.api.mission.interfaces.api.PublicMissionHistoryController
import org.happyzion.api.site.application.SiteSettingService
import org.happyzion.api.site.interfaces.api.SiteSettingController
import org.happyzion.api.video.application.VideoService
import org.happyzion.api.video.interfaces.api.PublicVideoController
import org.happyzion.api.video.interfaces.api.VideoAdminController
import org.happyzion.api.youtube.application.YouTubeSyncService
import org.happyzion.api.youtube.interfaces.api.YouTubeAdminController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
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

    // ── menu ──────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var publicMenuService: PublicMenuService
    @MockitoBean private lateinit var menuManagementService: MenuManagementService

    // ── board ──────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var publicBoardService: PublicBoardService
    @MockitoBean private lateinit var boardAdminService: BoardAdminService
    @MockitoBean private lateinit var uploadTokenService: UploadTokenService
    @MockitoBean private lateinit var uploadAssetService: UploadAssetService

    // ── video ──────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var videoService: VideoService

    // ── admin account ──────────────────────────────────────────────────────────
    @MockitoBean private lateinit var adminAccountGuard: AdminAccountGuard
    @MockitoBean private lateinit var adminAccountManagementService: AdminAccountManagementService
    @MockitoBean private lateinit var adminAccountAuthService: AdminAccountAuthService

    // ── youtube ────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var youTubeSyncService: YouTubeSyncService

    // ── mission ────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var missionHistoryService: MissionHistoryService

    // ── site ───────────────────────────────────────────────────────────────────
    @MockitoBean private lateinit var siteSettingService: SiteSettingService

    @Test
    fun `generate full OpenAPI yaml`() {
        val yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
            .andReturn()
            .response
            .contentAsString

        assertThat(yaml)
            .contains("/api/v1/public/menu:")
            .contains("/api/v1/public/menu/resolve:")
            .contains("/api/v1/public/boards/")
            .contains("/api/v1/admin/boards/")
            .contains("/api/v1/admin/menu:")
            .contains("/api/v1/admin/videos:")
            .contains("/api/v1/admin/accounts:")
            .contains("/api/v1/admin/auth/login:")
            .contains("/api/v1/admin/youtube/playlists:")
            .contains("/api/v1/public/mission-history:")
            .contains("/api/v1/admin/mission-history:")
            .contains("/api/v1/public/site/main-video:")
            .contains("/api/v1/admin/uploads/token:")
            .doesNotContain("/api/v1/admin/members:")

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
@Import(
    OpenApiConfig::class,
    PublicMenuController::class,
    MenuAdminController::class,
    PublicBoardController::class,
    BoardAdminController::class,
    UploadAdminController::class,
    PublicVideoController::class,
    VideoAdminController::class,
    AdminAccountController::class,
    AdminAuthController::class,
    YouTubeAdminController::class,
    PublicMissionHistoryController::class,
    MissionHistoryAdminController::class,
    SiteSettingController::class,
)
private class OpenApiSpecTestApplication
