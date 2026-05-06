package org.happyzion.api.board.infrastructure.persistence

import org.happyzion.api.board.domain.PostAsset
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface PostAssetRepository : JpaRepository<PostAsset, Long> {
    fun findAllByPostIdOrderBySortOrderAscIdAsc(postId: Long): List<PostAsset>

    fun findAllByPostIdIn(postIds: Collection<Long>): List<PostAsset>

    fun findAllByPostIdIsNullAndDetachedAtBefore(cutoff: OffsetDateTime): List<PostAsset>
}
