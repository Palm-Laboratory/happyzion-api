package org.happyzion.api.sms

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SmsSchemaContractTest {

    @Test
    fun `V9 migration creates sms_log with correct columns and constraints`() {
        val normalized = readV9()
        assertThat(normalized).contains("create table sms_log")
        assertThat(normalized).contains("aligo_msg_id varchar(64)")
        assertThat(normalized).contains("msg_type varchar(8) not null")
        assertThat(normalized).contains("total_count integer not null")
        assertThat(normalized).contains("requested_by bigint not null")
        assertThat(normalized).contains("references admin_account(id) on delete restrict")
        assertThat(normalized).contains("constraint chk_sms_log_msg_type check (msg_type in ('sms','lms','mms'))")
        assertThat(normalized).contains("idx_sms_log_requested_at")
        assertThat(normalized).contains("idx_sms_log_aligo_msg_id")
    }

    @Test
    fun `V9 migration creates sms_log_recipient with PII columns and constraints`() {
        val normalized = readV9()
        assertThat(normalized).contains("create table sms_log_recipient")
        assertThat(normalized).contains("phone_enc text not null")
        assertThat(normalized).contains("phone_hash varchar(64) not null")
        assertThat(normalized).contains("receiver_name_enc text")
        assertThat(normalized).contains("message_enc text not null")
        assertThat(normalized).contains("constraint chk_sms_log_recipient_status")
        assertThat(normalized).contains("check (status in ('pending','sent','failed','unknown'))")
        assertThat(normalized).contains("idx_sms_log_recipient_log")
        assertThat(normalized).contains("idx_sms_log_recipient_phone_hash")
    }

    private fun readV9(): String =
        Files.readString(Path.of("src/main/resources/db/migration/V10__create_sms_log.sql"))
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
