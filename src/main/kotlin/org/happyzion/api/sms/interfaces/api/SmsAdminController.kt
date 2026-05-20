package org.happyzion.api.sms.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.sms.application.SmsHistoryService
import org.happyzion.api.sms.application.SmsSendService
import org.happyzion.api.sms.interfaces.dto.SmsBulkSendRequest
import org.happyzion.api.sms.interfaces.dto.SmsSendRequest
import org.happyzion.api.sms.interfaces.dto.SmsSendResponse
import org.happyzion.api.sms.interfaces.dto.SmsLogDetailResponse
import org.happyzion.api.sms.interfaces.dto.SmsLogPageResponse
import org.happyzion.api.sms.interfaces.dto.toCommand
import org.happyzion.api.sms.interfaces.dto.toResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/sms")
class SmsAdminController(
    private val smsSendService: SmsSendService,
    private val smsHistoryService: SmsHistoryService,
) {

    @PostMapping("/send")
    fun send(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: SmsSendRequest,
    ): SmsSendResponse = smsSendService.send(request.toCommand(actorId)).toResponse()

    @PostMapping("/send-bulk")
    fun sendBulk(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: SmsBulkSendRequest,
    ): SmsSendResponse = smsSendService.sendBulk(request.toCommand(actorId)).toResponse()

    @GetMapping
    fun listLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): SmsLogPageResponse = smsHistoryService.listLogs(page, pageSize).toResponse()

    @GetMapping("/{smsLogId}")
    fun getDetail(
        @PathVariable smsLogId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): SmsLogDetailResponse = smsHistoryService.getDetail(smsLogId, page, pageSize).toResponse()
}
