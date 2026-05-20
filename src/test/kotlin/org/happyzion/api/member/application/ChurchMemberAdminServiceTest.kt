package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiEncryptor
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.common.security.pii.PiiKeyRing
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberFaithRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.util.Optional

class ChurchMemberAdminServiceTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val props = PiiEncryptionProperties("v1:$key", "v1", key)
    private val hasher = PiiHasher(props)
    private val encryptor = PiiEncryptor(PiiKeyRing(props))
    private val normalizer = MemberSearchKeyNormalizer()
    private val mapper = ObjectMapper()

    private val memberRepo = mock<ChurchMemberRepository>()
    private val faithRepo = mock<ChurchMemberFaithRepository>()
    private val auditRepo = mock<ChurchMemberAuditLogRepository>()
    private val adminGuard = mock<AdminAccountGuard>()
    private val photoService = mock<ChurchMemberPhotoService>()
    private val auditWriter = ChurchMemberAuditWriter(auditRepo, encryptor, mapper)

    private val service = ChurchMemberAdminService(
        memberRepo = memberRepo,
        faithRepo = faithRepo,
        auditRepo = auditRepo,
        auditWriter = auditWriter,
        photoService = photoService,
        adminAccountGuard = adminGuard,
        hasher = hasher,
        normalizer = normalizer,
    )

    @BeforeEach
    fun setUp() {
        // AdminAccountGuard does nothing by default (no exception = passes)
        doNothing().whenever(adminGuard).verify(any())
        whenever(auditRepo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }
    }

    private fun makeCmd(
        name: String = "김철수",
        phone: String = "01012345678",
        faith: ChurchMemberFaithSaveCommand? = null,
    ) = ChurchMemberSaveCommand(
        name = name, phone = phone, email = null,
        birthDate = LocalDate.of(1990, 1, 1), birthCalendar = BirthCalendar.SOLAR,
        sex = Sex.M, address = "서울", addressDetail = null, job = null,
        cellLabel = null, status = ChurchMemberStatus.ACTIVE, faithStage = null,
        office = ChurchMemberOffice.LAY, officeAppointedAt = null,
        registeredAt = LocalDate.of(2024, 1, 1), memo = null,
        faith = faith,
    )

    private fun makeMember(name: String = "김철수"): ChurchMember =
        ChurchMember.create(
            name = name, phone = "01012345678", email = null,
            birthDate = LocalDate.of(1990, 1, 1), birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M, address = "서울", addressDetail = null, job = null,
            cellLabel = null, status = ChurchMemberStatus.ACTIVE, faithStage = null,
            office = ChurchMemberOffice.LAY, officeAppointedAt = null,
            registeredAt = LocalDate.of(2024, 1, 1), memo = null,
            hasher = hasher, normalizer = normalizer,
        )

    // ───────── Task 18 Tests ─────────

    @Test
    fun `createMember persists with hashes, links faith if present, records CREATE audit`() {
        val cmd = makeCmd(faith = ChurchMemberFaithSaveCommand(
            confessDate = LocalDate.of(2010, 3, 1), learningDate = null,
            baptismDate = null, baptismPlace = null, baptismOfficiant = null,
            confirmationDate = null, previousChurch = null, transferredInAt = null,
        ))
        val member = makeMember()
        val faith = ChurchMemberFaith(
            churchMemberId = 0L,
            confessDate = LocalDate.of(2010, 3, 1), learningDate = null,
            baptismDate = null, baptismPlace = null, baptismOfficiant = null,
            confirmationDate = null, previousChurch = null, transferredInAt = null,
        )
        whenever(memberRepo.save(any<ChurchMember>())).thenReturn(member)
        whenever(faithRepo.save(any<ChurchMemberFaith>())).thenReturn(faith)

        val detail = service.createMember(cmd, actorId = 7L)

        verify(memberRepo).save(any())
        verify(faithRepo).save(any())
        // Verify CREATE audit was recorded
        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(auditRepo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.CREATE)
        assertThat(captor.firstValue.actorId).isEqualTo(7L)

        assertThat(detail.faith).isNotNull()
        assertThat(detail.faith!!.confessDate).isEqualTo(LocalDate.of(2010, 3, 1))
    }

    @Test
    fun `createMember calls AdminAccountGuard before any writes`() {
        val cmd = makeCmd()
        val member = makeMember()
        whenever(memberRepo.save(any<ChurchMember>())).thenReturn(member)

        val guardOrder = mutableListOf<String>()
        doAnswer { guardOrder.add("guard"); Unit }.whenever(adminGuard).verify(7L)
        whenever(memberRepo.save(any<ChurchMember>())).thenAnswer { guardOrder.add("save"); member }

        service.createMember(cmd, actorId = 7L)

        assertThat(guardOrder.first()).isEqualTo("guard")
    }

    @Test
    fun `updateMember computes diff between snapshots and records UPDATE`() {
        val member = makeMember(name = "김철수")
        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))
        whenever(faithRepo.findById(1L)).thenReturn(Optional.empty())

        val cmd = makeCmd(name = "이영희")
        service.updateMember(id = 1L, cmd = cmd, actorId = 7L)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(auditRepo).save(captor.capture())
        val log = captor.firstValue
        assertThat(log.action).isEqualTo(AuditAction.UPDATE)
        val decrypted = encryptor.decrypt(log.diffJson!!)
        assertThat(decrypted).contains("name")
    }

    @Test
    fun `softDeleteMember marks REMOVED and records DELETE audit`() {
        val member = makeMember()
        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))

        service.softDeleteMember(id = 1L, actorId = 7L)

        assertThat(member.status).isEqualTo(ChurchMemberStatus.REMOVED)
        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(auditRepo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.DELETE)
    }

    @Test
    fun `getMember returns plaintext detail including faith`() {
        val member = makeMember()
        val faith = ChurchMemberFaith(
            churchMemberId = 0L,
            confessDate = LocalDate.of(2010, 3, 1), learningDate = null,
            baptismDate = null, baptismPlace = null, baptismOfficiant = null,
            confirmationDate = null, previousChurch = null, transferredInAt = null,
        )
        whenever(memberRepo.findById(1L)).thenReturn(Optional.of(member))
        whenever(faithRepo.findById(1L)).thenReturn(Optional.of(faith))

        val detail = service.getMember(id = 1L, actorId = 7L)

        assertThat(detail.name).isEqualTo("김철수")
        assertThat(detail.faith).isNotNull()
        assertThat(detail.faith!!.confessDate).isEqualTo(LocalDate.of(2010, 3, 1))
    }

    // ───────── Task 19 Tests: listMembers search ─────────

    private fun emptyPage(): Page<ChurchMember> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)

    private fun stubEmptySearch() {
        whenever(memberRepo.search(
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            any<Set<ChurchMemberStatus>>(), any<Pageable>()
        )).thenReturn(emptyPage())
    }

    @Test
    fun `listMembers with name search produces nameHash via normalizer`() {
        stubEmptySearch()

        val filter = ChurchMemberSearchFilter(
            name = "김철수", phone = null,
            statuses = ChurchMemberStatus.ACTIVE_SET,
            faithStage = null, cellLabel = null, includeInactive = false,
        )
        service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        val expectedNameHash = hasher.hash(normalizer.forNameQuery("김철수")!!)
        verify(memberRepo).search(
            nameHash = eq(expectedNameHash),
            phoneHash = isNull(),
            phoneLast4Hash = isNull(),
            faithStage = isNull(),
            cellLabel = isNull(),
            statuses = eq(ChurchMemberStatus.ACTIVE_SET),
            pageable = any(),
        )
    }

    @Test
    fun `listMembers with phone 4 digits uses phone_last4_hash filter only`() {
        stubEmptySearch()

        val filter = ChurchMemberSearchFilter(
            name = null, phone = "5678",
            statuses = ChurchMemberStatus.ACTIVE_SET,
            faithStage = null, cellLabel = null, includeInactive = false,
        )
        service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        val expectedLast4Hash = hasher.hash("5678")
        verify(memberRepo).search(
            nameHash = isNull(),
            phoneHash = isNull(),
            phoneLast4Hash = eq(expectedLast4Hash),
            faithStage = isNull(),
            cellLabel = isNull(),
            statuses = eq(ChurchMemberStatus.ACTIVE_SET),
            pageable = any(),
        )
    }

    @Test
    fun `listMembers with phone 11 digits uses phone_hash filter`() {
        stubEmptySearch()

        val filter = ChurchMemberSearchFilter(
            name = null, phone = "01012345678",
            statuses = ChurchMemberStatus.ACTIVE_SET,
            faithStage = null, cellLabel = null, includeInactive = false,
        )
        service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        val expectedPhoneHash = hasher.hash("01012345678")
        verify(memberRepo).search(
            nameHash = isNull(),
            phoneHash = eq(expectedPhoneHash),
            phoneLast4Hash = isNull(),
            faithStage = isNull(),
            cellLabel = isNull(),
            statuses = eq(ChurchMemberStatus.ACTIVE_SET),
            pageable = any(),
        )
    }

    @Test
    fun `listMembers default filters out REMOVED and DECEASED`() {
        stubEmptySearch()

        val filter = ChurchMemberSearchFilter(
            name = null, phone = null,
            statuses = ChurchMemberStatus.ACTIVE_SET,
            faithStage = null, cellLabel = null, includeInactive = false,
        )
        service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        val statusCaptor = argumentCaptor<Set<ChurchMemberStatus>>()
        verify(memberRepo).search(
            nameHash = isNull(), phoneHash = isNull(), phoneLast4Hash = isNull(),
            faithStage = isNull(), cellLabel = isNull(),
            statuses = statusCaptor.capture(), pageable = any(),
        )
        val usedStatuses = statusCaptor.firstValue
        assertThat(usedStatuses).doesNotContain(ChurchMemberStatus.REMOVED)
        assertThat(usedStatuses).doesNotContain(ChurchMemberStatus.DECEASED)
    }

    @Test
    fun `listMembers includeInactive=true uses all statuses`() {
        stubEmptySearch()

        val filter = ChurchMemberSearchFilter(
            name = null, phone = null,
            statuses = emptySet(),
            faithStage = null, cellLabel = null, includeInactive = true,
        )
        service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        val statusCaptor = argumentCaptor<Set<ChurchMemberStatus>>()
        verify(memberRepo).search(
            nameHash = isNull(), phoneHash = isNull(), phoneLast4Hash = isNull(),
            faithStage = isNull(), cellLabel = isNull(),
            statuses = statusCaptor.capture(), pageable = any(),
        )
        val usedStatuses = statusCaptor.firstValue
        assertThat(usedStatuses).containsAll(ChurchMemberStatus.values().toList())
    }

    @Test
    fun `listMembers with ambiguous phone length returns empty result without calling repo`() {
        val filter = ChurchMemberSearchFilter(
            name = null, phone = "12345",   // 5 digits — ambiguous, not 4 and not >= 9
            statuses = ChurchMemberStatus.ACTIVE_SET,
            faithStage = null, cellLabel = null, includeInactive = false,
        )
        val result = service.listMembers(filter, actorId = 7L, page = 0, size = 10)

        assertThat(result.items).isEmpty()
        assertThat(result.hasNext).isFalse()
        verify(memberRepo, never()).search(any(), any(), any(), any(), any(), any(), any())
    }
}
