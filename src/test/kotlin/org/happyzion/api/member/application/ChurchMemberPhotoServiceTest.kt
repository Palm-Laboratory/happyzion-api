package org.happyzion.api.member.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.common.security.pii.PiiKeyRing
import org.happyzion.api.member.domain.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional

class ChurchMemberPhotoServiceTest {

    private val postAssetRepo = mock<PostAssetRepository>()
    private val service = ChurchMemberPhotoService(postAssetRepo)

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
        kind: PostAssetKind = PostAssetKind.MEMBER_PHOTO,
        detachedAt: OffsetDateTime? = OffsetDateTime.now().minusHours(1),
        uploadedByActorId: Long = 99L,
    ) = PostAsset(
        id = id,
        uploadedByActorId = uploadedByActorId,
        kind = kind,
        originalFilename = "photo.jpg",
        storedPath = "/uploads/photo.jpg",
        byteSize = 1024L,
        detachedAt = detachedAt,
    )

    @Test
    fun `replacePhoto rejects when asset missing`() {
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.replacePhoto(makeMember(), 10L, 99L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("id=10")
    }

    @Test
    fun `replacePhoto rejects when asset kind is not MEMBER_PHOTO`() {
        val asset = makeAsset(kind = PostAssetKind.INLINE_IMAGE)
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.of(asset))

        assertThatThrownBy { service.replacePhoto(makeMember(), 10L, 99L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("교인 사진이 아닙니다")
    }

    @Test
    fun `replacePhoto rejects when asset is already attached (detachedAt is null)`() {
        val asset = makeAsset(detachedAt = null)
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.of(asset))

        assertThatThrownBy { service.replacePhoto(makeMember(), 10L, 99L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("이미 사용 중인 자산입니다")
    }

    @Test
    fun `replacePhoto rejects when uploader is not the current actor`() {
        val asset = makeAsset(uploadedByActorId = 42L)
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.of(asset))

        assertThatThrownBy { service.replacePhoto(makeMember(), 10L, 99L) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessageContaining("다른 관리자가 업로드한 자산입니다")
    }

    @Test
    fun `replacePhoto attaches new asset and detaches previous`() {
        val newAsset = makeAsset(id = 10L, uploadedByActorId = 99L)
        val oldAsset = makeAsset(id = 5L, detachedAt = null, uploadedByActorId = 1L)
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.of(newAsset))
        whenever(postAssetRepo.findById(5L)).thenReturn(Optional.of(oldAsset))
        whenever(postAssetRepo.save(any())).thenAnswer { it.arguments[0] }

        val member = makeMember(photoAssetId = 5L)
        service.replacePhoto(member, 10L, 99L)

        assertThat(member.photoAssetId).isEqualTo(10L)
        assertThat(oldAsset.detachedAt).isNotNull()
        assertThat(newAsset.detachedAt).isNull()
        verify(postAssetRepo, times(2)).save(any())
    }

    @Test
    fun `replacePhoto wraps DataIntegrityViolationException to IllegalArgumentException`() {
        val asset = makeAsset(uploadedByActorId = 99L)
        whenever(postAssetRepo.findById(10L)).thenReturn(Optional.of(asset))
        whenever(postAssetRepo.save(any())).thenThrow(DataIntegrityViolationException("unique constraint"))

        assertThatThrownBy { service.replacePhoto(makeMember(), 10L, 99L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("이미 다른 교인에 연결된 사진입니다")
    }
}
