package org.happyzion.api.menu.interfaces.api

import org.happyzion.api.menu.application.PublicMenuService
import org.happyzion.api.menu.interfaces.dto.toDto
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public")
class PublicMenuController(
    private val publicMenuService: PublicMenuService,
) {
    @GetMapping("/menu")
    fun getMenu() =
        cacheableMenuResponse(publicMenuService.getNavigation().toDto())

    @GetMapping("/menu/resolve")
    fun resolveMenuPath(
        @RequestParam path: String,
    ) = cacheableMenuResponse(publicMenuService.resolveMenuPath(path).toDto())

    @GetMapping("/videos")
    fun getVideoDetailByPath(
        @RequestParam path: String,
    ) = publicMenuService.getVideoDetailByPath(path).toDto()

    private fun <T> cacheableMenuResponse(body: T): ResponseEntity<T> =
        ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, PUBLIC_MENU_CACHE_CONTROL)
            .body(body)

    private companion object {
        const val PUBLIC_MENU_CACHE_CONTROL = "public, max-age=60, stale-while-revalidate=300"
    }
}
