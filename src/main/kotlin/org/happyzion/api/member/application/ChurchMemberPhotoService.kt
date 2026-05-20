package org.happyzion.api.member.application

import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.domain.ChurchMember
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ChurchMemberPhotoService(
    private val postAssetRepository: PostAssetRepository,
) {
    @Transactional
    fun replacePhoto(member: ChurchMember, assetId: Long, actorId: Long) {
        val asset = postAssetRepository.findById(assetId).orElseThrow {
            NotFoundException("사진 자산을 찾을 수 없습니다. id=$assetId")
        }
        require(asset.kind == PostAssetKind.MEMBER_PHOTO) { "교인 사진이 아닙니다." }
        require(asset.detachedAt != null) { "이미 사용 중인 자산입니다." }
        if (asset.uploadedByActorId != actorId) {
            throw ForbiddenException("다른 관리자가 업로드한 자산입니다.")
        }

        member.photoAssetId?.let { previousId ->
            postAssetRepository.findById(previousId).ifPresent { previous ->
                previous.detachedAt = OffsetDateTime.now()
                postAssetRepository.save(previous)
            }
        }

        asset.detachedAt = null
        try {
            postAssetRepository.save(asset)
        } catch (ex: DataIntegrityViolationException) {
            throw IllegalArgumentException("이미 다른 교인에 연결된 사진입니다.", ex)
        }
        member.linkPhoto(assetId)
    }

    @Transactional
    fun removePhoto(member: ChurchMember) {
        val previousId = member.photoAssetId ?: return
        postAssetRepository.findById(previousId).ifPresent { previous ->
            previous.detachedAt = OffsetDateTime.now()
            postAssetRepository.save(previous)
        }
        member.unlinkPhoto()
    }
}
