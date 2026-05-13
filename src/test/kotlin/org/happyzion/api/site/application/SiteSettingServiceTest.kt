package org.happyzion.api.site.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.config.UploadProperties
import org.happyzion.api.site.domain.SiteSetting
import org.happyzion.api.site.infrastructure.persistence.SiteSettingRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

class SiteSettingServiceTest {

    @TempDir
    lateinit var uploadRoot: Path

    private val siteSettingRepository: SiteSettingRepository = mock()

    @Test
    fun `upload main video keeps new setting when previous uploaded video cleanup fails`() {
        val previousStoredPath = "site/main-video/2026/04/previous.mp4"
        val previousPath = uploadRoot.resolve(previousStoredPath)
        Files.createDirectories(previousPath)
        Files.write(previousPath.resolve("child.txt"), byteArrayOf(1))
        val setting = SiteSetting(
            key = MAIN_VIDEO_KEY,
            value = "$PUBLIC_BASE_URL/$previousStoredPath",
        )
        val service = service()
        whenever(siteSettingRepository.findById(MAIN_VIDEO_KEY)).thenReturn(Optional.of(setting))
        whenever(siteSettingRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }

        val result = service.uploadMainVideo(mp4File())

        assertThat(result.videoUrl).startsWith("$PUBLIC_BASE_URL/site/main-video/")
        assertThat(result.videoUrl).endsWith(".mp4")
        assertThat(setting.value).isEqualTo(result.videoUrl)
        assertThat(Files.exists(previousPath.resolve("child.txt"))).isTrue()
    }

    @Test
    fun `upload main video deletes newly written file when repository save fails`() {
        val setting = SiteSetting(key = MAIN_VIDEO_KEY, value = "/video/sample.mp4")
        val service = service()
        whenever(siteSettingRepository.findById(MAIN_VIDEO_KEY)).thenReturn(Optional.of(setting))
        whenever(siteSettingRepository.save(any())).thenThrow(IllegalStateException("database unavailable"))

        assertThatThrownBy {
            service.uploadMainVideo(mp4File())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("database unavailable")

        assertThat(Files.walk(uploadRoot).use { paths ->
            paths.anyMatch { Files.isRegularFile(it) }
        }).isFalse()
    }

    @Test
    fun `upload main video deletes partially written file when stream fails after valid signature`() {
        val service = service()

        assertThatThrownBy {
            service.uploadMainVideo(FailingAfterSignatureMultipartFile())
        }.isInstanceOf(IOException::class.java)
            .hasMessage("stream failed after signature")

        assertThat(Files.walk(uploadRoot).use { paths ->
            paths.anyMatch { Files.isRegularFile(it) }
        }).isFalse()
    }

    @Test
    fun `upload main video ignores missing previous uploaded video during cleanup`() {
        val setting = SiteSetting(
            key = MAIN_VIDEO_KEY,
            value = "$PUBLIC_BASE_URL/site/main-video/2026/04/missing.mp4",
        )
        val service = service()
        whenever(siteSettingRepository.findById(MAIN_VIDEO_KEY)).thenReturn(Optional.of(setting))
        whenever(siteSettingRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }

        assertThatCode {
            service.uploadMainVideo(mp4File())
        }.doesNotThrowAnyException()

        assertThat(setting.value).startsWith("$PUBLIC_BASE_URL/site/main-video/")
    }

    @Test
    fun `upload main video streams file content without requiring bytes`() {
        val service = service()
        whenever(siteSettingRepository.findById(MAIN_VIDEO_KEY)).thenReturn(Optional.empty())
        whenever(siteSettingRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }

        val result = service.uploadMainVideo(InputStreamOnlyMultipartFile())

        assertThat(result.videoUrl).startsWith("$PUBLIC_BASE_URL/site/main-video/")
        assertThat(result.videoUrl).endsWith(".mp4")
        assertThat(Files.walk(uploadRoot).use { paths ->
            paths.anyMatch { Files.isRegularFile(it) }
        }).isTrue()
    }

    private fun service(): SiteSettingService =
        SiteSettingService(
            siteSettingRepository = siteSettingRepository,
            uploadProperties = UploadProperties(
                rootPath = uploadRoot.toString(),
                publicBaseUrl = PUBLIC_BASE_URL,
            ),
        )

    private fun mp4File(): MockMultipartFile =
        MockMultipartFile(
            "file",
            "main.mp4",
            "video/mp4",
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x18,
                'f'.code.toByte(),
                't'.code.toByte(),
                'y'.code.toByte(),
                'p'.code.toByte(),
                'i'.code.toByte(),
                's'.code.toByte(),
                'o'.code.toByte(),
                'm'.code.toByte(),
                0x00,
                0x00,
                0x02,
                0x00,
            ),
        )

    private class InputStreamOnlyMultipartFile : MultipartFile {
        private val content = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x18,
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte(),
            'o'.code.toByte(),
            'm'.code.toByte(),
            0x00,
            0x00,
            0x02,
            0x00,
        )

        override fun getName(): String = "file"

        override fun getOriginalFilename(): String = "main.mp4"

        override fun getContentType(): String = "video/mp4"

        override fun isEmpty(): Boolean = false

        override fun getSize(): Long = content.size.toLong()

        override fun getBytes(): ByteArray = throw AssertionError("MultipartFile.bytes should not be used")

        override fun getInputStream(): InputStream = ByteArrayInputStream(content)

        override fun transferTo(dest: File) {
            dest.outputStream().use { output ->
                getInputStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private class FailingAfterSignatureMultipartFile : MultipartFile {
        private val signature = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x18,
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte(),
            'o'.code.toByte(),
            'm'.code.toByte(),
        )
        private val bodyChunk = byteArrayOf(0x00, 0x00, 0x02, 0x00)

        override fun getName(): String = "file"

        override fun getOriginalFilename(): String = "main.mp4"

        override fun getContentType(): String = "video/mp4"

        override fun isEmpty(): Boolean = false

        override fun getSize(): Long = signature.size + bodyChunk.size + 8L

        override fun getBytes(): ByteArray = throw AssertionError("MultipartFile.bytes should not be used")

        override fun getInputStream(): InputStream = object : InputStream() {
            private var offset = 0
            private var bodyOffset = 0

            override fun read(): Int {
                if (offset < signature.size) {
                    return signature[offset++].toInt() and 0xff
                }
                if (bodyOffset < bodyChunk.size) {
                    return bodyChunk[bodyOffset++].toInt() and 0xff
                }
                throw IOException("stream failed after signature")
            }

            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (offset < signature.size) {
                    val bytesToCopy = minOf(len, signature.size - offset)
                    signature.copyInto(buffer, off, offset, offset + bytesToCopy)
                    offset += bytesToCopy
                    return bytesToCopy
                }
                if (bodyOffset < bodyChunk.size) {
                    val bytesToCopy = minOf(len, bodyChunk.size - bodyOffset)
                    bodyChunk.copyInto(buffer, off, bodyOffset, bodyOffset + bytesToCopy)
                    bodyOffset += bytesToCopy
                    return bytesToCopy
                }
                throw IOException("stream failed after signature")
            }
        }

        override fun transferTo(dest: File) {
            dest.outputStream().use { output ->
                getInputStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private companion object {
        const val MAIN_VIDEO_KEY = "main_video_url"
        const val PUBLIC_BASE_URL = "https://cdn.example.com/upload"
    }
}
