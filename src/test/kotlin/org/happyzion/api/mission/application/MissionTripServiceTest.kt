package org.happyzion.api.mission.application

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.happyzion.api.mission.domain.MissionTrip
import org.happyzion.api.mission.domain.MissionTripStatus
import org.happyzion.api.mission.domain.MissionTripType
import org.happyzion.api.mission.domain.ParticipantRole
import org.happyzion.api.mission.domain.ParticipationStatus
import org.happyzion.api.mission.infrastructure.persistence.MissionTripParticipantRepository
import org.happyzion.api.mission.infrastructure.persistence.MissionTripRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

class MissionTripServiceTest {

    private val tripRepository: MissionTripRepository = mock()
    private val participantRepository: MissionTripParticipantRepository = mock()
    private val memberRepository: ChurchMemberRepository = mock()
    private val adminAccountRepository: AdminAccountRepository = mock()
    private val service = MissionTripService(
        tripRepository = tripRepository,
        participantRepository = participantRepository,
        memberRepository = memberRepository,
        adminAccountRepository = adminAccountRepository,
    )

    @Test
    fun `add participant rejects blank external name`() {
        givenActiveAdminAndTrip()

        val exception = assertThrows<IllegalArgumentException> {
            service.addParticipant(
                actorId = 1L,
                tripId = 10L,
                command = AddParticipantCommand(
                    churchMemberId = null,
                    externalName = "   ",
                    role = ParticipantRole.MEMBER,
                    participationStatus = ParticipationStatus.CONFIRMED,
                    note = null,
                ),
            )
        }

        assertThat(exception).hasMessage("교인 또는 외부인 이름 중 하나만 지정해 주세요.")
        verify(participantRepository, never()).save(any())
    }

    @Test
    fun `add participant rejects both church member and external name`() {
        givenActiveAdminAndTrip()

        val exception = assertThrows<IllegalArgumentException> {
            service.addParticipant(
                actorId = 1L,
                tripId = 10L,
                command = AddParticipantCommand(
                    churchMemberId = 5L,
                    externalName = "홍길동",
                    role = ParticipantRole.MEMBER,
                    participationStatus = ParticipationStatus.CONFIRMED,
                    note = null,
                ),
            )
        }

        assertThat(exception).hasMessage("교인 또는 외부인 이름 중 하나만 지정해 주세요.")
        verify(memberRepository, never()).findById(any())
        verify(participantRepository, never()).save(any())
    }

    private fun givenActiveAdminAndTrip() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(adminAccount()))
        whenever(tripRepository.findById(10L)).thenReturn(Optional.of(missionTrip()))
    }

    private fun adminAccount() = AdminAccount(
        id = 1L,
        username = "admin",
        displayName = "관리자",
        passwordHash = "hash",
        role = AdminAccountRole.ADMIN,
        active = true,
    )

    private fun missionTrip() = MissionTrip(
        title = "2026 필리핀 단기선교",
        country = "필리핀",
        startDate = LocalDate.of(2026, 7, 1),
        endDate = null,
        type = MissionTripType.SHORT_TERM,
        status = MissionTripStatus.PLANNED,
        leaderLabel = null,
        budget = null,
        description = null,
        coverPhotoAssetId = null,
    )
}
