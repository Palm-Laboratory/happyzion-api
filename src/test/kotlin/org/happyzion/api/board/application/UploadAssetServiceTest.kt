package org.happyzion.api.board.application

import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

class UploadAssetServiceTest {

    private val tokenService: UploadTokenService = mock()
    private val attachmentStorage: AttachmentStorage = mock()
    private val postAssetRepository: PostAssetRepository = mock()
    private val adminAccountGuard: AdminAccountGuard = mock()

    private val service = UploadAssetService(
        uploadTokenService = tokenService,
        attachmentStorage = attachmentStorage,
        postAssetRepository = postAssetRepository,
        adminAccountGuard = adminAccountGuard,
    )

    @Test
    fun `upload validates token before storage and persists the uploaded asset`() {
        val rawToken = "raw-upload-token"
        val kind = PostAssetKind.INLINE_IMAGE
        val file = MockMultipartFile(
            "file",
            "hero.png",
            "image/png",
            byteArrayOf(1, 2, 3, 4),
        )
        val validation = UploadTokenValidationResult(
            actorId = 42L,
            kind = kind,
            maxByteSize = 10_000L,
            allowedMimeTypes = listOf("image/png"),
        )
        val storedAttachment = StoredAttachment(
            storedPath = "2026/04/hero.png",
            mimeType = "image/png",
            byteSize = file.size,
            width = 640,
            height = 480,
        )
        whenever(
            tokenService.validateAndConsume(
                rawToken = rawToken,
                kind = kind,
                byteSize = file.size,
                mimeType = file.contentType!!,
            ),
        ).thenReturn(validation)
        whenever(
            attachmentStorage.store(
                file = file,
                kind = kind,
                maxByteSize = validation.maxByteSize,
            ),
        ).thenReturn(storedAttachment)
        whenever(
            postAssetRepository.save(
                argThat {
                    uploadedByActorId == validation.actorId &&
                        this.kind == kind &&
                        originalFilename == "hero.png" &&
                        storedPath == storedAttachment.storedPath &&
                        mimeType == storedAttachment.mimeType &&
                        byteSize == storedAttachment.byteSize &&
                        detachedAt != null &&
                        width == storedAttachment.width &&
                        height == storedAttachment.height
                },
            ),
        ).thenReturn(
            PostAsset(
                id = 99L,
                uploadedByActorId = validation.actorId,
                kind = kind,
                originalFilename = "hero.png",
                storedPath = storedAttachment.storedPath,
                byteSize = storedAttachment.byteSize,
                mimeType = storedAttachment.mimeType,
                width = storedAttachment.width,
                height = storedAttachment.height,
            ),
        )

        val result = service.upload(
            rawToken = rawToken,
            file = file,
            kind = kind,
        )

        val savedAsset = argumentCaptor<PostAsset>().apply {
            verify(postAssetRepository).save(capture())
        }.firstValue

        val order = inOrder(tokenService, attachmentStorage, postAssetRepository)
        order.verify(tokenService).validateAndConsume(
            rawToken = rawToken,
            kind = kind,
            byteSize = file.size,
            mimeType = file.contentType!!,
        )
        order.verify(attachmentStorage).store(
            file = file,
            kind = kind,
            maxByteSize = validation.maxByteSize,
        )
        order.verify(postAssetRepository).save(savedAsset)

        assertThat(result.assetId).isEqualTo(99L)
        assertThat(result.storedPath).isEqualTo(storedAttachment.storedPath)
        assertThat(result.mimeType).isEqualTo(storedAttachment.mimeType)
        assertThat(result.byteSize).isEqualTo(storedAttachment.byteSize)
        assertThat(result.width).isEqualTo(storedAttachment.width)
        assertThat(result.height).isEqualTo(storedAttachment.height)
        assertThat(savedAsset.detachedAt).isNotNull()
    }

    @Test
    fun `upload does not touch storage or repository when token validation fails`() {
        val rawToken = "invalid-token"
        val kind = PostAssetKind.FILE_ATTACHMENT
        val file = MockMultipartFile(
            "file",
            "brochure.pdf",
            "application/pdf",
            byteArrayOf(9, 8, 7),
        )
        whenever(
            tokenService.validateAndConsume(
                rawToken = rawToken,
                kind = kind,
                byteSize = file.size,
                mimeType = file.contentType!!,
            ),
        ).thenThrow(UnauthorizedException("invalid"))

        assertThatThrownBy {
            service.upload(
                rawToken = rawToken,
                file = file,
                kind = kind,
            )
        }.isInstanceOf(UnauthorizedException::class.java)

        verify(attachmentStorage, never()).store(
            org.mockito.kotlin.eq(file),
            org.mockito.kotlin.eq(kind),
            org.mockito.kotlin.any(),
        )
        verify(postAssetRepository, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `upload does not save repository when storage fails`() {
        val rawToken = "raw-upload-token"
        val kind = PostAssetKind.INLINE_IMAGE
        val file = MockMultipartFile(
            "file",
            "hero.png",
            "image/png",
            byteArrayOf(1, 2, 3, 4),
        )
        val validation = UploadTokenValidationResult(
            actorId = 42L,
            kind = kind,
            maxByteSize = 10_000L,
            allowedMimeTypes = listOf("image/png"),
        )
        whenever(
            tokenService.validateAndConsume(
                rawToken = rawToken,
                kind = kind,
                byteSize = file.size,
                mimeType = file.contentType!!,
            ),
        ).thenReturn(validation)
        whenever(
            attachmentStorage.store(
                file = file,
                kind = kind,
                maxByteSize = validation.maxByteSize,
            ),
        ).thenThrow(IllegalArgumentException("storage rejected"))

        assertThatThrownBy {
            service.upload(
                rawToken = rawToken,
                file = file,
                kind = kind,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(postAssetRepository, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `upload deletes stored file when repository save fails`() {
        val rawToken = "raw-upload-token"
        val kind = PostAssetKind.INLINE_IMAGE
        val file = MockMultipartFile(
            "file",
            "hero.png",
            "image/png",
            byteArrayOf(1, 2, 3, 4),
        )
        val validation = UploadTokenValidationResult(
            actorId = 42L,
            kind = kind,
            maxByteSize = 10_000L,
            allowedMimeTypes = listOf("image/png"),
        )
        val storedAttachment = StoredAttachment(
            storedPath = "2026/04/hero.png",
            mimeType = "image/png",
            byteSize = file.size,
            width = 640,
            height = 480,
        )
        whenever(
            tokenService.validateAndConsume(
                rawToken = rawToken,
                kind = kind,
                byteSize = file.size,
                mimeType = file.contentType!!,
            ),
        ).thenReturn(validation)
        whenever(
            attachmentStorage.store(
                file = file,
                kind = kind,
                maxByteSize = validation.maxByteSize,
            ),
        ).thenReturn(storedAttachment)
        whenever(
            postAssetRepository.save(
                org.mockito.kotlin.any(),
            ),
        ).thenThrow(RuntimeException("database down"))

        assertThatThrownBy {
            service.upload(
                rawToken = rawToken,
                file = file,
                kind = kind,
            )
        }.isInstanceOf(RuntimeException::class.java)

        verify(attachmentStorage).delete(storedAttachment.storedPath)
    }

    @Test
    fun `MEMBER_PHOTO upload by inactive admin throws Forbidden and deletes stored file`() {
        val rawToken = "raw-upload-token"
        val kind = PostAssetKind.MEMBER_PHOTO
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val validation = UploadTokenValidationResult(
            actorId = 1L, kind = kind, maxByteSize = 5_000_000L,
            allowedMimeTypes = listOf("image/jpeg"),
        )
        val storedAttachment = StoredAttachment(
            storedPath = "member-photos/2026/05/uuid.jpg",
            mimeType = "image/jpeg", byteSize = 3L, width = null, height = null,
        )
        whenever(tokenService.validateAndConsume(rawToken, kind, file.size, file.contentType!!))
            .thenReturn(validation)
        whenever(attachmentStorage.store(file, kind, validation.maxByteSize)).thenReturn(storedAttachment)
        doThrow(ForbiddenException("비활성 관리자입니다.")).whenever(adminAccountGuard).verify(any())

        assertThatThrownBy {
            service.upload(rawToken = rawToken, file = file, kind = kind)
        }.isInstanceOf(ForbiddenException::class.java)

        verify(attachmentStorage).delete(storedAttachment.storedPath)
        verify(postAssetRepository, never()).save(any())
    }

    @Test
    fun `non MEMBER_PHOTO upload does not call adminAccountGuard`() {
        val rawToken = "raw-upload-token"
        val kind = PostAssetKind.INLINE_IMAGE
        val file = MockMultipartFile("file", "hero.png", "image/png", byteArrayOf(1, 2, 3, 4))
        val validation = UploadTokenValidationResult(
            actorId = 42L, kind = kind, maxByteSize = 10_000L,
            allowedMimeTypes = listOf("image/png"),
        )
        val storedAttachment = StoredAttachment(
            storedPath = "2026/05/hero.png",
            mimeType = "image/png", byteSize = 4L, width = 1, height = 1,
        )
        whenever(tokenService.validateAndConsume(rawToken, kind, file.size, file.contentType!!))
            .thenReturn(validation)
        whenever(attachmentStorage.store(file, kind, validation.maxByteSize)).thenReturn(storedAttachment)
        whenever(postAssetRepository.save(any())).thenReturn(
            PostAsset(
                id = 1L, uploadedByActorId = 42L, kind = kind,
                originalFilename = "hero.png", storedPath = storedAttachment.storedPath,
                byteSize = 4L, mimeType = "image/png", width = 1, height = 1,
            )
        )

        service.upload(rawToken = rawToken, file = file, kind = kind)

        verify(adminAccountGuard, never()).verify(any())
    }
}
