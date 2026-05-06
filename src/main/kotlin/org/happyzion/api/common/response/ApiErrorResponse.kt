package org.happyzion.api.common.response

import java.time.OffsetDateTime

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
