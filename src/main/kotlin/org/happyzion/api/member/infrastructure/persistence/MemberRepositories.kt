package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.AttendanceRecord
import org.happyzion.api.member.domain.AttendanceServiceDate
import org.happyzion.api.member.domain.FaithStage
import org.happyzion.api.member.domain.Member
import org.happyzion.api.member.domain.MemberEventLog
import org.happyzion.api.member.domain.MemberFaith
import org.happyzion.api.member.domain.MemberFamily
import org.happyzion.api.member.domain.MemberService
import org.happyzion.api.member.domain.MemberStatus
import org.happyzion.api.member.domain.MemberTag
import org.happyzion.api.member.domain.MemberTraining
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface MemberRepository : JpaRepository<Member, Long> {
    @Query(
        value = """
            select m
            from Member m
            where (:status is null or m.status = :status)
              and (:stage is null or m.faithStage = :stage)
              and (:cellId is null or m.cellId = :cellId)
            order by m.registeredAt desc, m.id desc
        """,
        countQuery = """
            select count(m)
            from Member m
            where (:status is null or m.status = :status)
              and (:stage is null or m.faithStage = :stage)
              and (:cellId is null or m.cellId = :cellId)
        """,
    )
    fun findAdminMembersWithoutQuery(
        @Param("status") status: MemberStatus?,
        @Param("stage") stage: FaithStage?,
        @Param("cellId") cellId: String?,
        pageable: Pageable,
    ): Page<Member>

    @Query(
        value = """
            select m
            from Member m
            where (lower(m.name) like :searchQuery
                or m.phone like :phoneQuery
                or lower(m.address) like :searchQuery
                or lower(coalesce(m.addressDetail, '')) like :searchQuery)
              and (:status is null or m.status = :status)
              and (:stage is null or m.faithStage = :stage)
              and (:cellId is null or m.cellId = :cellId)
            order by m.registeredAt desc, m.id desc
        """,
        countQuery = """
            select count(m)
            from Member m
            where (lower(m.name) like :searchQuery
                or m.phone like :phoneQuery
                or lower(m.address) like :searchQuery
                or lower(coalesce(m.addressDetail, '')) like :searchQuery)
              and (:status is null or m.status = :status)
              and (:stage is null or m.faithStage = :stage)
              and (:cellId is null or m.cellId = :cellId)
        """,
    )
    fun findAdminMembersWithQuery(
        @Param("searchQuery") searchQuery: String,
        @Param("phoneQuery") phoneQuery: String,
        @Param("status") status: MemberStatus?,
        @Param("stage") stage: FaithStage?,
        @Param("cellId") cellId: String?,
        pageable: Pageable,
    ): Page<Member>
}

interface MemberFaithRepository : JpaRepository<MemberFaith, Long>

interface MemberFamilyRepository : JpaRepository<MemberFamily, Long> {
    fun findAllByMemberIdOrderByIsHeadDescIdAsc(memberId: Long): List<MemberFamily>
}

interface MemberServiceRepository : JpaRepository<MemberService, Long> {
    fun findAllByMemberIdOrderByEndedAtAscStartedAtDescIdDesc(memberId: Long): List<MemberService>
}

interface MemberTrainingRepository : JpaRepository<MemberTraining, Long> {
    fun findAllByMemberIdOrderByCompletedAtDescIdDesc(memberId: Long): List<MemberTraining>
}

interface MemberTagRepository : JpaRepository<MemberTag, Long> {
    fun findAllByMemberIdOrderByTagAsc(memberId: Long): List<MemberTag>
}

interface MemberEventLogRepository : JpaRepository<MemberEventLog, Long> {
    fun findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId: Long): List<MemberEventLog>
}

interface AttendanceServiceDateRepository : JpaRepository<AttendanceServiceDate, Long> {
    fun findTop4ByOrderByServiceDateDescIdDesc(): List<AttendanceServiceDate>
    fun findAllByServiceDateBetweenOrderByServiceDateDescIdDesc(from: LocalDate, to: LocalDate): List<AttendanceServiceDate>

    @Query(
        """
            select s
            from AttendanceServiceDate s
            where (:fromDate is null or s.serviceDate >= :fromDate)
              and (:toDate is null or s.serviceDate <= :toDate)
            order by s.serviceDate desc, s.id desc
        """
    )
    fun findAllInRange(
        @Param("fromDate") fromDate: LocalDate?,
        @Param("toDate") toDate: LocalDate?,
    ): List<AttendanceServiceDate>
}

interface AttendanceRecordRepository : JpaRepository<AttendanceRecord, Long> {
    fun findAllByMemberIdAndServiceDateIdIn(memberId: Long, serviceDateIds: Collection<Long>): List<AttendanceRecord>
}
