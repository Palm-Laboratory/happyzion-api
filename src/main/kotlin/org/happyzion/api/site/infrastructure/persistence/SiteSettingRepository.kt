package org.happyzion.api.site.infrastructure.persistence

import org.happyzion.api.site.domain.SiteSetting
import org.springframework.data.jpa.repository.JpaRepository

interface SiteSettingRepository : JpaRepository<SiteSetting, String>
