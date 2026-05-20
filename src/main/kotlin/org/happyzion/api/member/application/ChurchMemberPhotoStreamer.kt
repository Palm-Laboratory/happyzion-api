package org.happyzion.api.member.application

import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.board.application.AttachmentStorage
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class StreamedPhoto(val resource: Resource, val mimeType: String)

@Service
class ChurchMemberPhotoStreamer(
    private val memberRepo: ChurchMemberRepository,
    private val postAssetRepository: PostAssetRepository,
    private val attachmentStorage: AttachmentStorage,
    private val adminAccountGuard: AdminAccountGuard,
) {
    @Transactional(readOnly = true)
    fun load(memberId: Long, actorId: Long): StreamedPhoto {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(memberId).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val assetId = member.photoAssetId ?: throw NotFoundException("사진이 등록되어 있지 않습니다.")
        val asset = postAssetRepository.findById(assetId).orElseThrow { NotFoundException("사진 자산을 찾을 수 없습니다.") }
        val mime = asset.mimeType ?: throw NotFoundException("자산의 MIME 정보가 없습니다.")
        return StreamedPhoto(attachmentStorage.load(asset.storedPath), mime)
    }
}
