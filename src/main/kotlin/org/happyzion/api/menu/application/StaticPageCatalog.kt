package org.happyzion.api.menu.application

data class StaticPageRoute(
    val key: String,
    val label: String,
    val path: String,
)

object StaticPageCatalog {
    private val staticRoutes = listOf(
        StaticPageRoute("about.greeting", "교회 소개 / 인사말/비전", "/about/greeting"),
        StaticPageRoute("about.church-story", "교회 소개 / 교회 이야기", "/about/church-story"),
        StaticPageRoute("about.revival-organization", "교회 소개 / 부흥 조직도", "/about/revival-organization"),
        StaticPageRoute("about.mission-history", "교회 소개 / 선교 이력", "/about/mission-history"),
        StaticPageRoute("about.location", "교회 소개 / 오시는 길", "/about/location"),
        StaticPageRoute("about.online-giving", "교회 소개 / 온라인 헌금", "/about/online-giving"),
        StaticPageRoute("about.service-times", "교회 소개 / 예배시간", "/about/service-times"),
    )
    private val staticRoutesByKey = staticRoutes.associateBy { it.key }

    fun resolveRoute(staticPageKey: String?): String? = staticPageKey?.let(staticRoutesByKey::get)?.path

    fun allKeys(): Set<String> = staticRoutesByKey.keys

    fun allRoutes(): List<StaticPageRoute> = staticRoutes
}
