package org.happyzion.api.education.application

import org.happyzion.api.education.domain.EducationCategory
import org.happyzion.api.education.domain.EducationCourseStatus
import org.happyzion.api.education.domain.EnrollmentRole
import org.happyzion.api.education.domain.EnrollmentStatus
import java.time.LocalDate
import java.time.OffsetDateTime

// ── Course ───────────────────────────────────────────────────────────────────

data class EducationCourseSummary(
    val id: Long,
    val title: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EducationCourseStatus,
    val instructorLabel: String?,
    val enrollmentCount: Int,
)

data class EducationCoursePage(
    val courses: List<EducationCourseSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class EducationCourseDetail(
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
    val enrollments: List<EnrollmentDetail>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class EducationCourseCreateCommand(
    val title: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EducationCourseStatus,
    val instructorLabel: String?,
    val location: String?,
    val description: String?,
)

data class EducationCourseUpdateCommand(
    val title: String,
    val category: EducationCategory,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EducationCourseStatus,
    val instructorLabel: String?,
    val location: String?,
    val description: String?,
)

// ── Enrollment ──────────────────────────────────────────────────────────────

data class EnrollmentDetail(
    val id: Long,
    val churchMemberId: Long?,
    val displayName: String,
    val externalName: String?,
    val role: EnrollmentRole,
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
)

data class AddEnrollmentCommand(
    val churchMemberId: Long?,
    val externalName: String?,
    val role: EnrollmentRole,
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
)

data class UpdateEnrollmentCommand(
    val role: EnrollmentRole,
    val enrollmentStatus: EnrollmentStatus,
    val note: String?,
)

// ── Member's education history ────────────────────────────────────────────────

data class MemberEducationEnrollment(
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
