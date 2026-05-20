package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiEncryptor
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.common.security.pii.PiiKeyRing
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class ChurchMemberAuditWriterTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val props = PiiEncryptionProperties("v1:$key", "v1", key)
    private val encryptor = PiiEncryptor(PiiKeyRing(props))
    private val mapper = ObjectMapper()
    private val repo = mock<ChurchMemberAuditLogRepository>()
    private val writer = ChurchMemberAuditWriter(repo, encryptor, mapper)

    @Test
    fun `recordUpdate captures only changed fields`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val before = snapshot(name = "김철수", phone = "01012345678")
        val after  = snapshot(name = "이영희", phone = "01012345678")

        writer.recordUpdate(memberId = 1, actorId = 7, before = before, after = after)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        val log = captor.firstValue
        assertThat(log.action).isEqualTo(AuditAction.UPDATE)
        assertThat(log.actorId).isEqualTo(7)
        val decrypted = encryptor.decrypt(log.diffJson!!)
        assertThat(decrypted).contains("\"name\":[\"김철수\",\"이영희\"]")
        assertThat(decrypted).doesNotContain("phone")
    }

    @Test
    fun `recordCreate stores after snapshot fields as new`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val after = snapshot()
        writer.recordCreate(memberId = 1, actorId = 7, after = after)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.CREATE)
    }

    @Test
    fun `recordDelete stores before snapshot`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val before = snapshot()
        writer.recordDelete(memberId = 1, actorId = 7, before = before)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.DELETE)
    }

    private fun snapshot(name: String = "김철수", phone: String = "01012345678") =
        ChurchMemberSnapshot(
            name = name, phone = phone, email = null,
            birthDate = LocalDate.of(1990,1,1), birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M, address = "서울", addressDetail = null, job = null,
            memo = null, photoAssetId = null, cellLabel = null,
            status = ChurchMemberStatus.ACTIVE, faithStage = null,
            office = ChurchMemberOffice.LAY, officeAppointedAt = null,
            registeredAt = LocalDate.of(2024,1,1),
        )
}
