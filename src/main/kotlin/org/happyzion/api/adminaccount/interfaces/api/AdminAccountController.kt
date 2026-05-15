package org.happyzion.api.adminaccount.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.adminaccount.application.AdminAccountManagementService
import org.happyzion.api.adminaccount.application.CreateAdminAccountCommand
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountCreateRequest
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountUpdateRequest
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountsResponse
import org.happyzion.api.adminaccount.interfaces.dto.toDto
import org.happyzion.api.common.security.AdminKeyRequired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@AdminKeyRequired
@RestController
@RequestMapping("/api/v1/admin/accounts")
class AdminAccountController(
    private val adminAccountManagementService: AdminAccountManagementService,
) {
    @GetMapping("/{id}")
    fun getAccount(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
    ) = adminAccountManagementService.getAccount(actorId, id).toDto()

    @GetMapping
    fun getAccounts(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
    ): AdminAccountsResponse =
        AdminAccountsResponse(
            accounts = adminAccountManagementService.getAccounts(actorId).map { it.toDto() },
        )

    @PostMapping
    fun createAccount(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @Valid @RequestBody request: AdminAccountCreateRequest,
    ) = adminAccountManagementService.createAdminAccount(
        actorId = actorId,
        CreateAdminAccountCommand(
            username = request.username,
            displayName = request.displayName,
            password = request.password,
        ),
    ).toDto()

    @PutMapping("/{id}")
    fun updateAccount(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: AdminAccountUpdateRequest,
    ) = adminAccountManagementService.updateAdminAccount(
        actorId = actorId,
        accountId = id,
        command = org.happyzion.api.adminaccount.application.UpdateAdminAccountCommand(
            username = request.username,
            displayName = request.displayName,
            role = request.role,
            active = request.active,
            password = request.password,
        ),
    ).toDto()

    @DeleteMapping("/{id}")
    fun deleteAccount(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
    ) {
        adminAccountManagementService.deleteAdminAccount(actorId, id)
    }
}
