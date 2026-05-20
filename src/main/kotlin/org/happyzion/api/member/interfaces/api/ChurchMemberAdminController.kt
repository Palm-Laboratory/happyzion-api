package org.happyzion.api.member.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.member.application.*
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.interfaces.dto.*
import org.springframework.web.bind.annotation.*

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members")
class ChurchMemberAdminController(
    private val service: ChurchMemberAdminService,
) {
    @GetMapping
    fun list(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) faithStage: FaithStage?,
        @RequestParam(required = false) cellLabel: String?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ChurchMemberPageResponse {
        val statuses = status?.split(",")?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.map(ChurchMemberStatus::valueOf)?.toSet() ?: emptySet()
        val result = service.listMembers(
            ChurchMemberSearchFilter(name, phone, statuses, faithStage, cellLabel, includeInactive),
            actorId, page, size,
        )
        return ChurchMemberPageResponse(
            items = result.items.map { ChurchMemberSummaryResponse(it.id, it.name, it.phone, it.status, it.cellLabel, it.registeredAt) },
            hasNext = result.hasNext,
            total = result.total,
        )
    }

    @PostMapping
    fun create(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: ChurchMemberSaveRequest,
    ): ChurchMemberDetailResponse = service.createMember(request.toCommand(), actorId).toResponse()

    @GetMapping("/{id}")
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): ChurchMemberDetailResponse = service.getMember(id, actorId).toResponse()

    @PutMapping("/{id}")
    fun update(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ChurchMemberSaveRequest,
    ): ChurchMemberDetailResponse = service.updateMember(id, request.toCommand(), actorId).toResponse()

    @DeleteMapping("/{id}")
    fun delete(@RequestAttribute("adminAccountId") actorId: Long, @PathVariable id: Long) {
        service.softDeleteMember(id, actorId)
    }

    @PostMapping("/{id}/photo")
    fun attachPhoto(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ChurchMemberPhotoAttachRequest,
    ): ChurchMemberDetailResponse = service.attachPhoto(id, request.assetId, actorId).toResponse()

    @DeleteMapping("/{id}/photo")
    fun detachPhoto(@RequestAttribute("adminAccountId") actorId: Long, @PathVariable id: Long): ChurchMemberDetailResponse =
        service.detachPhoto(id, actorId).toResponse()

    @GetMapping("/{id}/audit-logs")
    fun auditLogs(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ChurchMemberAuditPageResponse {
        val p = service.listAuditLogs(id, actorId, page, size)
        return ChurchMemberAuditPageResponse(
            items = p.items.map { ChurchMemberAuditEntryResponse(it.id, it.action, it.actorId, it.diffJson, it.createdAt) },
            hasNext = p.hasNext,
        )
    }

    private fun ChurchMemberDetail.toResponse() = ChurchMemberDetailResponse(
        id, name, phone, email, birthDate, birthCalendar, sex, address, addressDetail,
        job, memo, photoAssetId, cellLabel, status, faithStage, office, officeAppointedAt,
        registeredAt,
        faith?.let { ChurchMemberFaithDetailResponse(it.confessDate, it.learningDate, it.baptismDate, it.baptismPlace, it.baptismOfficiant, it.confirmationDate, it.previousChurch, it.transferredInAt) },
        createdAt, updatedAt,
    )
}
