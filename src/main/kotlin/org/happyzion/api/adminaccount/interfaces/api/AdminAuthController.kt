package org.happyzion.api.adminaccount.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.adminaccount.application.AdminAccountAuthService
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountAuthenticateRequest
import org.happyzion.api.adminaccount.interfaces.dto.AdminAuthenticatedAccountDto
import org.happyzion.api.adminaccount.interfaces.dto.toDto
import org.happyzion.api.common.security.AdminKeyRequired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(
    private val adminAccountAuthService: AdminAccountAuthService,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AdminAccountAuthenticateRequest,
    ): AdminAuthenticatedAccountDto =
        adminAccountAuthService.authenticate(
            username = request.username,
            password = request.password,
        ).toDto()

    @AdminKeyRequired
    @GetMapping("/me")
    fun me(
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
    ): AdminAuthenticatedAccountDto =
        adminAccountAuthService.getCurrentAccount(actorId).toDto()
}
