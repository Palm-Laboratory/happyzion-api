package org.happyzion.api.member.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.board.application.AttachmentStorage
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.io.Resource
import java.time.LocalDate
import java.util.Optional

class ChurchMemberPhotoStreamerTest {

    private val memberRepo: ChurchMemberRepository = mock()
    private val postAssetRepository: PostAssetRepository = mock()
    private val attachmentStorage: AttachmentStorage = mock()
    private val adminAccountGuard: AdminAccountGuard = mock()

    private val streamer = ChurchMemberPhotoStreamer(
        memberRepo, postAssetRepository, attachmentStorage, adminAccountGuard,
    )

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val props = PiiEncryptionProperties("v1:$key", "v1", key)
    private val hasher = PiiHasher(props)
    private val normalizer = MemberSearchKeyNormalizer()

    private fun makeMember(photoAssetId: Long? = null): ChurchMember {
        val m = ChurchMember.create(
            name = "김철수", phone = "01012345678", email = null,
            birthDate = LocalDate.of(1990, 1, 1), birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M, address = "서울", addressDetail = null, job = null,
            cellLabel = null, status = ChurchMemberStatus.ACTIVE, faithStage = null,
            office = ChurchMemberOffice.LAY, officeAppointedAt = null,
            registeredAt = LocalDate.of(2024, 1, 1), memo = null,
            hasher = hasher, normalizer = normalizer,
        )
        if (photoAssetId != null) m.linkPhoto(photoAssetId)
        return m
    }

    private fun makeAsset(
        id: Long = 10L,
        mimeType: String? = "image/jpeg",
        storedPath: String = "/uploads/photo.jpg",
    ) = PostAsset(
        id = id,
        uploadedByActorId = 1L,
        kind = PostAssetKind.MEMBER_PHOTO,
        originalFilename = "photo.jpg",
        storedPath = storedPath,
        byteSize = 1024L,
        mimeType = mimeType,
    )

    @Test
    fun `member missing throws NotFoundException`() {
        whenever(memberRepo.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy { streamer.load(99L, 1L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("교인을 찾을 수 없습니다.")
    }

    @Test
    fun `photoAssetId null throws NotFoundException`() {
        val member = makeMember(photoAssetId = null)
        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))

        assertThatThrownBy { streamer.load(1L, 1L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("사진이 등록되어 있지 않습니다.")
    }

    @Test
    fun `asset missing throws NotFoundException`() {
        val member = makeMember(photoAssetId = 10L)
        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))
        whenever(postAssetRepository.findById(10L)).thenReturn(Optional.empty())

        assertThatThrownBy { streamer.load(1L, 1L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("사진 자산을 찾을 수 없습니다.")
    }

    @Test
    fun `returns StreamedPhoto for normal path`() {
        val member = makeMember(photoAssetId = 10L)
        val asset = makeAsset(id = 10L, mimeType = "image/png", storedPath = "/uploads/photo.png")
        val resource: Resource = mock()

        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))
        whenever(postAssetRepository.findById(10L)).thenReturn(Optional.of(asset))
        whenever(attachmentStorage.load("/uploads/photo.png")).thenReturn(resource)

        val result = streamer.load(1L, 1L)

        assertThat(result.resource).isSameAs(resource)
        assertThat(result.mimeType).isEqualTo("image/png")
        verify(adminAccountGuard).verify(1L)
    }
}
