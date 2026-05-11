package org.happyzion.api.site.application

import org.happyzion.api.common.config.UploadProperties
import org.happyzion.api.site.domain.SiteSetting
import org.happyzion.api.site.infrastructure.persistence.SiteSettingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

private const val MAIN_VIDEO_KEY = "main_video_url"
private const val DEFAULT_MAIN_VIDEO_URL = "/video/sample.mp4"
private const val MAX_MAIN_VIDEO_BYTE_SIZE = 200L * 1024L * 1024L
private const val MAIN_VIDEO_SIGNATURE_BYTE_SIZE = 12

@Service
class SiteSettingService(
    private val siteSettingRepository: SiteSettingRepository,
    private val uploadProperties: UploadProperties,
) {
    @Transactional(readOnly = true)
    fun getMainVideoSetting(): MainVideoSetting =
        MainVideoSetting(videoUrl = currentMainVideoUrl())

    @Transactional
    fun uploadMainVideo(file: MultipartFile): MainVideoSetting {
        val mimeType = file.contentType?.trim()?.lowercase()
            ?: throw IllegalArgumentException("영상 파일 MIME 타입이 없습니다.")
        val extension = extensionForMainVideoMimeType(mimeType)
        require(file.size in 1..MAX_MAIN_VIDEO_BYTE_SIZE) { "메인 영상은 200MB 이하로 업로드해 주세요." }

        val originalFilename = file.originalFilename?.trim().orEmpty()
        require(originalFilename.isNotBlank()) { "영상 파일명이 없습니다." }

        val storedPath = buildMainVideoStoredPath(extension)
        val target = Path.of(uploadProperties.rootPath).resolve(storedPath).normalize()
        val rootPath = Path.of(uploadProperties.rootPath).toAbsolutePath().normalize()
        require(target.toAbsolutePath().startsWith(rootPath)) { "영상 저장 경로가 올바르지 않습니다." }

        writeMainVideoWithSignatureValidation(file, mimeType, target)

        val videoUrl = publicUploadUrl(storedPath)
        val previousVideoUrl = try {
            val previousVideoUrl = currentMainVideoUrl()
            val setting = siteSettingRepository.findByIdOrNull(MAIN_VIDEO_KEY)
                ?: SiteSetting(key = MAIN_VIDEO_KEY)
            setting.value = videoUrl
            siteSettingRepository.save(setting)
            previousVideoUrl
        } catch (ex: Exception) {
            Files.deleteIfExists(target)
            throw ex
        }

        deletePreviousUploadedMainVideoBestEffort(previousVideoUrl)

        return MainVideoSetting(videoUrl = videoUrl)
    }

    private fun currentMainVideoUrl(): String =
        siteSettingRepository.findByIdOrNull(MAIN_VIDEO_KEY)
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MAIN_VIDEO_URL

    private fun buildMainVideoStoredPath(extension: String): String {
        val now = LocalDate.now()
        return "site/main-video/%04d/%02d/%s.%s".format(
            now.year,
            now.monthValue,
            UUID.randomUUID().toString(),
            extension,
        )
    }

    private fun publicUploadUrl(storedPath: String): String =
        "${uploadProperties.publicBaseUrl.trimEnd('/')}/${storedPath.trimStart('/')}"

    private fun extensionForMainVideoMimeType(mimeType: String): String =
        when (mimeType) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            else -> throw IllegalArgumentException("MP4, WebM, MOV 영상만 업로드할 수 있습니다.")
        }

    private fun writeMainVideoWithSignatureValidation(
        file: MultipartFile,
        mimeType: String,
        target: Path,
    ) {
        file.inputStream.use { input ->
            val signature = input.readNBytes(MAIN_VIDEO_SIGNATURE_BYTE_SIZE)
            require(matchesVideoSignature(signature, mimeType)) { "지원하지 않는 영상 파일 형식입니다." }

            Files.createDirectories(target.parent)
            try {
                Files.newOutputStream(target).use { output ->
                    output.write(signature)
                    input.copyTo(output)
                }
            } catch (ex: Exception) {
                Files.deleteIfExists(target)
                throw ex
            }
        }
    }

    private fun matchesVideoSignature(bytes: ByteArray, mimeType: String): Boolean =
        when (mimeType) {
            "video/mp4", "video/quicktime" -> hasIsoBaseMediaSignature(bytes)
            "video/webm" -> hasWebmSignature(bytes)
            else -> false
        }

    private fun hasIsoBaseMediaSignature(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()

    private fun hasWebmSignature(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()

    private fun deletePreviousUploadedMainVideoBestEffort(videoUrl: String) {
        try {
            deletePreviousUploadedMainVideo(videoUrl)
        } catch (_: Exception) {
            // Previous upload cleanup must not invalidate the newly saved setting.
        }
    }

    private fun deletePreviousUploadedMainVideo(videoUrl: String) {
        val baseUrl = uploadProperties.publicBaseUrl.trimEnd('/')
        if (!videoUrl.startsWith("$baseUrl/")) return

        val storedPath = videoUrl.removePrefix("$baseUrl/").trimStart('/')
        val rootPath = Path.of(uploadProperties.rootPath).toAbsolutePath().normalize()
        val target = rootPath.resolve(storedPath).normalize()
        if (target.startsWith(rootPath)) {
            Files.deleteIfExists(target)
        }
    }
}
