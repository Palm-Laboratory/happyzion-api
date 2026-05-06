package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.common.config.UploadProperties
import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO

private const val MAX_DIMENSION = 2048
private const val MAX_PIXEL_COUNT = 80_000_000L

@Component
class LocalAttachmentStorage(
    private val rootPath: Path,
) : AttachmentStorage {

    @Autowired
    constructor(uploadProperties: UploadProperties) : this(Path.of(uploadProperties.rootPath))

    override fun store(
        file: MultipartFile,
        kind: PostAssetKind,
        maxByteSize: Long,
    ): StoredAttachment {
        val byteSize = file.size
        if (byteSize > maxByteSize) {
            throw IllegalArgumentException("첨부파일이 허용 크기를 초과합니다.")
        }

        val bytes = file.bytes
        val mimeType = detectMimeType(bytes)
        require(mimeType == file.contentType) { "첨부파일 MIME 타입이 일치하지 않습니다." }

        val processedAttachment = processAttachment(bytes, mimeType)
        val extension = extensionForMimeType(processedAttachment.mimeType)
        val storedPath = buildStoredPath(extension)
        val target = rootPath.resolve(storedPath)

        Files.createDirectories(target.parent)
        Files.write(target, processedAttachment.bytes)

        return StoredAttachment(
            storedPath = storedPath,
            mimeType = processedAttachment.mimeType,
            byteSize = processedAttachment.bytes.size.toLong(),
            width = processedAttachment.width,
            height = processedAttachment.height,
        )
    }

    override fun delete(storedPath: String) {
        Files.deleteIfExists(rootPath.resolve(storedPath))
    }

    private fun processAttachment(bytes: ByteArray, mimeType: String): ProcessedAttachment =
        when (mimeType) {
            "image/jpeg", "image/png" -> processRasterImage(bytes, mimeType)
            "image/webp" -> processWebpImage(bytes)
            else -> ProcessedAttachment(
                bytes = bytes,
                mimeType = mimeType,
                width = null,
                height = null,
            )
        }

    private fun processWebpImage(bytes: ByteArray): ProcessedAttachment {
        val dims = webpDimensions(bytes)
        checkPixelCount(dims.first, dims.second)

        val sourceImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("이미지 첨부파일을 읽을 수 없습니다.")

        return processRasterImage(sourceImage, "image/png")
    }

    private fun processRasterImage(bytes: ByteArray, mimeType: String): ProcessedAttachment {
        val sourceImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("이미지 첨부파일을 읽을 수 없습니다.")

        return processRasterImage(sourceImage, mimeType)
    }

    private fun processRasterImage(sourceImage: java.awt.image.BufferedImage, mimeType: String): ProcessedAttachment {
        checkPixelCount(sourceImage.width, sourceImage.height)

        val format = if (mimeType == "image/jpeg") "jpeg" else "png"
        val out = ByteArrayOutputStream()
        val needsResize = sourceImage.width > MAX_DIMENSION || sourceImage.height > MAX_DIMENSION

        val builder = if (needsResize) {
            Thumbnails.of(sourceImage).size(MAX_DIMENSION, MAX_DIMENSION).keepAspectRatio(true)
        } else {
            Thumbnails.of(sourceImage).scale(1.0)
        }
        builder.outputFormat(format).outputQuality(0.85).toOutputStream(out)

        val processedBytes = out.toByteArray()
        val finalImage = ImageIO.read(ByteArrayInputStream(processedBytes))
        return ProcessedAttachment(
            bytes = processedBytes,
            mimeType = if (format == "jpeg") "image/jpeg" else "image/png",
            width = finalImage?.width,
            height = finalImage?.height,
        )
    }

    private fun checkPixelCount(width: Int?, height: Int?) {
        if (width != null && height != null && width.toLong() * height.toLong() > MAX_PIXEL_COUNT) {
            throw IllegalArgumentException("이미지 해상도가 허용 범위를 초과합니다.")
        }
    }

    private fun buildStoredPath(extension: String): String {
        val now = LocalDate.now()
        return "%04d/%02d/%s.%s".format(
            now.year,
            now.monthValue,
            UUID.randomUUID().toString(),
            extension,
        )
    }

    private fun detectMimeType(bytes: ByteArray): String =
        when {
            isPng(bytes) -> "image/png"
            isJpeg(bytes) -> "image/jpeg"
            isWebp(bytes) -> "image/webp"
            isPdf(bytes) -> "application/pdf"
            else -> throw IllegalArgumentException("지원하지 않는 첨부파일 형식입니다.")
        }

    private fun extensionForMimeType(mimeType: String): String =
        when (mimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            else -> throw IllegalArgumentException("지원하지 않는 첨부파일 형식입니다.")
        }

    private fun webpDimensions(bytes: ByteArray): Pair<Int?, Int?> {
        if (bytes.size < 30) return null to null

        val header = String(bytes, 0, 4, Charsets.US_ASCII)
        val webpMarker = String(bytes, 8, 4, Charsets.US_ASCII)
        if (header != "RIFF" || webpMarker != "WEBP") {
            throw IllegalArgumentException("지원하지 않는 첨부파일 형식입니다.")
        }

        return when (String(bytes, 12, 4, Charsets.US_ASCII)) {
            "VP8 " -> vp8Dimensions(bytes)
            "VP8L" -> vp8lDimensions(bytes)
            "VP8X" -> vp8xDimensions(bytes)
            else -> null to null
        }
    }

    // VP8 lossy: keyframe dimensions at bytes 26–29
    private fun vp8Dimensions(bytes: ByteArray): Pair<Int?, Int?> {
        if (bytes.size < 30) return null to null
        if ((bytes[20].toInt() and 0x01) != 0) return null to null
        if (bytes[23] != 0x9D.toByte() || bytes[24] != 0x01.toByte() || bytes[25] != 0x2A.toByte()) return null to null
        val width = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)) and 0x3FFF
        val height = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)) and 0x3FFF
        return width to height
    }

    // VP8L lossless: signature 0x2F at byte 20, then (width-1) in bits 0–13, (height-1) in bits 14–27
    private fun vp8lDimensions(bytes: ByteArray): Pair<Int?, Int?> {
        if (bytes.size < 25 || bytes[20] != 0x2F.toByte()) return null to null
        val bits = (bytes[21].toInt() and 0xFF) or
            ((bytes[22].toInt() and 0xFF) shl 8) or
            ((bytes[23].toInt() and 0xFF) shl 16) or
            ((bytes[24].toInt() and 0xFF) shl 24)
        return ((bits and 0x3FFF) + 1) to (((bits ushr 14) and 0x3FFF) + 1)
    }

    // VP8X extended: canvas (width-1) at bytes 24–26, (height-1) at bytes 27–29 (24-bit LE)
    private fun vp8xDimensions(bytes: ByteArray): Pair<Int?, Int?> {
        if (bytes.size < 30) return null to null
        val width = ((bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16)) + 1
        val height = ((bytes[27].toInt() and 0xFF) or
            ((bytes[28].toInt() and 0xFF) shl 8) or
            ((bytes[29].toInt() and 0xFF) shl 16)) + 1
        return width to height
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()

    private fun isPdf(bytes: ByteArray): Boolean =
        bytes.size >= 5 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[4] == '-'.code.toByte()
}

private data class ProcessedAttachment(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
)
