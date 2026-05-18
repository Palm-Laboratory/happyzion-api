package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMemberFaith
import org.springframework.data.jpa.repository.JpaRepository

interface ChurchMemberFaithRepository : JpaRepository<ChurchMemberFaith, Long>
