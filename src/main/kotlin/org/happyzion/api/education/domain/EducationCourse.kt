package org.happyzion.api.education.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "education_course")
class EducationCourse(
    @Column(nullable = false, length = 200)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var category: EducationCategory,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date")
    var endDate: LocalDate?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: EducationCourseStatus,

    @Column(name = "instructor_label", length = 120)
    var instructorLabel: String?,

    @Column(length = 200)
    var location: String?,

    @Column(columnDefinition = "text")
    var description: String?,

    @Column(name = "cover_photo_asset_id")
    var coverPhotoAssetId: Long?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
