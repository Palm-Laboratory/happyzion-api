package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMember
import org.happyzion.api.member.domain.ChurchMemberStatus
import org.happyzion.api.member.domain.FaithStage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChurchMemberRepository : JpaRepository<ChurchMember, Long> {

    @Query("""
        select m from ChurchMember m
        where (:nameHash is null or m.nameHash = :nameHash)
          and (:phoneHash is null or m.phoneHash = :phoneHash)
          and (:phoneLast4Hash is null or m.phoneLast4Hash = :phoneLast4Hash)
          and (:faithStage is null or m.faithStage = :faithStage)
          and (:cellLabel is null or m.cellLabel = :cellLabel)
          and m.status in :statuses
        order by m.registeredAt desc, m.id desc
    """)
    fun search(
        @Param("nameHash") nameHash: String?,
        @Param("phoneHash") phoneHash: String?,
        @Param("phoneLast4Hash") phoneLast4Hash: String?,
        @Param("faithStage") faithStage: FaithStage?,
        @Param("cellLabel") cellLabel: String?,
        @Param("statuses") statuses: Set<ChurchMemberStatus>,
        pageable: Pageable,
    ): Page<ChurchMember>
}
