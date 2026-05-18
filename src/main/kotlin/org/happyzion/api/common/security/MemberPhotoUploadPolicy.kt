package org.happyzion.api.common.security

object MemberPhotoUploadPolicy {
    val ALLOWED_MIME: List<String> = listOf("image/jpeg", "image/png", "image/webp")
    const val MAX_BYTES: Long = 5L * 1024 * 1024
}
