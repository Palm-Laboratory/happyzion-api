package org.happyzion.api.member.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiHasher
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChurchMemberTest {

    private val hashKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val hasher = PiiHasher(
        PiiEncryptionProperties(keys = "v1:$hashKey", activeKeyId = "v1", hashKey = hashKey)
    )
    private val normalizer = MemberSearchKeyNormalizer()

    @Test
    fun `factory creates active member with correctly computed hashes`() {
        val m = newMember(name = "  김 철수 ", phone = "010-1234-5678")

        assertThat(m.name).isEqualTo("  김 철수 ")
        assertThat(m.phone).isEqualTo("010-1234-5678")
        assertThat(m.nameHash).isEqualTo(hasher.hash("김철수"))
        assertThat(m.phoneHash).isEqualTo(hasher.hash("01012345678"))
        assertThat(m.phoneLast4Hash).isEqualTo(hasher.hash("5678"))
        assertThat(m.status).isEqualTo(ChurchMemberStatus.ACTIVE)
    }

    @Test
    fun `rename updates name and nameHash atomically`() {
        val m = newMember(name = "김철수")
        m.rename("이영희", hasher, normalizer)

        assertThat(m.name).isEqualTo("이영희")
        assertThat(m.nameHash).isEqualTo(hasher.hash("이영희"))
    }

    @Test
    fun `markRemoved sets status to REMOVED`() {
        val m = newMember()
        m.markRemoved()
        assertThat(m.status).isEqualTo(ChurchMemberStatus.REMOVED)
    }

    @Test
    fun `directly setting status to REMOVED is forbidden`() {
        val m = newMember()
        assertThatThrownBy { m.changeStatus(ChurchMemberStatus.REMOVED) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `office_appointed_at cannot be in the future`() {
        val m = newMember()
        assertThatThrownBy { m.appointOffice(ChurchMemberOffice.DEACON, LocalDate.now().plusDays(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `linkPhoto sets photoAssetId, unlinkPhoto clears it`() {
        val m = newMember()
        m.linkPhoto(99L)
        assertThat(m.photoAssetId).isEqualTo(99L)
        m.unlinkPhoto()
        assertThat(m.photoAssetId).isNull()
    }

    private fun newMember(name: String = "김철수", phone: String = "01012345678") =
        ChurchMember.create(
            name = name, phone = phone, email = null,
            birthDate = LocalDate.of(1990, 1, 1),
            birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M,
            address = "서울",
            addressDetail = null,
            job = null,
            cellLabel = null,
            status = ChurchMemberStatus.ACTIVE,
            faithStage = null,
            office = ChurchMemberOffice.LAY,
            officeAppointedAt = null,
            registeredAt = LocalDate.of(2024, 1, 1),
            memo = null,
            hasher = hasher,
            normalizer = normalizer,
        )
}
