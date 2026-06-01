package org.happyzion.api.education.infrastructure.persistence

import org.happyzion.api.education.domain.EducationEnrollment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EducationEnrollmentRepository : JpaRepository<EducationEnrollment, Long> {

    fun findAllByEducationCourseIdOrderByCreatedAtAscIdAsc(educationCourseId: Long): List<EducationEnrollment>

    fun findAllByChurchMemberIdOrderByCreatedAtDescIdDesc(churchMemberId: Long): List<EducationEnrollment>

    fun existsByEducationCourseIdAndChurchMemberId(educationCourseId: Long, churchMemberId: Long): Boolean

    @Query("select e.educationCourseId as courseId, count(e) as cnt from EducationEnrollment e where e.educationCourseId in :courseIds group by e.educationCourseId")
    fun countGroupByCourseId(@Param("courseIds") courseIds: List<Long>): List<CourseEnrollmentCount>

    interface CourseEnrollmentCount {
        val courseId: Long
        val cnt: Long
    }
}

fun EducationEnrollmentRepository.countByCourseIds(courseIds: List<Long>): Map<Long, Int> =
    countGroupByCourseId(courseIds).associate { it.courseId to it.cnt.toInt() }
