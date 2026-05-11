package org.happyzion.api.site.interfaces.api

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.board.domain.PostAssetKind
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import java.nio.file.Files
import java.nio.file.Path

class SiteSettingDirectUploadContractTest {

    @Test
    fun `admin main video upload endpoint authenticates with upload token instead of admin key`() {
        val uploadMethod = SiteSettingController::class.java.declaredMethods
            .firstOrNull { method ->
                method.getAnnotation(PostMapping::class.java)
                    ?.paths()
                    ?.contains(ADMIN_MAIN_VIDEO_PATH) == true
            }

        assertThat(uploadMethod)
            .describedAs("Expected main-video upload endpoint to remain available for direct browser POSTs.")
            .isNotNull

        val headerNames = uploadMethod!!.parameters
            .flatMap { parameter -> parameter.annotations.filterIsInstance<RequestHeader>() }
            .map { requestHeader -> requestHeader.name.ifBlank { requestHeader.value } }

        assertThat(headerNames)
            .describedAs("Direct browser uploads must authenticate with a one-time upload token.")
            .contains("X-Upload-Token")
        assertThat(headerNames)
            .describedAs("Main-video upload POST must not accept the server admin sync key from browsers.")
            .doesNotContain("X-Admin-Key")
    }

    @Test
    fun `upload tokens support main video asset kind`() {
        assertThat(PostAssetKind.entries.map { it.name })
            .describedAs("Upload-token issuance needs a dedicated MAIN_VIDEO kind for the main-video endpoint.")
            .contains("MAIN_VIDEO")
    }

    @Test
    fun `cors allows browser direct upload to admin main video endpoint`() {
        val webConfig = Files.readString(Path.of("src/main/kotlin/org/happyzion/api/common/config/WebConfig.kt"))

        assertThat(webConfig)
            .describedAs("Browser direct uploads must be CORS-enabled for the backend main-video endpoint.")
            .contains("""addMapping("$ADMIN_MAIN_VIDEO_PATH")""")
        assertThat(webConfig)
            .describedAs("Main-video direct upload CORS must allow upload token headers.")
            .contains("X-Upload-Token")
    }

    private companion object {
        const val ADMIN_MAIN_VIDEO_PATH = "/api/v1/admin/site/main-video"
    }
}

private fun PostMapping.paths(): List<String> =
    value.asList() + path.asList()
