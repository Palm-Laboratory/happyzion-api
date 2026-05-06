package org.happyzion.api.menu.infrastructure.persistence

import org.happyzion.api.menu.domain.MenuRevision
import org.springframework.data.jpa.repository.JpaRepository

interface MenuRevisionRepository : JpaRepository<MenuRevision, Long>
