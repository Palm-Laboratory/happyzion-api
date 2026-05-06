package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class TiptapContentValidatorTest {

    private val postAssetRepository: PostAssetRepository = mock()
    private val validator = TiptapContentValidator(postAssetRepository)

    @Test
    fun `accepts nested image nodes when asset id and stored path match actor-owned unassigned assets`() {
        val asset = uploadedAsset(id = 100L, actorId = 1L)
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(asset))

        validator.validate(
            contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/asset-100.png"),
            actorId = 1L,
        )
    }

    @Test
    fun `accepts image node when assetId is a JSON string instead of a number`() {
        val asset = uploadedAsset(id = 100L, actorId = 1L)
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(asset))

        validator.validate(
            contentJson = """
                {
                  "type": "doc",
                  "content": [
                    {
                      "type": "image",
                      "attrs": {
                        "assetId": "100",
                        "storedPath": "uploads/asset-100.png"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            actorId = 1L,
        )
    }

    @Test
    fun `rejects image node that contains only a src url without canonical asset attrs`() {
        assertThrows<IllegalArgumentException> {
            validator.validate(
                contentJson = """
                    {
                      "type": "doc",
                      "content": [
                        {
                          "type": "image",
                          "attrs": {
                            "src": "/upload/uploads/asset-100.png#happyzionAssetId=100&happyzionStoredPath=uploads%2Fasset-100.png"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `returns referenced image asset ids in traversal order`() {
        whenever(postAssetRepository.findById(200L)).thenReturn(Optional.of(uploadedAsset(id = 200L, actorId = 1L)))
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(uploadedAsset(id = 100L, actorId = 1L)))
        whenever(postAssetRepository.findById(300L)).thenReturn(Optional.of(uploadedAsset(id = 300L, actorId = 1L)))

        val referencedAssetIds = validator.validate(
            contentJson = docWithImages(
                200L to "uploads/asset-200.png",
                100L to "uploads/asset-100.png",
                300L to "uploads/asset-300.png",
            ),
            actorId = 1L,
        )

        assertThat(referencedAssetIds).isEqualTo(listOf(200L, 100L, 300L))
    }

    @Test
    fun `accepts image nodes already assigned to current post when updating`() {
        val asset = uploadedAsset(id = 100L, actorId = 1L, postId = 99L)
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(asset))

        validator.validate(
            contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/asset-100.png"),
            actorId = 1L,
            postId = 99L,
        )
    }

    @Test
    fun `rejects image node that only contains public url`() {
        assertThrows<IllegalArgumentException> {
            validator.validate(
                contentJson = """
                    {
                      "type": "doc",
                      "content": [
                        {
                          "type": "image",
                          "attrs": {
                            "publicUrl": "https://cdn.example.com/uploads/asset-100.png"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `rejects image node missing asset id`() {
        assertThrows<IllegalArgumentException> {
            validator.validate(
                contentJson = """
                    {
                      "type": "doc",
                      "content": [
                        {
                          "type": "image",
                          "attrs": {
                            "storedPath": "uploads/asset-100.png"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `rejects image node when asset id does not exist`() {
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            validator.validate(
                contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/asset-100.png"),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `rejects image node when asset id and stored path do not match the same row`() {
        whenever(postAssetRepository.findById(100L)).thenReturn(
            Optional.of(uploadedAsset(id = 100L, actorId = 1L, storedPath = "uploads/asset-100.png"))
        )

        assertThrows<IllegalArgumentException> {
            validator.validate(
                contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/different.png"),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `rejects image node when asset was uploaded by another actor`() {
        whenever(postAssetRepository.findById(100L)).thenReturn(
            Optional.of(uploadedAsset(id = 100L, actorId = 2L))
        )

        assertThrows<ForbiddenException> {
            validator.validate(
                contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/asset-100.png"),
                actorId = 1L,
            )
        }
    }

    @Test
    fun `rejects image node already assigned to a different post`() {
        whenever(postAssetRepository.findById(100L)).thenReturn(
            Optional.of(uploadedAsset(id = 100L, actorId = 1L, postId = 77L))
        )

        assertThrows<ForbiddenException> {
            validator.validate(
                contentJson = docWithNestedImage(assetId = 100L, storedPath = "uploads/asset-100.png"),
                actorId = 1L,
                postId = 99L,
            )
        }
    }

    @Test
    fun `accepts youtube embed video id only when it matches youtube id format exactly`() {
        validator.validate(
            contentJson = """
                {
                  "type": "doc",
                  "content": [
                    {
                      "type": "youtubeEmbed",
                      "attrs": {
                        "videoId": "AbC_123-xYz"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            actorId = 1L,
        )
    }

    @Test
    fun `rejects youtube embed video id with invalid length or characters`() {
        val invalidVideoIds = listOf("AbC_123-xY", "AbC_123-xYzz", "AbC_123+xYz")

        invalidVideoIds.forEach { videoId ->
            assertThrows<IllegalArgumentException> {
                validator.validate(
                    contentJson = """
                        {
                          "type": "doc",
                          "content": [
                            {
                              "type": "youtubeEmbed",
                              "attrs": {
                                "videoId": "$videoId"
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                    actorId = 1L,
                )
            }
        }
    }

    @Test
    fun `extracts video id from supported youtube urls`() {
        assertThat(TiptapContentValidator.extractYouTubeVideoId("https://www.youtube.com/watch?v=AbC_123-xYz"))
            .isEqualTo("AbC_123-xYz")
        assertThat(TiptapContentValidator.extractYouTubeVideoId("https://youtu.be/AbC_123-xYz"))
            .isEqualTo("AbC_123-xYz")
        assertThat(TiptapContentValidator.extractYouTubeVideoId("https://www.youtube.com/embed/AbC_123-xYz"))
            .isEqualTo("AbC_123-xYz")
    }

    @Test
    fun `rejects unsupported youtube url hosts`() {
        assertThrows<IllegalArgumentException> {
            TiptapContentValidator.extractYouTubeVideoId("https://example.com/watch?v=AbC_123-xYz")
        }
    }

    private fun docWithNestedImage(assetId: Long, storedPath: String) = """
        {
          "type": "doc",
          "content": [
            {
              "type": "bulletList",
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "image",
                          "attrs": {
                            "assetId": $assetId,
                            "storedPath": "$storedPath"
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun docWithImages(vararg images: Pair<Long, String>) = """
        {
          "type": "doc",
          "content": [
            {
              "type": "paragraph",
              "content": [
                ${images.joinToString(",\n                ") { (assetId, storedPath) ->
                    """
                    {
                      "type": "image",
                      "attrs": {
                        "assetId": $assetId,
                        "storedPath": "$storedPath"
                      }
                    }
                    """.trimIndent()
                }}
              ]
            }
          ]
        }
    """.trimIndent()

    private fun uploadedAsset(
        id: Long,
        actorId: Long,
        storedPath: String = "uploads/asset-$id.png",
        postId: Long? = null,
    ) = PostAsset(
        id = id,
        uploadedByActorId = actorId,
        kind = PostAssetKind.INLINE_IMAGE,
        originalFilename = "asset-$id.png",
        storedPath = storedPath,
        byteSize = 123L,
        postId = postId,
        mimeType = "image/png",
        width = 640,
        height = 480,
    )
}
