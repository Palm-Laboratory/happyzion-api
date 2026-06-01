package org.happyzion.api.education.application

import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.education.domain.EducationCategory
import org.happyzion.api.education.domain.EducationCourse
import org.happyzion.api.education.domain.EducationCourseStatus
import org.happyzion.api.education.domain.EducationEnrollment
import org.happyzion.api.education.infrastructure.persistence.EducationCourseRepository
import org.happyzion.api.education.infrastructure.persistence.EducationEnrollmentRepository
import org.happyzion.api.education.infrastructure.persistence.countByCourseIds
import org.happyzion.api.education.infrastructure.persistence.search
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class EducationCourseService(
    private val courseRepository: EducationCourseRepository,
    private val enrollmentRepository: EducationEnrollmentRepository,
    private val memberRepository: ChurchMemberRepository,
    private val adminAccountRepository: AdminAccountRepository,
) {

    @Transactional(readOnly = true)
    fun listCourses(actorId: Long, year: Int?, status: EducationCourseStatus?, category: EducationCategory?): List<EducationCourseSummary> {
        requireActiveAdmin(actorId)
        val courses = courseRepository.search(year, status, category)
        val courseIds = courses.mapNotNull { it.id }
        val countByCourse: Map<Long, Int> = if (courseIds.isEmpty()) emptyMap()
        else enrollmentRepository.countByCourseIds(courseIds)
        return courses.map { it.toSummary(countByCourse[it.id!!] ?: 0) }
    }

    @Transactional(readOnly = true)
    fun getCourse(actorId: Long, courseId: Long): EducationCourseDetail {
        requireActiveAdmin(actorId)
        val course = requireCourse(courseId)
        val enrollments = enrollmentRepository.findAllByEducationCourseIdOrderByCreatedAtAscIdAsc(courseId)
        return course.toDetail(enrollments.map { it.toDetail() })
    }

    @Transactional
    fun createCourse(actorId: Long, command: EducationCourseCreateCommand): EducationCourseDetail {
        requireActiveAdmin(actorId)
        val course = courseRepository.save(
            EducationCourse(
                title = command.title,
                category = command.category,
                startDate = command.startDate,
                endDate = command.endDate,
                status = command.status,
                instructorLabel = command.instructorLabel,
                location = command.location,
                description = command.description,
                coverPhotoAssetId = null,
            )
        )
        return course.toDetail(emptyList())
    }

    @Transactional
    fun updateCourse(actorId: Long, courseId: Long, command: EducationCourseUpdateCommand): EducationCourseDetail {
        requireActiveAdmin(actorId)
        val course = requireCourse(courseId)
        course.title = command.title
        course.category = command.category
        course.startDate = command.startDate
        course.endDate = command.endDate
        course.status = command.status
        course.instructorLabel = command.instructorLabel
        course.location = command.location
        course.description = command.description
        course.updatedAt = OffsetDateTime.now()
        courseRepository.save(course)
        val enrollments = enrollmentRepository.findAllByEducationCourseIdOrderByCreatedAtAscIdAsc(courseId)
        return course.toDetail(enrollments.map { it.toDetail() })
    }

    @Transactional
    fun deleteCourse(actorId: Long, courseId: Long) {
        requireActiveAdmin(actorId)
        courseRepository.delete(requireCourse(courseId))
    }

    @Transactional
    fun addEnrollment(actorId: Long, courseId: Long, command: AddEnrollmentCommand): EnrollmentDetail {
        requireActiveAdmin(actorId)
        requireCourse(courseId)
        if (command.churchMemberId != null) {
            memberRepository.findByIdOrNull(command.churchMemberId)
                ?: throw NotFoundException("교인을 찾을 수 없습니다. id=${command.churchMemberId}")
            if (enrollmentRepository.existsByEducationCourseIdAndChurchMemberId(courseId, command.churchMemberId)) {
                throw IllegalArgumentException("이미 이 교육 과정에 등록된 교인입니다.")
            }
        }
        return enrollmentRepository.save(
            EducationEnrollment(
                educationCourseId = courseId,
                churchMemberId = command.churchMemberId,
                externalName = command.externalName,
                role = command.role,
                enrollmentStatus = command.enrollmentStatus,
                note = command.note,
            )
        ).toDetail()
    }

    @Transactional
    fun updateEnrollment(actorId: Long, courseId: Long, enrollmentId: Long, command: UpdateEnrollmentCommand): EnrollmentDetail {
        requireActiveAdmin(actorId)
        val enrollment = requireEnrollment(enrollmentId, courseId)
        enrollment.role = command.role
        enrollment.enrollmentStatus = command.enrollmentStatus
        enrollment.note = command.note
        enrollment.updatedAt = OffsetDateTime.now()
        return enrollmentRepository.save(enrollment).toDetail()
    }

    @Transactional
    fun removeEnrollment(actorId: Long, courseId: Long, enrollmentId: Long) {
        requireActiveAdmin(actorId)
        enrollmentRepository.delete(requireEnrollment(enrollmentId, courseId))
    }

    @Transactional(readOnly = true)
    fun getMemberEnrollments(actorId: Long, memberId: Long): List<MemberEducationEnrollment> {
        requireActiveAdmin(actorId)
        memberRepository.findByIdOrNull(memberId)
            ?: throw NotFoundException("교인을 찾을 수 없습니다. id=$memberId")
        val enrollments = enrollmentRepository.findAllByChurchMemberIdOrderByCreatedAtDescIdDesc(memberId)
        if (enrollments.isEmpty()) return emptyList()
        val courseMap = courseRepository.findAllById(enrollments.map { it.educationCourseId }.distinct()).associateBy { it.id!! }
        return enrollments.mapNotNull { e ->
            val course = courseMap[e.educationCourseId] ?: return@mapNotNull null
            MemberEducationEnrollment(
                enrollmentId = e.id!!,
                courseId = course.id!!,
                courseTitle = course.title,
                category = course.category,
                startDate = course.startDate,
                endDate = course.endDate,
                courseStatus = course.status,
                role = e.role,
                enrollmentStatus = e.enrollmentStatus,
                note = e.note,
            )
        }
    }

    private fun requireCourse(courseId: Long): EducationCourse =
        courseRepository.findByIdOrNull(courseId)
            ?: throw NotFoundException("교육 과정을 찾을 수 없습니다. id=$courseId")

    private fun requireEnrollment(enrollmentId: Long, courseId: Long): EducationEnrollment {
        val e = enrollmentRepository.findByIdOrNull(enrollmentId)
            ?: throw NotFoundException("교육생을 찾을 수 없습니다. id=$enrollmentId")
        if (e.educationCourseId != courseId) throw NotFoundException("교육생을 찾을 수 없습니다. id=$enrollmentId")
        return e
    }

    private fun requireActiveAdmin(actorId: Long) {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")
        if (!actor.active) throw ForbiddenException("비활성화된 계정입니다.")
    }

    private fun EducationCourse.toSummary(enrollmentCount: Int) = EducationCourseSummary(
        id = id!!, title = title, category = category,
        startDate = startDate, endDate = endDate, status = status,
        instructorLabel = instructorLabel, enrollmentCount = enrollmentCount,
    )

    private fun EducationCourse.toDetail(enrollments: List<EnrollmentDetail>) = EducationCourseDetail(
        id = id!!, title = title, category = category,
        startDate = startDate, endDate = endDate, status = status,
        instructorLabel = instructorLabel, location = location, description = description,
        coverPhotoAssetId = coverPhotoAssetId, enrollments = enrollments,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun EducationEnrollment.toDetail(): EnrollmentDetail {
        val memberName = churchMemberId?.let { memberRepository.findByIdOrNull(it)?.name ?: "(알 수 없음)" }
        return EnrollmentDetail(
            id = id!!, churchMemberId = churchMemberId,
            displayName = memberName ?: externalName ?: "(알 수 없음)",
            externalName = externalName, role = role,
            enrollmentStatus = enrollmentStatus, note = note, createdAt = createdAt,
        )
    }
}
