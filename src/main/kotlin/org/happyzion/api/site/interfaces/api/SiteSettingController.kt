package org.happyzion.api.site.interfaces.api

import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.site.application.SiteSettingService
import org.happyzion.api.site.interfaces.dto.UpdateMainVideoSettingRequest
import org.happyzion.api.site.interfaces.dto.toCommand
import org.happyzion.api.site.interfaces.dto.toDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SiteSettingController(
    private val siteSettingService: SiteSettingService,
    private val adminProperties: AdminProperties,
) {
    @GetMapping("/api/v1/public/site/main-video")
    fun getPublicMainVideo() =
        siteSettingService.getMainVideoSetting().toDto()

    @GetMapping("/api/v1/admin/site/main-video")
    fun getAdminMainVideo(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
    ) = run {
        validateAdminKey(adminKey)
        siteSettingService.getMainVideoSetting().toDto()
    }

    @PutMapping("/api/v1/admin/site/main-video")
    fun updateAdminMainVideo(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestBody request: UpdateMainVideoSettingRequest,
    ) = run {
        validateAdminKey(adminKey)
        siteSettingService.updateMainVideoSetting(request.toCommand()).toDto()
    }

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
    }
}
