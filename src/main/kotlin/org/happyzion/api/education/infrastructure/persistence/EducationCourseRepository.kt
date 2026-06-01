package org.happyzion.api.education.infrastructure.persistence

import org.happyzion.api.education.domain.EducationCategory
import org.happyzion.api.education.domain.EducationCourse
import org.happyzion.api.education.domain.EducationCourseStatus
import org.springframework.data.jpa.repository.JpaRepository

interface EducationCourseRepository : JpaRepository<EducationCourse, Long> {

    fun findAllByOrderByStartDateDescIdDesc(): List<EducationCourse>
}

fun EducationCourseRepository.search(year: Int?, status: EducationCourseStatus?, category: EducationCategory?): List<EducationCourse> =
    findAllByOrderByStartDateDescIdDesc().filter { course ->
        (year == null || course.startDate.year == year) &&
        (status == null || course.status == status) &&
        (category == null || course.category == category)
    }
