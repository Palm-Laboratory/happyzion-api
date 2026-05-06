package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PostAssetCleanupServiceTest {
    private val postAssetRepository: PostAssetRepository = mock()
    private val attachmentStorage: AttachmentStorage = mock()
    private val fixedNow = OffsetDateTime.parse("2026-04-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(), ZoneOffset.UTC)
    private val service = PostAssetCleanupService(
        postAssetRepository = postAssetRepository,
        attachmentStorage = attachmentStorage,
        clock = clock,
    )

    @Test
    fun `cleanupStaleTemporaryAssets deletes storage objects before rows and returns deleted count`() {
        val cutoff = fixedNow.minusHours(24)
        val staleInlineImage = postAsset(
            id = 11L,
            storedPath = "board/inline/old-image.png",
            detachedAt = cutoff.minusSeconds(1),
        )
        val staleAttachment = postAsset(
            id = 12L,
            kind = PostAssetKind.FILE_ATTACHMENT,
            storedPath = "board/files/old-file.pdf",
            detachedAt = cutoff.minusDays(1),
        )
        val staleTemporaryAssets = listOf(staleInlineImage, staleAttachment)
        whenever(postAssetRepository.findAllByPostIdIsNullAndDetachedAtBefore(cutoff))
            .thenReturn(staleTemporaryAssets)

        val deletedCount: Long = service.cleanupStaleTemporaryAssets()

        val order = inOrder(attachmentStorage, postAssetRepository)
        order.verify(attachmentStorage).delete(staleInlineImage.storedPath)
        order.verify(attachmentStorage).delete(staleAttachment.storedPath)
        order.verify(postAssetRepository).deleteAll(staleTemporaryAssets)
        verify(postAssetRepository).findAllByPostIdIsNullAndDetachedAtBefore(cutoff)
        assertThat(deletedCount).isEqualTo(2L)
    }

    @Test
    fun `cleanupStaleTemporaryAssets ignores attached and recent assets through repository cutoff query`() {
        val cutoff = fixedNow.minusHours(24)
        whenever(postAssetRepository.findAllByPostIdIsNullAndDetachedAtBefore(cutoff))
            .thenReturn(emptyList<PostAsset>())

        val deletedCount: Long = service.cleanupStaleTemporaryAssets()

        verify(postAssetRepository).findAllByPostIdIsNullAndDetachedAtBefore(cutoff)
        verify(attachmentStorage, never()).delete(org.mockito.kotlin.any())
        verify(postAssetRepository, never()).deleteAll(org.mockito.kotlin.any<Iterable<PostAsset>>())
        assertThat(deletedCount).isZero()
    }

    private fun postAsset(
        id: Long,
        kind: PostAssetKind = PostAssetKind.INLINE_IMAGE,
        storedPath: String,
        detachedAt: OffsetDateTime,
        postId: Long? = null,
    ) = PostAsset(
        id = id,
        uploadedByActorId = 42L,
        kind = kind,
        originalFilename = storedPath.substringAfterLast('/'),
        storedPath = storedPath,
        byteSize = 1024L,
        postId = postId,
        detachedAt = detachedAt,
        mimeType = if (kind == PostAssetKind.INLINE_IMAGE) "image/png" else "application/pdf",
        width = if (kind == PostAssetKind.INLINE_IMAGE) 640 else null,
        height = if (kind == PostAssetKind.INLINE_IMAGE) 480 else null,
        createdAt = fixedNow.minusDays(30),
        updatedAt = fixedNow.minusDays(30),
    )
}
