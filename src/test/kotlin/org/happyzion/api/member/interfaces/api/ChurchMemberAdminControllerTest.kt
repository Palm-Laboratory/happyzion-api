package org.happyzion.api.member.interfaces.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.application.*
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.interfaces.dto.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.OffsetDateTime

class ChurchMemberAdminControllerTest {

    private val service: ChurchMemberAdminService = mock()
    private val controller = ChurchMemberAdminController(service)

    private val now = OffsetDateTime.parse("2026-05-18T10:00:00+09:00")
    private val today = LocalDate.of(2026, 5, 18)

    private fun sampleDetail(id: Long = 1L) = ChurchMemberDetail(
        id = id,
        name = "김철수",
        phone = "01012345678",
        email = null,
        birthDate = LocalDate.of(1990, 1, 1),
        birthCalendar = BirthCalendar.SOLAR,
        sex = Sex.M,
        address = "서울시 강남구",
        addressDetail = null,
        job = null,
        memo = null,
        photoAssetId = null,
        cellLabel = "1셀",
        status = ChurchMemberStatus.ACTIVE,
        faithStage = FaithStage.GROWING,
        office = ChurchMemberOffice.LAY,
        officeAppointedAt = null,
        registeredAt = today,
        faith = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun sampleSaveRequest() = ChurchMemberSaveRequest(
        name = "김철수",
        sex = Sex.M,
        birthDate = LocalDate.of(1990, 1, 1),
        birthCalendar = BirthCalendar.SOLAR,
        phone = "01012345678",
        email = null,
        address = "서울시 강남구",
        addressDetail = null,
        job = null,
        cellLabel = "1셀",
        status = ChurchMemberStatus.ACTIVE,
        faithStage = FaithStage.GROWING,
        office = ChurchMemberOffice.LAY,
        officeAppointedAt = null,
        registeredAt = today,
        memo = null,
        faith = null,
    )

    @Test
    fun `list returns page response from service`() {
        val summary = ChurchMemberSummary(
            id = 1L, name = "김철수", phone = "01012345678",
            status = ChurchMemberStatus.ACTIVE, cellLabel = "1셀", registeredAt = today,
        )
        whenever(
            service.listMembers(any(), eq(42L), eq(0), eq(20))
        ).thenReturn(ChurchMemberPage(items = listOf(summary), hasNext = false, total = 1L))

        val response = controller.list(
            actorId = 42L,
            name = null, phone = null, status = null,
            faithStage = null, cellLabel = null,
            includeInactive = false, page = 0, size = 20,
        )

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].id).isEqualTo(1L)
        assertThat(response.items[0].name).isEqualTo("김철수")
        assertThat(response.items[0].status).isEqualTo(ChurchMemberStatus.ACTIVE)
        assertThat(response.hasNext).isFalse()
    }

    @Test
    fun `create returns detail response from service`() {
        val detail = sampleDetail()
        whenever(service.createMember(any(), eq(42L))).thenReturn(detail)

        val response = controller.create(actorId = 42L, request = sampleSaveRequest())

        assertThat(response.id).isEqualTo(1L)
        assertThat(response.name).isEqualTo("김철수")
        assertThat(response.status).isEqualTo(ChurchMemberStatus.ACTIVE)
        assertThat(response.office).isEqualTo(ChurchMemberOffice.LAY)
        assertThat(response.faith).isNull()
        verify(service).createMember(any(), eq(42L))
    }

    @Test
    fun `get returns detail response from service`() {
        val detail = sampleDetail(id = 7L)
        whenever(service.getMember(7L, 42L)).thenReturn(detail)

        val response = controller.get(actorId = 42L, id = 7L)

        assertThat(response.id).isEqualTo(7L)
        assertThat(response.phone).isEqualTo("01012345678")
        assertThat(response.birthCalendar).isEqualTo(BirthCalendar.SOLAR)
        assertThat(response.sex).isEqualTo(Sex.M)
    }

    @Test
    fun `delete calls softDeleteMember on service`() {
        controller.delete(actorId = 42L, id = 5L)

        verify(service).softDeleteMember(5L, 42L)
    }

    @Test
    fun `get propagates NotFoundException`() {
        whenever(service.getMember(99L, 42L)).thenThrow(NotFoundException("교인을 찾을 수 없습니다."))

        assertThatThrownBy { controller.get(actorId = 42L, id = 99L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("교인을 찾을 수 없습니다.")
    }
}
