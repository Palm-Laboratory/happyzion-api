package org.happyzion.api.board.application

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.net.URI

@Component
class TiptapContentValidator(
    private val postAssetRepository: PostAssetRepository,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {

    fun validate(contentJson: String, actorId: Long, postId: Long? = null): List<Long> {
        val root = try {
            objectMapper.readTree(contentJson)
        } catch (ex: JsonProcessingException) {
            throw IllegalArgumentException("contentJson은 올바른 JSON이어야 합니다.", ex)
        }

        val referencedAssetIds = mutableListOf<Long>()
        validateNode(root, actorId, postId, referencedAssetIds)
        return referencedAssetIds
    }

    private fun validateNode(node: JsonNode, actorId: Long, postId: Long?, referencedAssetIds: MutableList<Long>) {
        if (node.isObject) {
            when (node.path("type").asText(null)) {
                "image" -> validateImageNode(node, actorId, postId, referencedAssetIds)
                "youtubeEmbed" -> validateYouTubeNode(node)
            }
        }

        node.elements().forEachRemaining { child ->
            validateNode(child, actorId, postId, referencedAssetIds)
        }
    }

    private fun validateImageNode(node: JsonNode, actorId: Long, postId: Long?, referencedAssetIds: MutableList<Long>) {
        val attrs = node.path("attrs")
        if (!attrs.isObject) {
            throw IllegalArgumentException("이미지 노드는 assetId와 storedPath가 필요합니다.")
        }

        val assetIdNode = attrs.path("assetId")
        val storedPathNode = attrs.path("storedPath")

        val assetId: Long = when {
            assetIdNode.canConvertToLong() -> assetIdNode.asLong()
            assetIdNode.isTextual -> assetIdNode.asText().toLongOrNull()
                ?: throw IllegalArgumentException("이미지 노드는 assetId와 storedPath가 필요합니다.")
            else -> throw IllegalArgumentException("이미지 노드는 assetId와 storedPath가 필요합니다.")
        }
        if (!storedPathNode.isTextual || storedPathNode.asText().isBlank()) {
            throw IllegalArgumentException("이미지 노드는 assetId와 storedPath가 필요합니다.")
        }
        val storedPath = storedPathNode.asText()

        val asset = postAssetRepository.findByIdOrNull(assetId)
            ?: throw NotFoundException("첨부 파일을 찾을 수 없습니다. id=$assetId")

        if (asset.storedPath != storedPath) {
            throw IllegalArgumentException("이미지 노드의 assetId와 storedPath가 일치하지 않습니다.")
        }

        if (asset.uploadedByActorId != actorId) {
            throw ForbiddenException("이미지 첨부 파일을 사용할 수 없습니다. id=$assetId")
        }

        referencedAssetIds += assetId

        if (postId == null) {
            if (asset.postId != null) {
                throw ForbiddenException("이미지 첨부 파일을 사용할 수 없습니다. id=$assetId")
            }
            return
        }

        if (asset.postId != null && asset.postId != postId) {
            throw ForbiddenException("이미지 첨부 파일을 사용할 수 없습니다. id=$assetId")
        }
    }

    private fun validateYouTubeNode(node: JsonNode) {
        val videoId = node.path("attrs").path("videoId").asText(null)
        if (videoId == null || !YOUTUBE_VIDEO_ID_PATTERN.matches(videoId)) {
            throw IllegalArgumentException("유효한 YouTube videoId가 필요합니다.")
        }
    }

    companion object {
        private val YOUTUBE_VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        private val SUPPORTED_YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
        )

        fun extractYouTubeVideoId(rawUrl: String): String {
            val uri = try {
                URI(rawUrl)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("지원하지 않는 YouTube URL입니다.", ex)
            }

            val host = uri.host?.lowercase()
            if (host !in SUPPORTED_YOUTUBE_HOSTS) {
                throw IllegalArgumentException("지원하지 않는 YouTube URL입니다.")
            }

            val videoId = when (host) {
                "youtu.be" -> uri.path.trim('/').substringBefore('/')
                else -> extractYouTubeComVideoId(uri)
            }

            if (!YOUTUBE_VIDEO_ID_PATTERN.matches(videoId)) {
                throw IllegalArgumentException("유효한 YouTube videoId가 필요합니다.")
            }

            return videoId
        }

        private fun extractYouTubeComVideoId(uri: URI): String {
            val pathSegments = uri.path.trim('/').split('/').filter { it.isNotBlank() }
            return when {
                pathSegments.firstOrNull() == "embed" && pathSegments.size >= 2 -> pathSegments[1]
                pathSegments.firstOrNull() == "shorts" && pathSegments.size >= 2 -> pathSegments[1]
                uri.path == "/watch" -> extractQueryParameter(uri.rawQuery, "v")
                else -> throw IllegalArgumentException("지원하지 않는 YouTube URL입니다.")
            }
        }

        private fun extractQueryParameter(rawQuery: String?, name: String): String {
            if (rawQuery.isNullOrBlank()) {
                throw IllegalArgumentException("지원하지 않는 YouTube URL입니다.")
            }

            return rawQuery.split('&')
                .mapNotNull { parameter ->
                    val index = parameter.indexOf('=')
                    if (index <= 0) {
                        null
                    } else {
                        parameter.substring(0, index) to parameter.substring(index + 1)
                    }
                }
                .firstOrNull { (key, _) -> key == name }
                ?.second
                ?: throw IllegalArgumentException("지원하지 않는 YouTube URL입니다.")
        }
    }
}

