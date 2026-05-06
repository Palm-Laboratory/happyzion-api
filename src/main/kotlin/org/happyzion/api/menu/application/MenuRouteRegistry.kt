package org.happyzion.api.menu.application

object MenuRouteRegistry {
    private val staticRoutes = mapOf(
        "about.greeting" to "/about/greeting",
        "about.pastor" to "/about/pastor",
        "about.service-times" to "/about/service-times",
        "about.location" to "/about/location",
        "about.history" to "/about/history",
        "about.giving" to "/about/giving",
        "newcomer.guide" to "/newcomer/guide",
        "newcomer.care" to "/newcomer/care",
        "newcomer.disciples" to "/newcomer/disciples",
        "commission.summary" to "/commission/summary",
        "commission.nextgen" to "/commission/nextgen",
        "commission.culture" to "/commission/culture",
        "commission.ethnic" to "/commission/ethnic",
    )

    fun resolveStaticRoute(staticPageKey: String?): String? = staticPageKey?.let(staticRoutes::get)

    fun allStaticPageKeys(): Set<String> = staticRoutes.keys
}
