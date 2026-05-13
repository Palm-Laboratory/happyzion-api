package org.happyzion.api.site.interfaces.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping

class SiteSettingUploadOnlyContractTest {

    @Test
    fun `admin main video API does not expose manual URL update over PUT`() {
        val putMappings = SiteSettingController::class.java.declaredMethods
            .mapNotNull { method -> method.getAnnotation(PutMapping::class.java) }
            .flatMap { mapping -> mapping.paths() }

        assertThat(putMappings)
            .doesNotContain(ADMIN_MAIN_VIDEO_PATH)
    }

    @Test
    fun `admin main video API keeps POST multipart upload support`() {
        val uploadMapping = SiteSettingController::class.java.declaredMethods
            .mapNotNull { method -> method.getAnnotation(PostMapping::class.java) }
            .firstOrNull { mapping ->
                mapping.paths().contains(ADMIN_MAIN_VIDEO_PATH)
            }

        assertThat(uploadMapping)
            .describedAs("Expected main video admin endpoint to keep POST multipart upload support.")
            .isNotNull
        assertThat(uploadMapping!!.consumes)
            .contains(MediaType.MULTIPART_FORM_DATA_VALUE)
    }

    private companion object {
        const val ADMIN_MAIN_VIDEO_PATH = "/api/v1/admin/site/main-video"
    }
}

private fun PutMapping.paths(): List<String> =
    value.asList() + path.asList()

private fun PostMapping.paths(): List<String> =
    value.asList() + path.asList()
