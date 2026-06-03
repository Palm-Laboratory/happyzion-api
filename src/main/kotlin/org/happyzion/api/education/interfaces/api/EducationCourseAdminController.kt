package org.happyzion.api.education.interfaces.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.education.application.*
import org.happyzion.api.education.domain.EducationCategory
import org.happyzion.api.education.domain.EducationCourseStatus
import org.happyzion.api.education.domain.EnrollmentRole
import org.happyzion.api.education.domain.EnrollmentStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.OffsetDateTime

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/education-courses")
class EducationCourseAdminController(
    private val educationCourseService: EducationCourseService,
) {
    @GetMapping
    fun list(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) status: EducationCourseStatus?,
        @RequestParam(required = false) category: EducationCategory?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): EducationCourseListResponse =
        educationCourseService.listCourses(actorId, year, status, category, page, size).toResponse()

    @GetMapping("/{id}")
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): EducationCourseDetailResponse =
        educationCourseService.getCourse(actorId, id).toDetailResponse()

    @PostMapping
    fun create(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: EducationCourseSaveRequest,
    ): EducationCourseDetailResponse =
        educationCourseService.createCourse(actorId, request.toCreateCommand()).toDetailResponse()

    @PutMapping("/{id}")
    fun update(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: EducationCourseSaveRequest,
    ): EducationCourseDetailResponse =
        educationCourseService.updateCourse(actorId, id, request.toUpdateCommand()).toDetailResponse()

    @DeleteMapping("/{id}")
    fun delete(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ) = educationCourseService.deleteCourse(actorId, id)

    @PostMapping("/{id}/enrollments")
    fun addEnrollment(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: AddEnrollmentRequest,
    ): EnrollmentResponse =
        educationCourseService.addEnrollment(actorId, id, request.toCommand()).toResponse()

    @PatchMapping("/{id}/enrollments/{enrollmentId}")
    fun updateEnrollment(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @PathVariable enrollmentId: Long,
        @Valid @RequestBody request: UpdateEnrollmentRequest,
    ): EnrollmentResponse =
        educationCourseService.updateEnrollment(actorId, id, enrollmentId, request.toCommand()).toResponse()

    @DeleteMapping("/{id}/enrollments/{enrollmentId}")
    fun removeEnrollment(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @PathVariable enrollmentId: Long,
    ) = educationCourseService.removeEnrollment(actorId, id, enrollmentId)
}

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members/{memberId}/education")
class MemberEducationController(
    private val educationCourseService: EducationCourseService,
) {
    @GetMapping
    fun getMemberEducation(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable memberId: Long,
    ): MemberEducationListResponse =
        MemberEducationListResponse(educationCourseService.getMemberEnrollments(actorId, memberId).map { it.toResponse() })
}

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class EducationCourseSaveRequest(
    @field:NotBlank(message = "제목을 입력해 주세요.")
    @field:Size(max = 200, message = "제목은 200자 이내로 입력해 주세요.")
    val title: String,

    @field:NotNull(message = "분류를 선택해 주세요.")
    val category: EducationCategory,

    @field:NotNull(message = "시작일을 입력해 주세요.")
    val startDate: LocalDate,

    val endDate: LocalDate?,

    @field:NotNull(message = "상태를 선택해 주세요.")
    val status: EducationCourseStatus,

    @field:Size(max = 120, message = "강사/인도자는 120자 이내로 입력해 주세요.")
    val instructorLabel: String?,

    @field:Size(max = 200, message = "장소는 200자 이내로 입력해 주세요.")
    val location: String?,

    val description: String?,
) {
    fun toCreateCommand() = EducationCourseCreateCommand(
        title = title, category = category,
        startDate = startDate, endDate = endDate, status = status,
        instructorLabel = instructorLabel, location = location, description = description,
    )
    fun toUpdateCommand() = EducationCourseUpdateCommand(
        title = title, category = category,
        startDate = startDate, endDate = endDate, status = status,
        instructorLabel = instructorLabel, location = location, description = description,
    )
}

data class AddEnrollmentRequest(
    val churchMemberId: Long?,
    @field:Size(max = 120, message = "이름은 120자 이내로 입력해 주세요.")
    val externalName: String?,
    @field:NotNull(message = "역할을 선택해 주세요.")
    val role: EnrollmentRole,
    @field:NotNull(message = "등록 상태를 선택해 주세요.")
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
) {
    fun toCommand() = AddEnrollmentCommand(
        churchMemberId = churchMemberId, externalName = externalName,
        role = role, enrollmentStatus = enrollmentStatus, note = note,
    )
}

data class UpdateEnrollmentRequest(
    @field:NotNull(message = "역할을 선택해 주세요.")
    val role: EnrollmentRole,
    @field:NotNull(message = "등록 상태를 선택해 주세요.")
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
) {
    fun toCommand() = UpdateEnrollmentCommand(role = role, enrollmentStatus = enrollmentStatus, note = note)
}

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class EducationCourseListResponse(
    val courses: List<EducationCourseSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class EducationCourseSummaryResponse(
    val id: Long,
    val title: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EducationCourseStatus,
    val instructorLabel: String?,
    val enrollmentCount: Int,
)

data class EducationCourseDetailResponse(
    val id: Long,
    val title: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EducationCourseStatus,
    val instructorLabel: String?,
    val location: String?,
    val description: String?,
    val coverPhotoAssetId: Long?,
    val enrollments: List<EnrollmentResponse>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class EnrollmentResponse(
    val id: Long,
    val churchMemberId: Long?,
    val displayName: String,
    val externalName: String?,
    val role: EnrollmentRole,
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
)

data class MemberEducationListResponse(val enrollments: List<MemberEducationEnrollmentResponse>)

data class MemberEducationEnrollmentResponse(
    val enrollmentId: Long,
    val courseId: Long,
    val courseTitle: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val courseStatus: EducationCourseStatus,
    val role: EnrollmentRole,
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
)

// ── Mappers ───────────────────────────────────────────────────────────────────

private fun EducationCourseSummary.toResponse() = EducationCourseSummaryResponse(
    id = id, title = title, category = category,
    startDate = startDate, endDate = endDate, status = status,
    instructorLabel = instructorLabel, enrollmentCount = enrollmentCount,
)

private fun EducationCoursePage.toResponse() = EducationCourseListResponse(
    courses = courses.map { it.toResponse() },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

private fun EducationCourseDetail.toDetailResponse() = EducationCourseDetailResponse(
    id = id, title = title, category = category,
    startDate = startDate, endDate = endDate, status = status,
    instructorLabel = instructorLabel, location = location, description = description,
    coverPhotoAssetId = coverPhotoAssetId, enrollments = enrollments.map { it.toResponse() },
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun EnrollmentDetail.toResponse() = EnrollmentResponse(
    id = id, churchMemberId = churchMemberId, displayName = displayName,
    externalName = externalName, role = role, enrollmentStatus = enrollmentStatus,
    note = note, createdAt = createdAt,
)

private fun MemberEducationEnrollment.toResponse() = MemberEducationEnrollmentResponse(
    enrollmentId = enrollmentId, courseId = courseId, courseTitle = courseTitle,
    category = category, startDate = startDate, endDate = endDate,
    courseStatus = courseStatus, role = role, enrollmentStatus = enrollmentStatus, note = note,
)
