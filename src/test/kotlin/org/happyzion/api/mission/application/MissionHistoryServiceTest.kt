package org.happyzion.api.mission.application

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.mission.domain.MissionEntry
import org.happyzion.api.mission.domain.MissionYear
import org.happyzion.api.mission.infrastructure.persistence.MissionEntryRepository
import org.happyzion.api.mission.infrastructure.persistence.MissionYearRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class MissionHistoryServiceTest {

    private val missionYearRepository: MissionYearRepository = mock()
    private val missionEntryRepository: MissionEntryRepository = mock()
    private val adminAccountRepository: AdminAccountRepository = mock()
    private val service = MissionHistoryService(
        missionYearRepository = missionYearRepository,
        missionEntryRepository = missionEntryRepository,
        adminAccountRepository = adminAccountRepository,
    )

    @Test
    fun `public list does not require an admin actor`() {
        whenever(missionYearRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(listOf(missionYear()))
        whenever(missionEntryRepository.findAllByYearIdInOrderBySortOrderAscIdAsc(listOf(1L))).thenReturn(
            listOf(missionEntry()),
        )

        val years = service.listYears()

        assertThat(years).hasSize(1)
        assertThat(years[0].entries).hasSize(1)
        verify(adminAccountRepository, never()).findById(any())
    }

    @Test
    fun `admin list requires an active admin actor`() {
        whenever(adminAccountRepository.findById(42L)).thenReturn(Optional.of(adminAccount(active = true)))
        whenever(missionYearRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(emptyList())

        assertThat(service.listAdminYears(42L)).isEmpty()

        verify(adminAccountRepository).findById(42L)
    }

    @Test
    fun `admin list rejects inactive admin actor`() {
        whenever(adminAccountRepository.findById(42L)).thenReturn(Optional.of(adminAccount(active = false)))

        assertThrows<ForbiddenException> {
            service.listAdminYears(42L)
        }

        verify(missionYearRepository, never()).findAllByOrderBySortOrderAscIdAsc()
    }

    @Test
    fun `admin detail rejects missing admin actor`() {
        whenever(adminAccountRepository.findById(404L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.getAdminYear(404L, 1L)
        }

        verify(missionYearRepository, never()).findById(any())
    }

    private fun adminAccount(active: Boolean) = AdminAccount(
        id = 42L,
        username = "admin",
        displayName = "관리자",
        passwordHash = "hash",
        role = AdminAccountRole.ADMIN,
        active = active,
    )

    private fun missionYear() = MissionYear(
        id = 1L,
        year = "2026",
        caption = "선교는 계속됩니다",
        tone = null,
        sortOrder = 0,
    )

    private fun missionEntry() = MissionEntry(
        id = 10L,
        yearId = 1L,
        month = null,
        place = "필리핀 팡가시난",
        isFirst = false,
        sortOrder = 0,
    )
}
