package org.happyzion.api.board

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BoardSchemaContractTest {

    @Test
    fun `database migrations should include the current schema migrations`() {
        val migrationDir = Path.of("src/main/resources/db/migration")
        val migrations = Files.list(migrationDir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".sql") }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        assertThat(migrations).containsExactly(
            "V1__create_happyzion_schema.sql",
            "V2__create_site_setting.sql",
            "V3__allow_main_video_upload_token.sql",
            "V4__create_mission_history.sql",
            "V5__seed_mission_history.sql",
            "V6__drop_member_registry.sql",
        )
    }

    @Test
    fun `V1 migration should not create retired board type tables`() {
        val normalized = readMigration("V1__create_happyzion_schema.sql")

        assertThat(normalized).doesNotContain("create table board_type")
        assertThat(normalized).doesNotContain("board_type_id")
        assertThat(normalized).doesNotContain("idx_upload_token_board_id")
    }

    @Test
    fun `V1 migration should define the current board upload schema`() {
        val normalized = readBaselineMigration()

        assertThat(normalized).contains("create table board")
        assertThat(normalized).contains("menu_id bigint references menu_item(id) on delete set null")
        assertThat(normalized).contains("constraint chk_board_type")
        assertThat(normalized).contains("check (type in ('notice', 'bulletin', 'album', 'general'))")
        assertThat(normalized).contains("create unique index uq_board_menu_id")

        assertThat(normalized).contains("create table post")
        assertThat(normalized).contains("board_id bigint not null references board(id) on delete cascade")
        assertThat(normalized).contains("menu_id bigint not null references menu_item(id)")
        assertThat(normalized).contains("content_json jsonb not null")
        assertThat(normalized).contains("content_html text")
        assertThat(normalized).contains("author_id bigint not null references admin_account(id)")
        assertThat(normalized).contains("view_count bigint not null default 0")
        assertThat(normalized).contains("is_public boolean not null default true")
        assertThat(normalized).contains("is_pinned boolean not null default false")
        assertThat(normalized).contains("idx_post_board_public_pinned_created_at")
        assertThat(normalized).contains("idx_post_menu_public_pinned_created_at")

        assertThat(normalized).contains("create table post_asset")
        assertThat(normalized).contains("original_filename varchar(255) not null")
        assertThat(normalized).contains("stored_path text not null")
        assertThat(normalized).contains("byte_size bigint not null")
        assertThat(normalized).contains("detached_at timestamptz")
        assertThat(normalized).contains("constraint uq_post_asset_stored_path unique (stored_path)")
        assertThat(normalized).contains("constraint chk_post_asset_kind")
        assertThat(normalized).contains("check (kind in ('inline_image', 'file_attachment'))")
        assertThat(normalized).contains("idx_post_asset_detached_at")

        assertThat(normalized).contains("create table upload_token")
        assertThat(normalized).doesNotContain("board_id bigint references board(id) on delete cascade")
        assertThat(normalized).contains("token_hash varchar(128) not null unique")
        assertThat(normalized).contains("allowed_mime_types jsonb not null default '[]'::jsonb")
        assertThat(normalized).contains("constraint chk_upload_token_asset_kind")
        assertThat(normalized).contains("check (asset_kind in ('inline_image', 'file_attachment'))")

        listOf(
            "trg_board_updated_at",
            "trg_post_updated_at",
            "trg_post_asset_updated_at",
            "trg_upload_token_updated_at",
        ).forEach { triggerName ->
            assertThat(normalized).contains(triggerName)
        }
        assertThat(normalized).contains("execute function set_current_timestamp_updated_at()")
    }

    @Test
    fun `V1 migration should define youtube video metadata without legacy sermon or media table names`() {
        val normalized = readBaselineMigration()

        assertThat(normalized).contains("create table youtube_video")
        assertThat(normalized).contains("create table youtube_playlist_item")
        assertThat(normalized).contains("create table youtube_video_meta")
        assertThat(normalized).contains("idx_youtube_video_meta_hidden")
        assertThat(normalized).contains("idx_youtube_video_meta_display_published_at")
        assertThat(normalized).contains("trg_youtube_video_meta_updated_at")
        assertThat(normalized).doesNotContain("create table video_meta")
        assertThat(normalized).doesNotContain("sermon_video_meta")
        assertThat(normalized).doesNotContain("media_video_meta")
    }

    @Test
    fun `V1 migration should seed only current static menu entries`() {
        val normalized = readBaselineMigration()

        assertThat(normalized).contains("'교회 소개', false, false, 'about'")
        assertThat(normalized).contains("'선교 사역', false, false, 'mission'")
        assertThat(normalized).contains("'행복이 가득한', false, false, 'happy'")
        assertThat(normalized).contains("'인사말/비전', false, false, 'greeting', 'about.greeting'")
        assertThat(normalized).contains("'교회 이야기', false, false, 'church-story', 'about.church-story'")
        assertThat(normalized).contains("'부흥 조직도', false, false, 'revival-organization', 'about.revival-organization'")
        assertThat(normalized).contains("'선교 이력', false, false, 'mission-history', 'about.mission-history'")
        assertThat(normalized).contains("select setval('menu_item_id_seq', 13, true)")
        assertThat(normalized).contains("'happy-2026', 13, '2026년', 'general'")
        assertThat(normalized).contains("'mission-philippines', 10, '필리핀 선교', 'general'")
        assertThat(normalized).contains("'mission-indonesia', 11, '인도네시아 선교', 'general'")
        assertThat(normalized).doesNotContain("'예배 영상', 'videos'")
        assertThat(normalized).doesNotContain("'교회 행사', 'events'")
        assertThat(normalized).doesNotContain("'제자 양육', 'newcomer'")
        assertThat(normalized).doesNotContain("legacy-board-posts")
    }

    @Test
    fun `V1 migration should seed one super admin account`() {
        val normalized = readBaselineMigration()

        assertThat(normalized).contains("insert into admin_account")
        assertThat(normalized).contains("'happyzion.admin'")
        assertThat(normalized).contains("'happy zion 관리자'")
        assertThat(normalized).contains("'super_admin'")
        assertThat(normalized).contains("select setval('admin_account_id_seq', 1, true)")
    }

    @Test
    fun `V6 migration should drop member registry tables without rewriting V1`() {
        val normalized = readMigration("V6__drop_member_registry.sql")

        assertThat(readBaselineMigration()).contains("create table member")
        assertThat(normalized).contains("drop table if exists attendance_record cascade")
        assertThat(normalized).contains("drop table if exists attendance_service_date cascade")
        assertThat(normalized).contains("drop table if exists member_event_log cascade")
        assertThat(normalized).contains("drop table if exists member_tag cascade")
        assertThat(normalized).contains("drop table if exists member_training cascade")
        assertThat(normalized).contains("drop table if exists member_service cascade")
        assertThat(normalized).contains("drop table if exists member_family cascade")
        assertThat(normalized).contains("drop table if exists member_faith cascade")
        assertThat(normalized).contains("drop table if exists member cascade")
    }

    @Test
    fun `board repository should support deleting boards by menu ids before menu deletion`() {
        val repository = Path.of("src/main/kotlin/org/happyzion/api/board/infrastructure/persistence/BoardRepository.kt")

        assertThat(repository).exists()

        val normalized = Files.readString(repository).lowercase()

        assertThat(normalized).contains("findallbymenuidin")
    }

    @Test
    fun `board post post asset and upload token entities should expose the current field names`() {
        val expectedFiles = listOf(
            "src/main/kotlin/org/happyzion/api/board/domain/Board.kt",
            "src/main/kotlin/org/happyzion/api/board/domain/Post.kt",
            "src/main/kotlin/org/happyzion/api/board/domain/PostAsset.kt",
            "src/main/kotlin/org/happyzion/api/board/domain/UploadToken.kt",
        ).map(Path::of)

        expectedFiles.forEach { assertThat(it).exists() }

        val board = Files.readString(expectedFiles[0]).lowercase()
        val post = Files.readString(expectedFiles[1]).lowercase()
        val postAsset = Files.readString(expectedFiles[2]).lowercase()
        val uploadToken = Files.readString(expectedFiles[3]).lowercase()

        assertThat(board).contains("type: boardtype")
        assertThat(board).doesNotContain("boardtypeid")
        assertThat(post).contains("menuid")
        assertThat(post).contains("name = \"menu_id\"")
        assertThat(post).contains("contentjson")
        assertThat(post).contains("contenthtml")
        assertThat(post).contains("ispublic")
        assertThat(post).contains("ispinned")
        assertThat(postAsset).contains("originalfilename")
        assertThat(postAsset).contains("storedpath")
        assertThat(postAsset).contains("bytesize")
        assertThat(postAsset).contains("detachedat")
        assertThat(postAsset).contains("sortorder")
        assertThat(uploadToken).contains("actorid")
        assertThat(uploadToken).contains("maxbytesize")
        assertThat(uploadToken).contains("usedat")
        assertThat(uploadToken).doesNotContain("boardid")
    }

    private fun readBaselineMigration(): String =
        readMigration("V1__create_happyzion_schema.sql")

    private fun readMigration(fileName: String): String {
        val migration = Path.of("src/main/resources/db/migration/$fileName")

        assertThat(migration).exists()

        return Files.readString(migration).lowercase()
    }
}
