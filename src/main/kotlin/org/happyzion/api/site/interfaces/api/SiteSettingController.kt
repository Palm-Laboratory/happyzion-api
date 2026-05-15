package org.happyzion.api.site.interfaces.api

import org.happyzion.api.board.application.UploadTokenService
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.common.security.AdminKeyRequired
import org.happyzion.api.site.application.SiteSettingService
import org.happyzion.api.site.interfaces.dto.toDto
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class SiteSettingController(
    private val siteSettingService: SiteSettingService,
    private val uploadTokenService: UploadTokenService,
) {
    @GetMapping("/api/v1/public/site/main-video")
    fun getPublicMainVideo() =
        siteSettingService.getMainVideoSetting().toDto()

    @AdminKeyRequired
    @GetMapping("/api/v1/admin/site/main-video")
    fun getAdminMainVideo() =
        siteSettingService.getMainVideoSetting().toDto()

    @PostMapping("/api/v1/admin/site/main-video", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAdminMainVideo(
        @RequestHeader(name = "X-Upload-Token") rawToken: String,
        @RequestParam("file") file: MultipartFile,
    ) = run {
        val mimeType = file.contentType?.trim()?.lowercase()
            ?: throw IllegalArgumentException("영상 파일 MIME 타입이 없습니다.")
        uploadTokenService.validateAndConsume(
            rawToken = rawToken,
            kind = PostAssetKind.MAIN_VIDEO,
            byteSize = file.size,
            mimeType = mimeType,
        )
        siteSettingService.uploadMainVideo(file).toDto()
    }
}
