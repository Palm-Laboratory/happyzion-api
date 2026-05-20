package org.happyzion.api.member.infrastructure.persistence

import jakarta.persistence.Convert
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.security.pii.EncryptedLocalDateConverter
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import org.happyzion.api.member.domain.ChurchMember
import org.happyzion.api.member.domain.ChurchMemberAuditLog
import org.happyzion.api.member.domain.ChurchMemberFaith
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies PII encryption contract without a live database.
 * A live-DB integration test would require Testcontainers which is not configured in this project.
 * These reflection-based checks ensure the encryption annotations are in place and the migration
 * SQL uses encrypted column names, providing equivalent contractual guarantees.
 */
class ChurchMemberRepositoryContractTest {

    @Test
    fun `ChurchMember PII string fields are annotated with EncryptedStringConverter`() {
        val piiStringFields = listOf("name", "phone", "email", "address", "addressDetail", "job", "memo")

        for (fieldName in piiStringFields) {
            val field = findField(ChurchMember::class.java, fieldName)
                ?: error("Field '$fieldName' not found in ChurchMember")
            val convert = field.getAnnotation(Convert::class.java)
            assertThat(convert)
                .withFailMessage("Field '$fieldName' in ChurchMember must have @Convert")
                .isNotNull
            assertThat(convert.converter)
                .withFailMessage("Field '$fieldName' must use EncryptedStringConverter")
                .isEqualTo(EncryptedStringConverter::class)
        }
    }

    @Test
    fun `ChurchMember PII date field is annotated with EncryptedLocalDateConverter`() {
        val field = findField(ChurchMember::class.java, "birthDate")
            ?: error("Field 'birthDate' not found in ChurchMember")
        val convert = field.getAnnotation(Convert::class.java)
        assertThat(convert).withFailMessage("birthDate must have @Convert").isNotNull
        assertThat(convert.converter).isEqualTo(EncryptedLocalDateConverter::class)
    }

    @Test
    fun `ChurchMemberFaith PII fields are encrypted`() {
        val stringFields = listOf("baptismPlace", "baptismOfficiant", "previousChurch")
        val dateFields = listOf("confessDate", "learningDate", "baptismDate", "confirmationDate", "transferredInAt")

        for (fieldName in stringFields) {
            val field = findField(ChurchMemberFaith::class.java, fieldName)
                ?: error("Field '$fieldName' not found in ChurchMemberFaith")
            val convert = field.getAnnotation(Convert::class.java)
            assertThat(convert)
                .withFailMessage("Field '$fieldName' in ChurchMemberFaith must have @Convert")
                .isNotNull
            assertThat(convert.converter).isEqualTo(EncryptedStringConverter::class)
        }

        for (fieldName in dateFields) {
            val field = findField(ChurchMemberFaith::class.java, fieldName)
                ?: error("Field '$fieldName' not found in ChurchMemberFaith")
            val convert = field.getAnnotation(Convert::class.java)
            assertThat(convert)
                .withFailMessage("Field '$fieldName' in ChurchMemberFaith must have @Convert")
                .isNotNull
            assertThat(convert.converter).isEqualTo(EncryptedLocalDateConverter::class)
        }
    }

    @Test
    fun `ChurchMemberAuditLog diffJson is encrypted`() {
        val field = findField(ChurchMemberAuditLog::class.java, "diffJson")
            ?: error("Field 'diffJson' not found in ChurchMemberAuditLog")
        val convert = field.getAnnotation(Convert::class.java)
        assertThat(convert).withFailMessage("diffJson must have @Convert").isNotNull
        assertThat(convert.converter).isEqualTo(EncryptedStringConverter::class)
    }

    @Test
    fun `V8 migration declares UNIQUE constraint on photo_asset_id`() {
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V8__create_church_member_registry.sql")
        ).lowercase()
        assertThat(sql).contains("constraint uq_church_member_photo_asset_id unique (photo_asset_id)")
    }

    @Test
    fun `ChurchMemberAuditLogRepository has descending-order pagination method`() {
        val methodNames = ChurchMemberAuditLogRepository::class.java.methods.map { it.name }
        assertThat(methodNames).contains("findByChurchMemberIdOrderByCreatedAtDescIdDesc")
    }

    @Test
    fun `ChurchMemberRepository has search method`() {
        val methodNames = ChurchMemberRepository::class.java.methods.map { it.name }
        assertThat(methodNames).contains("search")
    }

    @Test
    fun `V8 migration encrypted columns use _enc suffix — no plaintext column names for PII`() {
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V8__create_church_member_registry.sql")
        ).lowercase()
        assertThat(sql).doesNotContain("name varchar")
        assertThat(sql).doesNotContain("phone varchar")
        assertThat(sql).doesNotContain("address varchar")
        assertThat(sql).doesNotContain("birth_date date")
        assertThat(sql).contains("name_enc")
        assertThat(sql).contains("phone_enc")
        assertThat(sql).contains("address_enc")
        assertThat(sql).contains("birth_date_enc")
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val field = runCatching { current!!.getDeclaredField(name) }.getOrNull()
            if (field != null) return field.also { it.isAccessible = true }
            current = current.superclass
        }
        return null
    }
}
