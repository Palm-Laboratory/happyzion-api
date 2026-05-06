package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAssetKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class LocalAttachmentStorageTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `store keeps sanitized relative path for png and returns metadata`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "../../church.png",
            "image/png",
            pngBytes(),
        )

        val result = storage.store(
            file = file,
            kind = PostAssetKind.INLINE_IMAGE,
            maxByteSize = 1024L,
        )

        val storedPath = readStringProperty(result, "storedPath")
        val mimeType = readStringProperty(result, "mimeType")
        val byteSize = readLongProperty(result, "byteSize")
        val width = readIntProperty(result, "width")
        val height = readIntProperty(result, "height")

        assertThat(storedPath).matches("""^\d{4}/\d{2}/[0-9a-fA-F-]{36}\.png$""")
        assertThat(storedPath).doesNotContain("church.png")
        assertThat(storedPath).doesNotContain("..")
        assertThat(mimeType).isEqualTo("image/png")
        assertThat(byteSize).isEqualTo(file.size)
        assertThat(width).isEqualTo(1)
        assertThat(height).isEqualTo(1)
        assertThat(Files.exists(root.resolve(storedPath))).isTrue()
    }

    @Test
    fun `store keeps pdf extension and omits image dimensions`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "brochure.pdf",
            "application/pdf",
            pdfBytes(),
        )

        val result = storage.store(
            file = file,
            kind = PostAssetKind.FILE_ATTACHMENT,
            maxByteSize = 1024L,
        )

        val storedPath = readStringProperty(result, "storedPath")
        val mimeType = readStringProperty(result, "mimeType")
        val byteSize = readLongProperty(result, "byteSize")
        val width = readNullableIntProperty(result, "width")
        val height = readNullableIntProperty(result, "height")

        assertThat(storedPath).matches("""^\d{4}/\d{2}/[0-9a-fA-F-]{36}\.pdf$""")
        assertThat(storedPath).doesNotContain("brochure.pdf")
        assertThat(mimeType).isEqualTo("application/pdf")
        assertThat(byteSize).isEqualTo(file.size)
        assertThat(width).isNull()
        assertThat(height).isNull()
        assertThat(Files.exists(root.resolve(storedPath))).isTrue()
    }

    @Test
    fun `store rejects unsupported mime and leaves no files behind`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "note.txt",
            "text/plain",
            pngBytes(),
        )

        assertThatThrownBy {
            storage.store(
                file = file,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1024L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(childCount(root)).isEqualTo(0L)
    }

    @Test
    fun `store rejects mismatched magic bytes and leaves no files behind`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "photo.png",
            "image/png",
            pdfBytes(),
        )

        assertThatThrownBy {
            storage.store(
                file = file,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1024L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(childCount(root)).isEqualTo(0L)
    }

    @Test
    fun `store rejects oversized file before saving`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "big.png",
            "image/png",
            pngBytes(),
        )

        assertThatThrownBy {
            storage.store(
                file = file,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(childCount(root)).isEqualTo(0L)
    }

    @Test
    fun `store resizes raster images down to the max dimension policy`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "wide.png",
            "image/png",
            pngBytes(width = 4096, height = 1024),
        )

        val result = storage.store(
            file = file,
            kind = PostAssetKind.INLINE_IMAGE,
            maxByteSize = 10_000_000L,
        )

        val storedPath = readStringProperty(result, "storedPath")
        val width = readIntProperty(result, "width")
        val height = readIntProperty(result, "height")
        val storedImage = ImageIO.read(root.resolve(storedPath).toFile())

        assertThat(width).isEqualTo(2048)
        assertThat(height).isEqualTo(512)
        assertThat(storedImage.width).isEqualTo(2048)
        assertThat(storedImage.height).isEqualTo(512)
    }

    @Test
    fun `store rejects images whose pixel count exceeds the safety limit`() {
        val storage = LocalAttachmentStorage(root)
        val file = MockMultipartFile(
            "file",
            "huge.webp",
            "image/webp",
            webpBytes(width = 10_000, height = 8_001),
        )

        assertThatThrownBy {
            storage.store(
                file = file,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 20_000_000L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("이미지 해상도")

        assertThat(childCount(root)).isEqualTo(0L)
    }

    private fun pngBytes(width: Int = 1, height: Int = 1): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, Color(255, 0, 0, 255).rgb)
            }
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun pdfBytes(): ByteArray =
        "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".toByteArray()

    private fun webpBytes(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(30)
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'E'.code.toByte()
        bytes[10] = 'B'.code.toByte()
        bytes[11] = 'P'.code.toByte()
        bytes[12] = 'V'.code.toByte()
        bytes[13] = 'P'.code.toByte()
        bytes[14] = '8'.code.toByte()
        bytes[15] = 'X'.code.toByte()

        val widthMinusOne = width - 1
        val heightMinusOne = height - 1
        bytes[24] = (widthMinusOne and 0xFF).toByte()
        bytes[25] = ((widthMinusOne ushr 8) and 0xFF).toByte()
        bytes[26] = ((widthMinusOne ushr 16) and 0xFF).toByte()
        bytes[27] = (heightMinusOne and 0xFF).toByte()
        bytes[28] = ((heightMinusOne ushr 8) and 0xFF).toByte()
        bytes[29] = ((heightMinusOne ushr 16) and 0xFF).toByte()
        return bytes
    }

    private fun readStringProperty(target: Any, propertyName: String): String {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == propertyName }
            ?: error("Expected $propertyName field on ${target.javaClass.name}")
        field.isAccessible = true
        return field.get(target) as String
    }

    private fun readLongProperty(target: Any, propertyName: String): Long {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == propertyName }
            ?: error("Expected $propertyName field on ${target.javaClass.name}")
        field.isAccessible = true
        return when (val value = field.get(target)) {
            is Long -> value
            is Number -> value.toLong()
            else -> error("Expected numeric $propertyName field on ${target.javaClass.name}")
        }
    }

    private fun readIntProperty(target: Any, propertyName: String): Int {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == propertyName }
            ?: error("Expected $propertyName field on ${target.javaClass.name}")
        field.isAccessible = true
        return when (val value = field.get(target)) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Expected numeric $propertyName field on ${target.javaClass.name}")
        }
    }

    private fun readNullableIntProperty(target: Any, propertyName: String): Int? {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == propertyName }
            ?: error("Expected $propertyName field on ${target.javaClass.name}")
        field.isAccessible = true
        return when (val value = field.get(target)) {
            null -> null
            is Int -> value
            is Number -> value.toInt()
            else -> error("Expected nullable numeric $propertyName field on ${target.javaClass.name}")
        }
    }

    private fun childCount(dir: Path): Long =
        Files.list(dir).use { stream -> stream.count() }
}
