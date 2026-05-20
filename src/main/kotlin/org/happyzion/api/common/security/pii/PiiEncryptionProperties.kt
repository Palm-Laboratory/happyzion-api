package org.happyzion.api.common.security.pii

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "pii.encryption")
data class PiiEncryptionProperties(
    @field:NotBlank(message = "HAPPYZION_PII_ENCRYPTION_KEYS는 비어 있을 수 없습니다.")
    val keys: String,
    @field:NotBlank(message = "HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID는 비어 있을 수 없습니다.")
    val activeKeyId: String,
    @field:NotBlank(message = "HAPPYZION_PII_HASH_KEY는 비어 있을 수 없습니다.")
    val hashKey: String,
)
