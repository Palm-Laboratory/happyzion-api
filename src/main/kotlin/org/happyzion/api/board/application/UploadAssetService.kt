package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime

@Service
class UploadAssetService(
    private val uploadTokenService: UploadTokenService,
    private val attachmentStorage: AttachmentStorage,
    private val postAssetRepository: PostAssetRepository,
) {
    @Transactional
    fun upload(
        rawToken: String,
        file: MultipartFile,
        kind: PostAssetKind,
    ): UploadedAssetResult {
        val mimeType = file.contentType
            ?: throw IllegalArgumentException("첨부파일 MIME 타입이 없습니다.")
        val originalFilename = file.originalFilename
            ?: throw IllegalArgumentException("첨부파일 원본 파일명이 없습니다.")
        val byteSize = file.size

        val validation = uploadTokenService.validateAndConsume(
            rawToken = rawToken,
            kind = kind,
            byteSize = byteSize,
            mimeType = mimeType,
        )

        val storedAttachment = attachmentStorage.store(
            file = file,
            kind = kind,
            maxByteSize = validation.maxByteSize,
        )

        val asset = PostAsset(
            uploadedByActorId = validation.actorId,
            kind = kind,
            originalFilename = originalFilename,
            storedPath = storedAttachment.storedPath,
            byteSize = storedAttachment.byteSize,
            detachedAt = OffsetDateTime.now(),
            mimeType = storedAttachment.mimeType,
            width = storedAttachment.width,
            height = storedAttachment.height,
        )

        return try {
            val saved = postAssetRepository.save(asset)
            UploadedAssetResult(
                assetId = saved.id!!,
                storedPath = saved.storedPath,
                mimeType = saved.mimeType,
                byteSize = saved.byteSize,
                width = saved.width,
                height = saved.height,
            )
        } catch (ex: RuntimeException) {
            attachmentStorage.delete(storedAttachment.storedPath)
            throw ex
        }
    }
}

data class UploadedAssetResult(
    val assetId: Long,
    val storedPath: String,
    val mimeType: String?,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
)
