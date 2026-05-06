package org.happyzion.api.menu.application

object MenuRouteRegistry {
    private val staticRoutes = emptyMap<String, String>()

    fun resolveStaticRoute(staticPageKey: String?): String? = staticPageKey?.let(staticRoutes::get)

    fun allStaticPageKeys(): Set<String> = staticRoutes.keys
}
