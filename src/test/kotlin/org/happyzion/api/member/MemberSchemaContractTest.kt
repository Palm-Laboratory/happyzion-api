package org.happyzion.api.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MemberSchemaContractTest {

    @Test
    fun `V8 migration creates church_member with encrypted columns and blind indexes`() {
        val normalized = readV8()

        assertThat(normalized).contains("create table church_member")
        assertThat(normalized).contains("name_enc                 text not null")
        assertThat(normalized).contains("name_hash                varchar(64) not null")
        assertThat(normalized).contains("phone_enc                text not null")
        assertThat(normalized).contains("phone_hash               varchar(64) not null")
        assertThat(normalized).contains("phone_last4_hash         varchar(64)")
        assertThat(normalized).contains("email_enc                text")
        assertThat(normalized).contains("address_enc              text not null")
        assertThat(normalized).contains("birth_date_enc           text not null")
        assertThat(normalized).contains("memo_enc                 text")
        assertThat(normalized).contains("photo_asset_id           bigint references post_asset(id)")
        assertThat(normalized).contains("constraint uq_church_member_photo_asset_id unique (photo_asset_id)")
        assertThat(normalized).contains("constraint chk_church_member_status")
        assertThat(normalized).contains("constraint chk_church_member_faith_stage")
        assertThat(normalized).contains("create index idx_church_member_name_hash       on church_member(name_hash)")
        assertThat(normalized).contains("create index idx_church_member_phone_hash      on church_member(phone_hash)")
        assertThat(normalized).contains("create index idx_church_member_phone_last4     on church_member(phone_last4_hash)")
        assertThat(normalized).contains("create index idx_church_member_faith_stage     on church_member(faith_stage)")
        assertThat(normalized).contains("trg_church_member_updated_at")
    }

    @Test
    fun `V8 migration creates church_member_faith with all encrypted columns`() {
        val normalized = readV8()

        assertThat(normalized).contains("create table church_member_faith")
        assertThat(normalized).contains("confess_date_enc              text")
        assertThat(normalized).contains("learning_date_enc             text")
        assertThat(normalized).contains("baptism_date_enc              text")
        assertThat(normalized).contains("baptism_place_enc             text")
        assertThat(normalized).contains("baptism_officiant_enc         text")
        assertThat(normalized).contains("confirmation_date_enc         text")
        assertThat(normalized).contains("previous_church_enc           text")
        assertThat(normalized).contains("transferred_in_at_enc         text")
    }

    @Test
    fun `V8 migration creates church_member_audit_log with restrict FKs and diff_enc`() {
        val normalized = readV8()

        assertThat(normalized).contains("create table church_member_audit_log")
        assertThat(normalized).contains("church_member_id  bigint not null references church_member(id) on delete restrict")
        assertThat(normalized).contains("actor_id          bigint not null references admin_account(id) on delete restrict")
        assertThat(normalized).contains("diff_enc          text")
        assertThat(normalized).contains("constraint chk_church_member_audit_action check (action in ('create','update','delete'))")
        assertThat(normalized).contains("idx_church_member_audit_member_id")
    }

    @Test
    fun `V8 extends post_asset CHECK without MAIN_VIDEO and upload_token CHECK keeping MAIN_VIDEO`() {
        val normalized = readV8()

        assertThat(normalized).contains("alter table post_asset")
        assertThat(normalized).contains("check (kind in ('inline_image','file_attachment','member_photo'))")

        assertThat(normalized).contains("alter table upload_token")
        assertThat(normalized).contains("check (asset_kind in ('inline_image','file_attachment','main_video','member_photo'))")
    }

    private fun readV8(): String =
        Files.readString(Path.of("src/main/resources/db/migration/V8__create_church_member_registry.sql"))
            .lowercase()
}
