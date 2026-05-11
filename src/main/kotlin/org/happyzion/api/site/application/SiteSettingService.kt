package org.happyzion.api.site.application

import org.happyzion.api.site.domain.SiteSetting
import org.happyzion.api.site.infrastructure.persistence.SiteSettingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val MAIN_VIDEO_KEY = "main_video_url"
private const val DEFAULT_MAIN_VIDEO_URL = "/video/sample.mp4"

@Service
class SiteSettingService(
    private val siteSettingRepository: SiteSettingRepository,
) {
    @Transactional(readOnly = true)
    fun getMainVideoSetting(): MainVideoSetting =
        MainVideoSetting(videoUrl = currentMainVideoUrl())

    @Transactional
    fun updateMainVideoSetting(command: UpdateMainVideoSettingCommand): MainVideoSetting {
        val videoUrl = normalizeVideoUrl(command.videoUrl)
        val setting = siteSettingRepository.findByIdOrNull(MAIN_VIDEO_KEY)
            ?: SiteSetting(key = MAIN_VIDEO_KEY)

        setting.value = videoUrl
        siteSettingRepository.save(setting)

        return MainVideoSetting(videoUrl = videoUrl)
    }

    private fun currentMainVideoUrl(): String =
        siteSettingRepository.findByIdOrNull(MAIN_VIDEO_KEY)
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MAIN_VIDEO_URL

    private fun normalizeVideoUrl(videoUrl: String): String {
        val normalized = videoUrl.trim()
        require(normalized.isNotBlank()) { "메인 영상 URL을 입력해 주세요." }
        require(normalized.length <= 2000) { "메인 영상 URL은 2000자 이내로 입력해 주세요." }
        require(normalized.startsWith("/") || normalized.startsWith("https://") || normalized.startsWith("http://")) {
            "메인 영상 URL은 /로 시작하는 경로 또는 http(s) URL이어야 합니다."
        }
        require(!normalized.startsWith("//")) { "메인 영상 URL 형식이 올바르지 않습니다." }

        return normalized
    }
}
