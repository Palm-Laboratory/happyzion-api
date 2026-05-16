package org.happyzion.api.menu.interfaces.api

import org.happyzion.api.common.security.AdminKeyRequired
import org.happyzion.api.menu.application.MenuManagementService
import org.happyzion.api.menu.application.StaticPageCatalog
import org.happyzion.api.menu.interfaces.dto.AdminStaticPagesResponse
import org.happyzion.api.menu.interfaces.dto.ReplaceMenuTreeRequest
import org.happyzion.api.menu.interfaces.dto.toCommand
import org.happyzion.api.menu.interfaces.dto.toDto
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@AdminKeyRequired
@RestController
@RequestMapping("/api/v1/admin/menu")
class MenuAdminController(
    private val menuManagementService: MenuManagementService,
) {
    @GetMapping
    fun getMenuTree(
        @RequestAttribute("adminAccountId") actorId: Long,
    ) = menuManagementService.getAdminSnapshot(actorId).toDto()

    @GetMapping("/static-pages")
    fun getStaticPages() = AdminStaticPagesResponse(pages = StaticPageCatalog.allRoutes().map { it.toDto() })

    @PutMapping("/tree")
    fun replaceTree(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestBody request: ReplaceMenuTreeRequest,
    ) = menuManagementService.replaceTree(actorId, request.toCommand()).toDto()

    @DeleteMapping("/{id}")
    fun deleteMenu(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ) {
        menuManagementService.deleteMenuItem(actorId, id)
    }
}
