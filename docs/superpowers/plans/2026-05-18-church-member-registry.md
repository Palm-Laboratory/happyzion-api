# 교적부(Church Member Registry) Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-only church member registry (personal info + faith info + audit log) with application-level PII encryption, blind-index search, member-photo streaming, and active-admin guards.

**Architecture:** New `org.happyzion.api.member` slice (domain / application / infrastructure / interfaces). Shared `common.security.pii` package for AES-GCM encryption + HMAC blind index, with Hibernate `SpringBeanContainer` injecting beans into JPA converters. Member photos reuse the existing `post_asset` / upload-token infrastructure with `MEMBER_PHOTO` kind, server-enforced MIME/size policy, and an authenticated Spring streaming proxy. Soft-delete only; audit log is append-only by app convention.

**Tech Stack:** Kotlin 2.1.21, Spring Boot 3.5.0, JDK 21, JPA/Hibernate, Flyway, PostgreSQL 16, JUnit 5, mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-05-18-church-member-registry-design.md`

**Suggested PR grouping** (each PR independently buildable + testable):
- PR 1: Task 1 (V8 migration + existing schema test update)
- PR 2: Tasks 2–3 (env config + `PostAssetKind.MEMBER_PHOTO`)
- PR 3: Tasks 4–7 (PII infra: properties, key ring, encryptor, hasher, normalizer, converters)
- PR 4: Tasks 8–9 (`AdminAccountGuard`, `MemberPhotoUploadPolicy`)
- PR 5: Tasks 10–13 (board/common changes: storage `load`, kind prefix, upload guards, redaction, CORS)
- PR 6: Tasks 14–15 (member domain enums, entities, repositories)
- PR 7: Tasks 16–19 (audit writer, photo service, admin service, search)
- PR 8: Tasks 20–21 (DTOs, admin controller, photo controller)
- PR 9: Tasks 22–23 (contract tests, OpenAPI regen, README)

---

## File Structure

### New files

```
src/main/resources/db/migration/
└── V8__create_church_member_registry.sql

src/main/kotlin/org/happyzion/api/common/security/pii/
├── PiiEncryptionProperties.kt
├── PiiKeyRing.kt
├── PiiEncryptor.kt
├── PiiHasher.kt
├── MemberSearchKeyNormalizer.kt
├── EncryptedStringConverter.kt
└── EncryptedLocalDateConverter.kt

src/main/kotlin/org/happyzion/api/common/security/
└── MemberPhotoUploadPolicy.kt

src/main/kotlin/org/happyzion/api/common/config/
└── HibernateSpringBeanContainerConfig.kt

src/main/kotlin/org/happyzion/api/adminaccount/application/
└── AdminAccountGuard.kt

src/main/kotlin/org/happyzion/api/member/
├── domain/
│   ├── Sex.kt
│   ├── BirthCalendar.kt
│   ├── ChurchMemberStatus.kt
│   ├── FaithStage.kt
│   ├── ChurchMemberOffice.kt
│   ├── AuditAction.kt
│   ├── ChurchMember.kt
│   ├── ChurchMemberFaith.kt
│   └── ChurchMemberAuditLog.kt
├── application/
│   ├── ChurchMemberAdminModels.kt
│   ├── ChurchMemberSnapshot.kt
│   ├── ChurchMemberAuditWriter.kt
│   ├── ChurchMemberPhotoService.kt
│   ├── ChurchMemberPhotoStreamer.kt
│   └── ChurchMemberAdminService.kt
├── infrastructure/persistence/
│   ├── ChurchMemberRepository.kt
│   ├── ChurchMemberFaithRepository.kt
│   └── ChurchMemberAuditLogRepository.kt
└── interfaces/
    ├── api/
    │   ├── ChurchMemberAdminController.kt
    │   └── ChurchMemberPhotoController.kt
    └── dto/
        └── ChurchMemberAdminDtos.kt
```

Test mirror under `src/test/kotlin/.../`.

### Modified files

| File | Reason |
|---|---|
| `src/main/kotlin/org/happyzion/api/ApiApplication.kt` | Register `PiiEncryptionProperties` |
| `src/main/kotlin/org/happyzion/api/board/domain/PostAssetKind.kt` | Add `MEMBER_PHOTO` |
| `src/main/kotlin/org/happyzion/api/board/application/AttachmentStorage.kt` | Add `load(storedPath): Resource` |
| `src/main/kotlin/org/happyzion/api/board/application/LocalAttachmentStorage.kt` | Implement `load` with path-traversal guard; `MEMBER_PHOTO` prefix |
| `src/main/kotlin/org/happyzion/api/board/application/UploadAssetService.kt` | Inject `AdminAccountGuard`, call on `MEMBER_PHOTO` upload, rollback file on failure |
| `src/main/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminController.kt` | Guard + policy override on `MEMBER_PHOTO` token issuance |
| `src/main/kotlin/org/happyzion/api/common/logging/RequestLoggingFilter.kt` | Path-aware redaction of `name`/`phone` |
| `src/main/kotlin/org/happyzion/api/common/config/WebConfig.kt` | CORS mapping `/api/v1/admin/members/**` |
| `application.yml` | Bind PII properties |
| `.env.example` / `.env.production.example` | New env vars |
| `deploy/docker-compose.prod.yml` | Forward new env vars |
| `deploy/nginx/api.happyzion.com.conf` | Deny `/upload/member-photos/` with `^~` + 404 |
| `src/test/kotlin/.../EnvironmentConfigContractTest.kt` | Assert new env keys |
| `src/test/kotlin/.../BoardSchemaContractTest.kt` | Add V8 to migration list |
| `README.md` | New endpoints |

---

## Task 1: V8 Flyway migration + schema contract update

**Files:**
- Create: `src/main/resources/db/migration/V8__create_church_member_registry.sql`
- Modify: `src/test/kotlin/org/happyzion/api/board/BoardSchemaContractTest.kt:21-29`
- Create: `src/test/kotlin/org/happyzion/api/member/MemberSchemaContractTest.kt`

- [ ] **Step 1: Write the failing schema contract test**

Create `src/test/kotlin/org/happyzion/api/member/MemberSchemaContractTest.kt`:

```kotlin
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
```

Update `src/test/kotlin/org/happyzion/api/board/BoardSchemaContractTest.kt:21-29` — replace the existing block:

```kotlin
assertThat(migrations).containsExactly(
    "V1__create_happyzion_schema.sql",
    "V2__create_site_setting.sql",
    "V3__allow_main_video_upload_token.sql",
    "V4__create_mission_history.sql",
    "V5__seed_mission_history.sql",
    "V6__add_developer_role.sql",
    "V7__drop_member_registry.sql",
    "V8__create_church_member_registry.sql",
)
```

- [ ] **Step 2: Run tests, verify they fail**

```bash
./gradlew test --tests "org.happyzion.api.member.MemberSchemaContractTest"
./gradlew test --tests "org.happyzion.api.board.BoardSchemaContractTest"
```

Expected: both fail (V8 file missing / migration list mismatch).

- [ ] **Step 3: Write the V8 migration**

Create `src/main/resources/db/migration/V8__create_church_member_registry.sql`:

```sql
create table church_member (
    id                       bigserial primary key,

    name_enc                 text not null,
    name_hash                varchar(64) not null,
    phone_enc                text not null,
    phone_hash               varchar(64) not null,
    phone_last4_hash         varchar(64),
    email_enc                text,
    address_enc              text not null,
    address_detail_enc       text,
    job_enc                  text,
    memo_enc                 text,
    birth_date_enc           text not null,

    sex                      varchar(1)   not null,
    birth_calendar           varchar(10)  not null,
    photo_asset_id           bigint references post_asset(id),
    cell_label               varchar(120),
    status                   varchar(32)  not null,
    faith_stage              varchar(32),
    office                   varchar(32)  not null default 'LAY',
    office_appointed_at      date,
    registered_at            date         not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),

    constraint uq_church_member_photo_asset_id unique (photo_asset_id),
    constraint chk_church_member_sex            check (sex in ('M','F')),
    constraint chk_church_member_birth_calendar check (birth_calendar in ('SOLAR','LUNAR')),
    constraint chk_church_member_status         check (status in (
        'ACTIVE','NEW','RESTING','LONG_ABSENT','TRANSFERRED_OUT','DECEASED','REMOVED')),
    constraint chk_church_member_office         check (office in (
        'LAY','DEACON_TEMP','DEACON','GWONSA','ELDER','ELDER_EMERITUS','EVANGELIST','PASTOR')),
    constraint chk_church_member_faith_stage    check (faith_stage is null or faith_stage in (
        'SEEKER','NEW_COMER','SETTLED','GROWING','DISCIPLE','MINISTER','LEADER'))
);

create index idx_church_member_name_hash       on church_member(name_hash);
create index idx_church_member_phone_hash      on church_member(phone_hash);
create index idx_church_member_phone_last4     on church_member(phone_last4_hash);
create index idx_church_member_registered_at   on church_member(registered_at desc, id desc);
create index idx_church_member_status          on church_member(status);
create index idx_church_member_faith_stage     on church_member(faith_stage);

create trigger trg_church_member_updated_at
before update on church_member
for each row
execute function set_current_timestamp_updated_at();

create table church_member_faith (
    church_member_id              bigint primary key
                                  references church_member(id) on delete cascade,
    confess_date_enc              text,
    learning_date_enc             text,
    baptism_date_enc              text,
    baptism_place_enc             text,
    baptism_officiant_enc         text,
    confirmation_date_enc         text,
    previous_church_enc           text,
    transferred_in_at_enc         text,
    created_at                    timestamptz not null default now(),
    updated_at                    timestamptz not null default now()
);

create trigger trg_church_member_faith_updated_at
before update on church_member_faith
for each row
execute function set_current_timestamp_updated_at();

create table church_member_audit_log (
    id                bigserial primary key,
    church_member_id  bigint not null references church_member(id) on delete restrict,
    actor_id          bigint not null references admin_account(id) on delete restrict,
    action            varchar(20) not null,
    diff_enc          text,
    created_at        timestamptz not null default now(),
    constraint chk_church_member_audit_action check (action in ('CREATE','UPDATE','DELETE'))
);

create index idx_church_member_audit_member_id
    on church_member_audit_log(church_member_id, created_at desc, id desc);

alter table post_asset
    drop constraint chk_post_asset_kind;
alter table post_asset
    add constraint chk_post_asset_kind
    check (kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MEMBER_PHOTO'));

alter table upload_token
    drop constraint chk_upload_token_asset_kind;
alter table upload_token
    add constraint chk_upload_token_asset_kind
    check (asset_kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MAIN_VIDEO','MEMBER_PHOTO'));
```

- [ ] **Step 4: Run tests, verify they pass**

```bash
./gradlew test --tests "org.happyzion.api.member.MemberSchemaContractTest" --tests "org.happyzion.api.board.BoardSchemaContractTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V8__create_church_member_registry.sql \
        src/test/kotlin/org/happyzion/api/member/MemberSchemaContractTest.kt \
        src/test/kotlin/org/happyzion/api/board/BoardSchemaContractTest.kt
git commit -m "feat(member): V8 migration for church_member registry tables and CHECK extensions"
```

---

## Task 2: `PostAssetKind.MEMBER_PHOTO` enum value

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/board/domain/PostAssetKind.kt`

- [ ] **Step 1: Add the enum value**

Replace contents:

```kotlin
package org.happyzion.api.board.domain

enum class PostAssetKind {
    INLINE_IMAGE,
    FILE_ATTACHMENT,
    MAIN_VIDEO,
    MEMBER_PHOTO,
}
```

- [ ] **Step 2: Run all tests to verify no regression**

```bash
./gradlew test
```

Expected: PASS (no test depends on a fixed enum set; CHECK constraint already extended in Task 1).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/board/domain/PostAssetKind.kt
git commit -m "feat(board): add PostAssetKind.MEMBER_PHOTO for church member photos"
```

---

## Task 3: PII env vars + properties registration + contract test

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/PiiEncryptionProperties.kt`
- Modify: `src/main/kotlin/org/happyzion/api/ApiApplication.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`, `.env.production.example`
- Modify: `deploy/docker-compose.prod.yml`
- Modify: `src/test/kotlin/org/happyzion/api/support/EnvironmentConfigContractTest.kt`

- [ ] **Step 1: Read existing env contract test to understand pattern**

```bash
cat src/test/kotlin/org/happyzion/api/support/EnvironmentConfigContractTest.kt
```

Note the assertions style. Add three new assertions to it.

- [ ] **Step 2: Extend `EnvironmentConfigContractTest`**

Append assertions for the three new keys in the test methods that read `.env.example`, `application.yml`, and `deploy/docker-compose.prod.yml`. The exact assertions follow the existing pattern — search for `HAPPYZION_UPLOAD_ROOT` in the test and add parallel ones for:
- `HAPPYZION_PII_ENCRYPTION_KEYS`
- `HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID`
- `HAPPYZION_PII_HASH_KEY`

- [ ] **Step 3: Run the test, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.support.EnvironmentConfigContractTest"
```

Expected: FAIL — keys missing in env/yml/compose files.

- [ ] **Step 4: Add the env vars**

In `.env.example` and `.env.production.example`, append:

```text
HAPPYZION_PII_ENCRYPTION_KEYS=v1:replace-with-base64-of-32-random-bytes
HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID=v1
HAPPYZION_PII_HASH_KEY=replace-with-base64-of-32-random-bytes
```

In `src/main/resources/application.yml` add under the top-level keys (alongside the existing `admin:`, `cors:`, `upload:` blocks):

```yaml
pii:
  encryption:
    keys: ${HAPPYZION_PII_ENCRYPTION_KEYS:}
    active-key-id: ${HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID:}
    hash-key: ${HAPPYZION_PII_HASH_KEY:}
```

In `deploy/docker-compose.prod.yml` add inside `services.app.environment:`:

```yaml
      HAPPYZION_PII_ENCRYPTION_KEYS: ${HAPPYZION_PII_ENCRYPTION_KEYS}
      HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID: ${HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID}
      HAPPYZION_PII_HASH_KEY: ${HAPPYZION_PII_HASH_KEY}
```

- [ ] **Step 5: Create `PiiEncryptionProperties.kt`**

```kotlin
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
```

Register it in `src/main/kotlin/org/happyzion/api/ApiApplication.kt` by adding `PiiEncryptionProperties::class` inside the `@EnableConfigurationProperties(value = [...])` array. Add the import as well.

- [ ] **Step 6: Run test, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.support.EnvironmentConfigContractTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add .env.example .env.production.example \
        src/main/resources/application.yml \
        deploy/docker-compose.prod.yml \
        src/main/kotlin/org/happyzion/api/common/security/pii/PiiEncryptionProperties.kt \
        src/main/kotlin/org/happyzion/api/ApiApplication.kt \
        src/test/kotlin/org/happyzion/api/support/EnvironmentConfigContractTest.kt
git commit -m "feat(pii): add PII encryption properties and environment bindings"
```

---

## Task 4: `PiiKeyRing`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/PiiKeyRing.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/security/pii/PiiKeyRingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PiiKeyRingTest {

    private val key1 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="   // 32B base64
    private val key2 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="

    @Test
    fun `parses single key and exposes activeKeyId`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1", activeKeyId = "v1", hashKey = "h")
        val ring = PiiKeyRing(props)

        assertThat(ring.activeKeyId).isEqualTo("v1")
        assertThat(ring.keyFor("v1")).isNotNull
    }

    @Test
    fun `parses multiple keys`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1,v2:$key2", activeKeyId = "v2", hashKey = "h")
        val ring = PiiKeyRing(props)

        assertThat(ring.activeKeyId).isEqualTo("v2")
        assertThat(ring.keyFor("v1")).isNotNull
        assertThat(ring.keyFor("v2")).isNotNull
    }

    @Test
    fun `fails fast when activeKeyId is not in keys`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1", activeKeyId = "v9", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("v9")
    }

    @Test
    fun `fails fast when a key entry is malformed`() {
        val props = PiiEncryptionProperties(keys = "bad-entry", activeKeyId = "v1", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `fails fast when a key is not 32 bytes`() {
        val shortKey = "QUFB"   // base64 of "AAA", 3 bytes
        val props = PiiEncryptionProperties(keys = "v1:$shortKey", activeKeyId = "v1", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiKeyRingTest"
```

Expected: FAIL (class does not exist).

- [ ] **Step 3: Implement `PiiKeyRing`**

```kotlin
package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Component
class PiiKeyRing(properties: PiiEncryptionProperties) {

    val activeKeyId: String = properties.activeKeyId
    private val keys: Map<String, SecretKey>

    init {
        val parsed = properties.keys.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { entry ->
                val parts = entry.split(":", limit = 2)
                check(parts.size == 2 && parts[0].isNotEmpty()) {
                    "암호화 키 항목 형식이 올바르지 않습니다: $entry"
                }
                val bytes = Base64.getDecoder().decode(parts[1])
                check(bytes.size == 32) { "AES-256 키는 32바이트여야 합니다 (id=${parts[0]})" }
                parts[0] to SecretKeySpec(bytes, "AES") as SecretKey
            }

        check(parsed.containsKey(activeKeyId)) {
            "active-key-id '$activeKeyId'가 KEYS 목록에 없습니다."
        }
        keys = parsed
    }

    fun keyFor(keyId: String): SecretKey? = keys[keyId]
    fun activeKey(): SecretKey = keys.getValue(activeKeyId)
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiKeyRingTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/security/pii/PiiKeyRing.kt \
        src/test/kotlin/org/happyzion/api/common/security/pii/PiiKeyRingTest.kt
git commit -m "feat(pii): add PiiKeyRing with fail-fast validation"
```

---

## Task 5: `PiiEncryptor` (AES-256-GCM)

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/PiiEncryptor.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/security/pii/PiiEncryptorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PiiEncryptorTest {

    private val key1 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val key2 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="

    private fun ringWith(active: String, keys: String) =
        PiiKeyRing(PiiEncryptionProperties(keys = keys, activeKeyId = active, hashKey = "h"))

    @Test
    fun `encrypt decrypt roundtrip`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        val ciphertext = enc.encrypt("김철수")

        assertThat(ciphertext).startsWith("v1:")
        assertThat(enc.decrypt(ciphertext)).isEqualTo("김철수")
    }

    @Test
    fun `same plaintext yields different ciphertext each time (IV randomness)`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        val c1 = enc.encrypt("김철수")
        val c2 = enc.encrypt("김철수")

        assertThat(c1).isNotEqualTo(c2)
        assertThat(enc.decrypt(c1)).isEqualTo("김철수")
        assertThat(enc.decrypt(c2)).isEqualTo("김철수")
    }

    @Test
    fun `decrypt can read ciphertext encrypted with old key id`() {
        val encV1 = PiiEncryptor(ringWith("v1", "v1:$key1"))
        val c = encV1.encrypt("hello")

        val encV2 = PiiEncryptor(ringWith("v2", "v1:$key1,v2:$key2"))

        assertThat(encV2.decrypt(c)).isEqualTo("hello")
    }

    @Test
    fun `decrypt rejects tampered ciphertext`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))
        val c = enc.encrypt("hello")
        val tampered = c.dropLast(2) + "XX"

        assertThatThrownBy { enc.decrypt(tampered) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `decrypt rejects unknown key id`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        assertThatThrownBy { enc.decrypt("v9:AAAA") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("v9")
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiEncryptorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `PiiEncryptor`**

```kotlin
package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@Component
class PiiEncryptor(private val keyRing: PiiKeyRing) {

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keyRing.activeKey(), GCMParameterSpec(TAG_BIT_LENGTH, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = iv + ciphertext
        return "${keyRing.activeKeyId}:${Base64.getEncoder().encodeToString(payload)}"
    }

    fun decrypt(token: String): String {
        val sep = token.indexOf(':')
        check(sep > 0) { "암호문 포맷이 올바르지 않습니다." }
        val keyId = token.substring(0, sep)
        val key = keyRing.keyFor(keyId)
            ?: throw IllegalStateException("알 수 없는 키 ID: $keyId")
        val payload = Base64.getDecoder().decode(token.substring(sep + 1))
        check(payload.size > IV_LENGTH) { "암호문 길이가 올바르지 않습니다." }
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val ct = payload.copyOfRange(IV_LENGTH, payload.size)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BIT_LENGTH, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (ex: Exception) {
            throw IllegalStateException("암호문 복호에 실패했습니다.", ex)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BIT_LENGTH = 128
    }
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiEncryptorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/security/pii/PiiEncryptor.kt \
        src/test/kotlin/org/happyzion/api/common/security/pii/PiiEncryptorTest.kt
git commit -m "feat(pii): add AES-256-GCM encryptor with key ring lookup"
```

---

## Task 6: `PiiHasher` + `MemberSearchKeyNormalizer`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/PiiHasher.kt`
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/MemberSearchKeyNormalizer.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/security/pii/PiiHasherTest.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/security/pii/MemberSearchKeyNormalizerTest.kt`

- [ ] **Step 1: Write failing tests**

`PiiHasherTest.kt`:

```kotlin
package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PiiHasherTest {

    private val hashKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private fun hasher() = PiiHasher(
        PiiEncryptionProperties(keys = "v1:$hashKey", activeKeyId = "v1", hashKey = hashKey)
    )

    @Test
    fun `same input produces same hash`() {
        val h = hasher()
        assertThat(h.hash("김철수")).isEqualTo(h.hash("김철수"))
    }

    @Test
    fun `different input produces different hash`() {
        val h = hasher()
        assertThat(h.hash("김철수")).isNotEqualTo(h.hash("김영희"))
    }

    @Test
    fun `produces 64 hex char output`() {
        val h = hasher().hash("test")
        assertThat(h).matches("[0-9a-f]{64}")
    }
}
```

`MemberSearchKeyNormalizerTest.kt`:

```kotlin
package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemberSearchKeyNormalizerTest {

    private val n = MemberSearchKeyNormalizer()

    @Test
    fun `forStoredName strips whitespace and lowercases`() {
        assertThat(n.forStoredName("  김 철 수 ")).isEqualTo("김철수")
        assertThat(n.forStoredName("John Doe")).isEqualTo("johndoe")
    }

    @Test
    fun `forStoredName rejects blank after normalization`() {
        assertThatThrownBy { n.forStoredName("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `forStoredPhone extracts digits and accepts 9+ digits`() {
        assertThat(n.forStoredPhone("010-1234-5678")).isEqualTo("01012345678")
    }

    @Test
    fun `forStoredPhone rejects under 9 digits`() {
        assertThatThrownBy { n.forStoredPhone("12345") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `forPhoneQuery returns Last4 for exactly 4 digits`() {
        assertThat(n.forPhoneQuery("5678")).isEqualTo(PhoneQueryKey.Last4("5678"))
    }

    @Test
    fun `forPhoneQuery returns Full for 9+ digits, with last4 part too`() {
        assertThat(n.forPhoneQuery("010-1234-5678"))
            .isEqualTo(PhoneQueryKey.Full(full = "01012345678", last4 = "5678"))
    }

    @Test
    fun `forPhoneQuery returns null for ambiguous lengths`() {
        assertThat(n.forPhoneQuery("12345")).isNull()
        assertThat(n.forPhoneQuery("")).isNull()
    }

    @Test
    fun `last4 of full phone`() {
        assertThat(n.last4OfStored("01012345678")).isEqualTo("5678")
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiHasherTest" --tests "org.happyzion.api.common.security.pii.MemberSearchKeyNormalizerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `PiiHasher`**

```kotlin
package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class PiiHasher(properties: PiiEncryptionProperties) {

    private val key = SecretKeySpec(Base64.getDecoder().decode(properties.hashKey), "HmacSHA256").also {
        check(it.encoded.size == 32) { "HMAC 키는 32바이트여야 합니다." }
    }

    fun hash(input: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        val out = mac.doFinal(input.toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: Implement `MemberSearchKeyNormalizer`**

```kotlin
package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.text.Normalizer

sealed class PhoneQueryKey {
    data class Last4(val last4: String) : PhoneQueryKey()
    data class Full(val full: String, val last4: String) : PhoneQueryKey()
}

@Component
class MemberSearchKeyNormalizer {

    fun forStoredName(raw: String): String {
        val out = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(WHITESPACE, "")
            .lowercase()
        require(out.isNotEmpty()) { "이름은 비어 있을 수 없습니다." }
        return out
    }

    fun forNameQuery(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val out = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(WHITESPACE, "")
            .lowercase()
        return out.ifEmpty { null }
    }

    fun forStoredPhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        require(digits.length >= 9) { "전화번호는 9자리 이상이어야 합니다." }
        return digits
    }

    fun forPhoneQuery(raw: String?): PhoneQueryKey? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 4 -> PhoneQueryKey.Last4(digits)
            digits.length >= 9 -> PhoneQueryKey.Full(full = digits, last4 = digits.takeLast(4))
            else -> null
        }
    }

    fun last4OfStored(storedPhone: String): String? =
        if (storedPhone.length >= 8) storedPhone.takeLast(4) else null

    companion object {
        private val WHITESPACE = Regex("[\\p{Z}\\s]+")
    }
}
```

- [ ] **Step 5: Run tests, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.PiiHasherTest" --tests "org.happyzion.api.common.security.pii.MemberSearchKeyNormalizerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/security/pii/PiiHasher.kt \
        src/main/kotlin/org/happyzion/api/common/security/pii/MemberSearchKeyNormalizer.kt \
        src/test/kotlin/org/happyzion/api/common/security/pii/PiiHasherTest.kt \
        src/test/kotlin/org/happyzion/api/common/security/pii/MemberSearchKeyNormalizerTest.kt
git commit -m "feat(pii): add HMAC hasher and member search key normalizer"
```

---

## Task 7: JPA encryption converters + SpringBeanContainer config

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/EncryptedStringConverter.kt`
- Create: `src/main/kotlin/org/happyzion/api/common/security/pii/EncryptedLocalDateConverter.kt`
- Create: `src/main/kotlin/org/happyzion/api/common/config/HibernateSpringBeanContainerConfig.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/security/pii/EncryptedStringConverterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EncryptedStringConverterTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val encryptor = PiiEncryptor(
        PiiKeyRing(PiiEncryptionProperties(keys = "v1:$key", activeKeyId = "v1", hashKey = key))
    )

    @Test
    fun `null plaintext converts to null column value`() {
        val c = EncryptedStringConverter(encryptor)

        assertThat(c.convertToDatabaseColumn(null)).isNull()
        assertThat(c.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `roundtrip non-null`() {
        val c = EncryptedStringConverter(encryptor)

        val db = c.convertToDatabaseColumn("김철수")
        assertThat(db).startsWith("v1:")
        assertThat(c.convertToEntityAttribute(db)).isEqualTo("김철수")
    }

    @Test
    fun `empty string roundtrips as empty`() {
        val c = EncryptedStringConverter(encryptor)

        val db = c.convertToDatabaseColumn("")
        assertThat(db).startsWith("v1:")
        assertThat(c.convertToEntityAttribute(db)).isEqualTo("")
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.EncryptedStringConverterTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement converters**

```kotlin
// EncryptedStringConverter.kt
package org.happyzion.api.common.security.pii

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Component
@Converter
class EncryptedStringConverter(private val encryptor: PiiEncryptor) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let(encryptor::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let(encryptor::decrypt)
}
```

```kotlin
// EncryptedLocalDateConverter.kt
package org.happyzion.api.common.security.pii

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
@Converter
class EncryptedLocalDateConverter(private val encryptor: PiiEncryptor) : AttributeConverter<LocalDate, String> {

    override fun convertToDatabaseColumn(attribute: LocalDate?): String? =
        attribute?.format(DateTimeFormatter.ISO_LOCAL_DATE)?.let(encryptor::encrypt)

    override fun convertToEntityAttribute(dbData: String?): LocalDate? =
        dbData?.let(encryptor::decrypt)?.let(LocalDate::parse)
}
```

- [ ] **Step 4: Wire `SpringBeanContainer`**

```kotlin
// HibernateSpringBeanContainerConfig.kt
package org.happyzion.api.common.config

import org.hibernate.cfg.AvailableSettings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.orm.hibernate5.SpringBeanContainer

@Configuration
class HibernateSpringBeanContainerConfig(
    private val beanFactory: ConfigurableListableBeanFactory,
) : HibernatePropertiesCustomizer {

    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.BEAN_CONTAINER] = SpringBeanContainer(beanFactory)
    }
}
```

> If `org.springframework.orm.hibernate5.SpringBeanContainer` is not on the classpath in this Spring Boot 3.5 + Hibernate 6 environment, use `org.hibernate.engine.spi.ManagedBeanRegistry` integration instead by importing `org.hibernate.resource.beans.container.spi.BeanContainer` and providing `SpringContextBootstrap`. Run the test in Step 5; if it fails on the import, switch to the Hibernate 6 import path before proceeding.

- [ ] **Step 5: Run test, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.common.security.pii.EncryptedStringConverterTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/security/pii/EncryptedStringConverter.kt \
        src/main/kotlin/org/happyzion/api/common/security/pii/EncryptedLocalDateConverter.kt \
        src/main/kotlin/org/happyzion/api/common/config/HibernateSpringBeanContainerConfig.kt \
        src/test/kotlin/org/happyzion/api/common/security/pii/EncryptedStringConverterTest.kt
git commit -m "feat(pii): add JPA attribute converters and Hibernate SpringBeanContainer config"
```

---

## Task 8: `AdminAccountGuard`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/adminaccount/application/AdminAccountGuard.kt`
- Create: `src/test/kotlin/org/happyzion/api/adminaccount/application/AdminAccountGuardTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package org.happyzion.api.adminaccount.application

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.OffsetDateTime
import java.util.Optional

class AdminAccountGuardTest {

    @Test
    fun `passes when admin is active`() {
        val admin = activeAdmin(id = 1)
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.of(admin) }
        AdminAccountGuard(repo).verify(1)
    }

    @Test
    fun `throws Unauthorized when admin not found`() {
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.empty() }

        assertThatThrownBy { AdminAccountGuard(repo).verify(1) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `throws Forbidden when admin is inactive`() {
        val inactive = activeAdmin(id = 1).also { it.deactivate() }
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.of(inactive) }

        assertThatThrownBy { AdminAccountGuard(repo).verify(1) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    private fun activeAdmin(id: Long) = AdminAccount(
        id = id,
        username = "u",
        displayName = "U",
        passwordHash = "x",
        role = AdminAccountRole.ADMIN,
        active = true,
        lastLoginAt = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
```

> Inspect `AdminAccount.kt` first to confirm the constructor signature and `deactivate()` / `active` accessor names. If they differ, adjust the test fixture accordingly.

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.adminaccount.application.AdminAccountGuardTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `AdminAccountGuard`**

```kotlin
package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Component

@Component
class AdminAccountGuard(private val adminAccountRepository: AdminAccountRepository) {

    fun verify(actorId: Long) {
        val admin = adminAccountRepository.findById(actorId).orElse(null)
            ?: throw UnauthorizedException("관리자 계정을 찾을 수 없습니다.")
        if (!admin.active) throw ForbiddenException("비활성 관리자입니다.")
    }
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.adminaccount.application.AdminAccountGuardTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/adminaccount/application/AdminAccountGuard.kt \
        src/test/kotlin/org/happyzion/api/adminaccount/application/AdminAccountGuardTest.kt
git commit -m "feat(adminaccount): add AdminAccountGuard for active-admin checks"
```

---

## Task 9: `MemberPhotoUploadPolicy` + `UploadAdminController` server-side override

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/common/security/MemberPhotoUploadPolicy.kt`
- Modify: `src/main/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminController.kt`
- Modify: `src/test/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminControllerTest.kt` (or create if missing)

- [ ] **Step 1: Create the policy object**

```kotlin
package org.happyzion.api.common.security

object MemberPhotoUploadPolicy {
    val ALLOWED_MIME: List<String> = listOf("image/jpeg", "image/png", "image/webp")
    const val MAX_BYTES: Long = 5L * 1024 * 1024
}
```

- [ ] **Step 2: Write/extend controller test**

Inspect `src/test/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminControllerTest.kt`. If absent, create one. Add cases:

```kotlin
@Test
fun `MEMBER_PHOTO token issuance overrides client mime and size with server policy`() {
    // 클라이언트가 application/pdf, 50MB를 보내도 토큰엔 image 3종 + 5MiB만 저장됨
    // mock uploadTokenService.issueToken capture (actorId, MEMBER_PHOTO, 5*1024*1024, listOf("image/jpeg","image/png","image/webp"))
}

@Test
fun `MEMBER_PHOTO token issuance for inactive admin returns 403`() {
    // adminAccountGuard.verify throws ForbiddenException; controller maps to 403 via GlobalExceptionHandler
}

@Test
fun `INLINE_IMAGE token issuance keeps client mime and size (no override)`() {
    // 기존 동작 회귀 방지
}
```

- [ ] **Step 3: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.board.interfaces.api.UploadAdminControllerTest"
```

Expected: FAIL.

- [ ] **Step 4: Modify the controller**

Replace `UploadAdminController.issueToken`:

```kotlin
@AdminAuthRequired
@PostMapping("/token")
fun issueToken(
    @RequestAttribute("adminAccountId") actorId: Long,
    @Valid @RequestBody request: UploadTokenIssueRequest,
): UploadTokenIssueResponse {
    val (effectiveMimes, effectiveMax) = if (request.kind == PostAssetKind.MEMBER_PHOTO) {
        adminAccountGuard.verify(actorId)
        MemberPhotoUploadPolicy.ALLOWED_MIME to MemberPhotoUploadPolicy.MAX_BYTES
    } else {
        request.allowedMimeTypes to request.maxByteSize
    }

    val result = uploadTokenService.issueToken(
        actorId = actorId,
        kind = request.kind,
        maxByteSize = effectiveMax,
        allowedMimeTypes = effectiveMimes,
    )

    return UploadTokenIssueResponse(rawToken = result.rawToken)
}
```

Add `private val adminAccountGuard: AdminAccountGuard` to the constructor params. Add the import.

- [ ] **Step 5: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.board.interfaces.api.UploadAdminControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/security/MemberPhotoUploadPolicy.kt \
        src/main/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminController.kt \
        src/test/kotlin/org/happyzion/api/board/interfaces/api/UploadAdminControllerTest.kt
git commit -m "feat(board): enforce server-side MEMBER_PHOTO upload policy and active-admin guard"
```

---

## Task 10: `AttachmentStorage.load` + path-traversal guard + kind prefix

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/board/application/AttachmentStorage.kt`
- Modify: `src/main/kotlin/org/happyzion/api/board/application/LocalAttachmentStorage.kt`
- Modify or create: `src/test/kotlin/org/happyzion/api/board/application/LocalAttachmentStorageTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `LocalAttachmentStorageTest`:

```kotlin
@Test
fun `load returns Resource for stored file`() {
    val (storage, root) = newStorage()
    val rel = "test/file.txt"
    val abs = root.resolve(rel)
    abs.parent.toFile().mkdirs()
    abs.toFile().writeText("hi")

    val resource = storage.load(rel)

    assertThat(resource.contentAsByteArray.toString(Charsets.UTF_8)).isEqualTo("hi")
}

@Test
fun `load rejects path traversal with NotFoundException`() {
    val (storage, _) = newStorage()

    assertThatThrownBy { storage.load("../../etc/passwd") }
        .isInstanceOf(NotFoundException::class.java)
}

@Test
fun `load throws NotFoundException when file is missing`() {
    val (storage, _) = newStorage()

    assertThatThrownBy { storage.load("doesnotexist.png") }
        .isInstanceOf(NotFoundException::class.java)
}

@Test
fun `MEMBER_PHOTO storedPath uses member-photos prefix`() {
    val (storage, _) = newStorage()
    val multipart = jpegMultipart()

    val stored = storage.store(multipart, PostAssetKind.MEMBER_PHOTO, maxByteSize = 1_000_000)

    assertThat(stored.storedPath).startsWith("member-photos/")
}
```

(Helper `newStorage()` already exists in the test class — see existing tests for pattern; `jpegMultipart()` produces a small valid JPEG.)

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.board.application.LocalAttachmentStorageTest"
```

Expected: FAIL.

- [ ] **Step 3: Modify `AttachmentStorage` interface**

```kotlin
package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAssetKind
import org.springframework.core.io.Resource
import org.springframework.web.multipart.MultipartFile

interface AttachmentStorage {
    fun store(file: MultipartFile, kind: PostAssetKind, maxByteSize: Long): StoredAttachment
    fun delete(storedPath: String)
    fun load(storedPath: String): Resource
}

data class StoredAttachment(
    val storedPath: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
)
```

- [ ] **Step 4: Implement `LocalAttachmentStorage.load` + kind-aware `buildStoredPath`**

In `LocalAttachmentStorage.kt`, add to class body:

```kotlin
override fun load(storedPath: String): Resource {
    val target = rootPath.resolve(storedPath).normalize()
    if (!target.startsWith(rootPath.toAbsolutePath().normalize())
        && !target.startsWith(rootPath.normalize())) {
        throw NotFoundException("자산을 찾을 수 없습니다.")
    }
    if (!Files.isRegularFile(target)) {
        throw NotFoundException("자산을 찾을 수 없습니다.")
    }
    return UrlResource(target.toUri())
}
```

Add imports: `org.springframework.core.io.Resource`, `org.springframework.core.io.UrlResource`, `org.happyzion.api.common.error.NotFoundException`.

Modify `store(...)` to pass `kind` into `buildStoredPath`:

```kotlin
val storedPath = buildStoredPath(kind, extension)
```

Modify `buildStoredPath`:

```kotlin
private fun buildStoredPath(kind: PostAssetKind, extension: String): String {
    val now = LocalDate.now()
    val prefix = if (kind == PostAssetKind.MEMBER_PHOTO) "member-photos/" else ""
    return "${prefix}%04d/%02d/%s.%s".format(
        now.year, now.monthValue, UUID.randomUUID().toString(), extension
    )
}
```

- [ ] **Step 5: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.board.application.LocalAttachmentStorageTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/board/application/AttachmentStorage.kt \
        src/main/kotlin/org/happyzion/api/board/application/LocalAttachmentStorage.kt \
        src/test/kotlin/org/happyzion/api/board/application/LocalAttachmentStorageTest.kt
git commit -m "feat(board): add AttachmentStorage.load with traversal guard and MEMBER_PHOTO prefix"
```

---

## Task 11: `UploadAssetService` MEMBER_PHOTO active guard

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/board/application/UploadAssetService.kt`
- Modify: `src/test/kotlin/org/happyzion/api/board/application/UploadAssetServiceTest.kt`

- [ ] **Step 1: Add failing test**

```kotlin
@Test
fun `MEMBER_PHOTO upload by inactive admin throws Forbidden and deletes stored file`() {
    // mock uploadTokenService.validateAndConsume returns actorId=1, kind=MEMBER_PHOTO
    // mock adminAccountGuard.verify(1) throws ForbiddenException
    // mock attachmentStorage.store returns a StoredAttachment
    // expect: ForbiddenException + attachmentStorage.delete called with the stored path
}

@Test
fun `non MEMBER_PHOTO upload does not call adminAccountGuard`() {
    // INLINE_IMAGE flow — guard.verify must not be invoked (verify(never))
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.board.application.UploadAssetServiceTest"
```

Expected: FAIL.

- [ ] **Step 3: Inject guard and call it**

In `UploadAssetService.kt`, add `private val adminAccountGuard: AdminAccountGuard` to constructor. Inside `upload(...)`, after `val validation = uploadTokenService.validateAndConsume(...)` and before `attachmentStorage.store(...)` is OK, but the actorId is only known after validation. Place the guard call **after `storedAttachment` is produced and before `postAssetRepository.save`** so that on failure we go through the existing `attachmentStorage.delete` rollback path:

```kotlin
val storedAttachment = attachmentStorage.store(file = file, kind = kind, maxByteSize = validation.maxByteSize)

if (kind == PostAssetKind.MEMBER_PHOTO) {
    try {
        adminAccountGuard.verify(validation.actorId)
    } catch (ex: RuntimeException) {
        attachmentStorage.delete(storedAttachment.storedPath)
        throw ex
    }
}

val asset = PostAsset(/* ... unchanged ... */)
```

Add import for `AdminAccountGuard`.

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.board.application.UploadAssetServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/board/application/UploadAssetService.kt \
        src/test/kotlin/org/happyzion/api/board/application/UploadAssetServiceTest.kt
git commit -m "feat(board): enforce active-admin on MEMBER_PHOTO upload, rollback file on failure"
```

---

## Task 12: `RequestLoggingFilter` PII redaction

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/common/logging/RequestLoggingFilter.kt`
- Create: `src/test/kotlin/org/happyzion/api/common/logging/RequestLoggingFilterTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package org.happyzion.api.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class RequestLoggingFilterTest {

    private val filter = RequestLoggingFilter()

    @Test
    fun `redacts name and phone in members path`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=김철수&phone=01012345678&page=0",
        )
        assertThat(out).contains("name=[REDACTED]")
        assertThat(out).contains("phone=[REDACTED]")
        assertThat(out).contains("page=0")
        assertThat(out).doesNotContain("김철수")
        assertThat(out).doesNotContain("01012345678")
    }

    @Test
    fun `redaction is case insensitive on key`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "NAME=김철수&Phone=01012345678",
        )
        assertThat(out).doesNotContain("김철수")
        assertThat(out).doesNotContain("01012345678")
    }

    @Test
    fun `redacts duplicate parameters`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=A&name=B",
        )
        assertThat(out).doesNotContain("=A")
        assertThat(out).doesNotContain("=B")
    }

    @Test
    fun `redacts url-encoded values without decoding`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=%EA%B9%80%EC%B2%A0%EC%88%98",
        )
        assertThat(out).doesNotContain("%EA%B9%80")
        assertThat(out).contains("name=[REDACTED]")
    }

    @Test
    fun `empty values stay as-is`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=&phone=",
        )
        assertThat(out).contains("name=")
        assertThat(out).contains("phone=")
        // No leakage anyway, but redaction is unnecessary
    }

    @Test
    fun `applies to nested members paths (photo, audit-logs)`() {
        val out1 = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members/123/photo",
            query = "name=김",
        )
        val out2 = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members/123/audit-logs",
            query = "name=김",
        )
        assertThat(out1).doesNotContain("김")
        assertThat(out2).doesNotContain("김")
    }

    @Test
    fun `does not redact on non-members paths`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/boards/notice/posts",
            query = "title=hello&name=admin",
        )
        assertThat(out).contains("title=hello")
        assertThat(out).contains("name=admin")
    }
}
```

Test relies on a small extracted seam. We add `buildRequestPathForTesting(uri, query)` to the filter for test purposes.

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.common.logging.RequestLoggingFilterTest"
```

Expected: FAIL.

- [ ] **Step 3: Modify the filter**

Replace `buildRequestPath` in `RequestLoggingFilter.kt`:

```kotlin
private fun buildRequestPath(request: HttpServletRequest): String =
    buildRequestPathForTesting(request.requestURI, request.queryString)

internal fun buildRequestPathForTesting(uri: String, query: String?): String {
    val q = query ?: return uri
    return if (uri.startsWith("/api/v1/admin/members")) {
        "$uri?${redactSensitive(q)}"
    } else {
        "$uri?$q"
    }
}

private fun redactSensitive(query: String): String {
    val keys = setOf("name", "phone")
    return query.split('&').joinToString("&") { token ->
        val eq = token.indexOf('=')
        if (eq <= 0) return@joinToString token
        val key = token.substring(0, eq)
        val value = token.substring(eq + 1)
        if (value.isEmpty()) return@joinToString token
        if (keys.contains(key.lowercase())) "$key=[REDACTED]" else token
    }
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.common.logging.RequestLoggingFilterTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/logging/RequestLoggingFilter.kt \
        src/test/kotlin/org/happyzion/api/common/logging/RequestLoggingFilterTest.kt
git commit -m "feat(logging): redact name/phone query params on /api/v1/admin/members paths"
```

---

## Task 13: CORS mapping + nginx deny rule documentation

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/common/config/WebConfig.kt`
- Modify: `deploy/nginx/api.happyzion.com.conf`

- [ ] **Step 1: Add CORS mapping for members**

In `WebConfig.addCorsMappings(...)`, after the existing `/api/v1/admin/site/main-video` block, append:

```kotlin
registry.addMapping("/api/v1/admin/members/**")
    .allowedOrigins(*allowedOrigins.toTypedArray())
    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    .allowedHeaders("Content-Type", "Authorization")
    .allowCredentials(false)
    .maxAge(600)
```

- [ ] **Step 2: Modify nginx config**

In `deploy/nginx/api.happyzion.com.conf`, **above** the existing `location /upload/` block (so it matches first), insert:

```nginx
    location ^~ /upload/member-photos/ {
        return 404;
    }
```

- [ ] **Step 3: Compile to verify no breakage**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/common/config/WebConfig.kt \
        deploy/nginx/api.happyzion.com.conf
git commit -m "feat(infra): CORS for /admin/members + nginx deny for /upload/member-photos"
```

---

## Task 14: Member domain — enums and entities

**Files:**
- Create all under `src/main/kotlin/org/happyzion/api/member/domain/`:
  `Sex.kt`, `BirthCalendar.kt`, `ChurchMemberStatus.kt`, `FaithStage.kt`, `ChurchMemberOffice.kt`, `AuditAction.kt`, `ChurchMember.kt`, `ChurchMemberFaith.kt`, `ChurchMemberAuditLog.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/domain/ChurchMemberTest.kt`

- [ ] **Step 1: Create enums**

```kotlin
// Sex.kt
package org.happyzion.api.member.domain
enum class Sex { M, F }

// BirthCalendar.kt
package org.happyzion.api.member.domain
enum class BirthCalendar { SOLAR, LUNAR }

// ChurchMemberStatus.kt
package org.happyzion.api.member.domain
enum class ChurchMemberStatus {
    ACTIVE, NEW, RESTING, LONG_ABSENT, TRANSFERRED_OUT, DECEASED, REMOVED;
    companion object {
        val ACTIVE_SET: Set<ChurchMemberStatus> =
            setOf(ACTIVE, NEW, RESTING, LONG_ABSENT, TRANSFERRED_OUT)
    }
}

// FaithStage.kt
package org.happyzion.api.member.domain
enum class FaithStage { SEEKER, NEW_COMER, SETTLED, GROWING, DISCIPLE, MINISTER, LEADER }

// ChurchMemberOffice.kt
package org.happyzion.api.member.domain
enum class ChurchMemberOffice {
    LAY, DEACON_TEMP, DEACON, GWONSA, ELDER, ELDER_EMERITUS, EVANGELIST, PASTOR
}

// AuditAction.kt
package org.happyzion.api.member.domain
enum class AuditAction { CREATE, UPDATE, DELETE }
```

- [ ] **Step 2: Write the failing domain test**

```kotlin
package org.happyzion.api.member.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiHasher
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChurchMemberTest {

    private val hashKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val hasher = PiiHasher(
        PiiEncryptionProperties(keys = "v1:$hashKey", activeKeyId = "v1", hashKey = hashKey)
    )
    private val normalizer = MemberSearchKeyNormalizer()

    @Test
    fun `factory creates active member with correctly computed hashes`() {
        val m = newMember(name = "  김 철수 ", phone = "010-1234-5678")

        assertThat(m.name).isEqualTo("  김 철수 ")          // 도메인은 평문 보존
        assertThat(m.phone).isEqualTo("010-1234-5678")
        assertThat(m.nameHash).isEqualTo(hasher.hash("김철수"))
        assertThat(m.phoneHash).isEqualTo(hasher.hash("01012345678"))
        assertThat(m.phoneLast4Hash).isEqualTo(hasher.hash("5678"))
        assertThat(m.status).isEqualTo(ChurchMemberStatus.ACTIVE)
    }

    @Test
    fun `rename updates name and nameHash atomically`() {
        val m = newMember(name = "김철수")
        m.rename("이영희", hasher, normalizer)

        assertThat(m.name).isEqualTo("이영희")
        assertThat(m.nameHash).isEqualTo(hasher.hash("이영희"))
    }

    @Test
    fun `markRemoved sets status to REMOVED`() {
        val m = newMember()
        m.markRemoved()
        assertThat(m.status).isEqualTo(ChurchMemberStatus.REMOVED)
    }

    @Test
    fun `directly setting status to REMOVED is forbidden`() {
        val m = newMember()
        assertThatThrownBy { m.changeStatus(ChurchMemberStatus.REMOVED) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `office_appointed_at cannot be in the future`() {
        val m = newMember()
        assertThatThrownBy { m.appointOffice(ChurchMemberOffice.DEACON, LocalDate.now().plusDays(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `linkPhoto sets photoAssetId, unlinkPhoto clears it`() {
        val m = newMember()
        m.linkPhoto(99L)
        assertThat(m.photoAssetId).isEqualTo(99L)
        m.unlinkPhoto()
        assertThat(m.photoAssetId).isNull()
    }

    private fun newMember(name: String = "김철수", phone: String = "01012345678") =
        ChurchMember.create(
            name = name, phone = phone, email = null,
            birthDate = LocalDate.of(1990, 1, 1),
            birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M,
            address = "서울",
            addressDetail = null,
            job = null,
            cellLabel = null,
            status = ChurchMemberStatus.ACTIVE,
            faithStage = null,
            office = ChurchMemberOffice.LAY,
            officeAppointedAt = null,
            registeredAt = LocalDate.of(2024, 1, 1),
            memo = null,
            hasher = hasher,
            normalizer = normalizer,
        )
}
```

- [ ] **Step 3: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.member.domain.ChurchMemberTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `ChurchMember`**

```kotlin
package org.happyzion.api.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.happyzion.api.common.security.pii.EncryptedLocalDateConverter
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiHasher
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member")
class ChurchMember(
    @Column(name = "name_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var name: String,

    @Column(name = "name_hash", nullable = false, length = 64)
    var nameHash: String,

    @Column(name = "phone_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var phone: String,

    @Column(name = "phone_hash", nullable = false, length = 64)
    var phoneHash: String,

    @Column(name = "phone_last4_hash", length = 64)
    var phoneLast4Hash: String?,

    @Column(name = "email_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var email: String?,

    @Column(name = "address_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var address: String,

    @Column(name = "address_detail_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var addressDetail: String?,

    @Column(name = "job_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var job: String?,

    @Column(name = "memo_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var memo: String?,

    @Column(name = "birth_date_enc", nullable = false)
    @Convert(converter = EncryptedLocalDateConverter::class)
    var birthDate: LocalDate,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 1)
    var sex: Sex,

    @Enumerated(EnumType.STRING) @Column(name = "birth_calendar", nullable = false, length = 10)
    var birthCalendar: BirthCalendar,

    @Column(name = "photo_asset_id")
    var photoAssetId: Long?,

    @Column(name = "cell_label", length = 120)
    var cellLabel: String?,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var status: ChurchMemberStatus,

    @Enumerated(EnumType.STRING) @Column(name = "faith_stage", length = 32)
    var faithStage: FaithStage?,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var office: ChurchMemberOffice,

    @Column(name = "office_appointed_at")
    var officeAppointedAt: LocalDate?,

    @Column(name = "registered_at", nullable = false)
    var registeredAt: LocalDate,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
        private set

    fun rename(newName: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer) {
        this.name = newName
        this.nameHash = hasher.hash(normalizer.forStoredName(newName))
    }

    fun changePhone(newPhone: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer) {
        val stored = normalizer.forStoredPhone(newPhone)
        this.phone = newPhone
        this.phoneHash = hasher.hash(stored)
        this.phoneLast4Hash = normalizer.last4OfStored(stored)?.let(hasher::hash)
    }

    fun changeAddress(newAddress: String, newDetail: String?) {
        this.address = newAddress
        this.addressDetail = newDetail
    }

    fun changeEmail(newEmail: String?) { this.email = newEmail }
    fun changeJob(newJob: String?) { this.job = newJob }
    fun changeMemo(newMemo: String?) { this.memo = newMemo }
    fun changeBirth(newDate: LocalDate, calendar: BirthCalendar) {
        this.birthDate = newDate; this.birthCalendar = calendar
    }
    fun changeSex(newSex: Sex) { this.sex = newSex }
    fun changeCellLabel(newLabel: String?) { this.cellLabel = newLabel }
    fun changeFaithStage(newStage: FaithStage?) { this.faithStage = newStage }
    fun changeRegisteredAt(newDate: LocalDate) { this.registeredAt = newDate }

    fun changeStatus(newStatus: ChurchMemberStatus) {
        require(newStatus != ChurchMemberStatus.REMOVED) {
            "직접 REMOVED 상태로 변경할 수 없습니다. softDelete 경로를 사용하세요."
        }
        this.status = newStatus
    }
    fun markRemoved() { this.status = ChurchMemberStatus.REMOVED }

    fun appointOffice(newOffice: ChurchMemberOffice, at: LocalDate?) {
        if (at != null) require(!at.isAfter(LocalDate.now())) { "직분 임명일은 미래일 수 없습니다." }
        this.office = newOffice
        this.officeAppointedAt = at
    }

    fun linkPhoto(assetId: Long) { this.photoAssetId = assetId }
    fun unlinkPhoto() { this.photoAssetId = null }

    companion object {
        fun create(
            name: String, phone: String, email: String?,
            birthDate: LocalDate, birthCalendar: BirthCalendar, sex: Sex,
            address: String, addressDetail: String?, job: String?,
            cellLabel: String?, status: ChurchMemberStatus, faithStage: FaithStage?,
            office: ChurchMemberOffice, officeAppointedAt: LocalDate?,
            registeredAt: LocalDate, memo: String?,
            hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer,
        ): ChurchMember {
            require(status != ChurchMemberStatus.REMOVED) { "REMOVED 상태로 신규 생성할 수 없습니다." }
            if (officeAppointedAt != null) require(!officeAppointedAt.isAfter(LocalDate.now())) {
                "직분 임명일은 미래일 수 없습니다."
            }
            val storedPhone = normalizer.forStoredPhone(phone)
            return ChurchMember(
                name = name,
                nameHash = hasher.hash(normalizer.forStoredName(name)),
                phone = phone,
                phoneHash = hasher.hash(storedPhone),
                phoneLast4Hash = normalizer.last4OfStored(storedPhone)?.let(hasher::hash),
                email = email,
                address = address,
                addressDetail = addressDetail,
                job = job,
                memo = memo,
                birthDate = birthDate,
                sex = sex,
                birthCalendar = birthCalendar,
                photoAssetId = null,
                cellLabel = cellLabel,
                status = status,
                faithStage = faithStage,
                office = office,
                officeAppointedAt = officeAppointedAt,
                registeredAt = registeredAt,
            )
        }
    }
}
```

- [ ] **Step 5: Implement `ChurchMemberFaith` and `ChurchMemberAuditLog`**

```kotlin
// ChurchMemberFaith.kt
package org.happyzion.api.member.domain

import jakarta.persistence.*
import org.happyzion.api.common.security.pii.EncryptedLocalDateConverter
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member_faith")
class ChurchMemberFaith(
    @Id @Column(name = "church_member_id") val churchMemberId: Long,

    @Column(name = "confess_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var confessDate: LocalDate?,
    @Column(name = "learning_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var learningDate: LocalDate?,
    @Column(name = "baptism_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var baptismDate: LocalDate?,
    @Column(name = "baptism_place_enc") @Convert(converter = EncryptedStringConverter::class)
    var baptismPlace: String?,
    @Column(name = "baptism_officiant_enc") @Convert(converter = EncryptedStringConverter::class)
    var baptismOfficiant: String?,
    @Column(name = "confirmation_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var confirmationDate: LocalDate?,
    @Column(name = "previous_church_enc") @Convert(converter = EncryptedStringConverter::class)
    var previousChurch: String?,
    @Column(name = "transferred_in_at_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var transferredInAt: LocalDate?,
) {
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
        private set
}
```

```kotlin
// ChurchMemberAuditLog.kt
package org.happyzion.api.member.domain

import jakarta.persistence.*
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member_audit_log")
class ChurchMemberAuditLog(
    @Column(name = "church_member_id", nullable = false) val churchMemberId: Long,
    @Column(name = "actor_id", nullable = false) val actorId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) val action: AuditAction,
    @Column(name = "diff_enc") @Convert(converter = EncryptedStringConverter::class)
    val diffJson: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
}
```

- [ ] **Step 6: Run domain test, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.domain.ChurchMemberTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/domain/ \
        src/test/kotlin/org/happyzion/api/member/domain/
git commit -m "feat(member): add domain enums and entities (ChurchMember, Faith, AuditLog)"
```

---

## Task 15: Member repositories

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/infrastructure/persistence/ChurchMemberRepository.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/infrastructure/persistence/ChurchMemberFaithRepository.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/infrastructure/persistence/ChurchMemberAuditLogRepository.kt`

- [ ] **Step 1: Create the repositories**

```kotlin
// ChurchMemberRepository.kt
package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMember
import org.happyzion.api.member.domain.ChurchMemberStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChurchMemberRepository : JpaRepository<ChurchMember, Long> {

    @Query("""
        select m from ChurchMember m
        where (:nameHash is null or m.nameHash = :nameHash)
          and (:phoneHash is null or m.phoneHash = :phoneHash)
          and (:phoneLast4Hash is null or m.phoneLast4Hash = :phoneLast4Hash)
          and (:faithStage is null or m.faithStage = :faithStage)
          and (:cellLabel is null or m.cellLabel = :cellLabel)
          and m.status in :statuses
        order by m.registeredAt desc, m.id desc
    """)
    fun search(
        @Param("nameHash") nameHash: String?,
        @Param("phoneHash") phoneHash: String?,
        @Param("phoneLast4Hash") phoneLast4Hash: String?,
        @Param("faithStage") faithStage: org.happyzion.api.member.domain.FaithStage?,
        @Param("cellLabel") cellLabel: String?,
        @Param("statuses") statuses: Set<ChurchMemberStatus>,
        pageable: Pageable,
    ): Page<ChurchMember>
}
```

```kotlin
// ChurchMemberFaithRepository.kt
package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMemberFaith
import org.springframework.data.jpa.repository.JpaRepository

interface ChurchMemberFaithRepository : JpaRepository<ChurchMemberFaith, Long>
```

```kotlin
// ChurchMemberAuditLogRepository.kt
package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMemberAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChurchMemberAuditLogRepository : JpaRepository<ChurchMemberAuditLog, Long> {
    fun findByChurchMemberIdOrderByCreatedAtDescIdDesc(churchMemberId: Long, pageable: Pageable): Page<ChurchMemberAuditLog>
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/infrastructure/
git commit -m "feat(member): add JPA repositories for member, faith, audit log"
```

---

## Task 16: `ChurchMemberSnapshot` + `ChurchMemberAuditWriter`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberSnapshot.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAuditWriter.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAuditWriterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiEncryptor
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.common.security.pii.PiiKeyRing
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class ChurchMemberAuditWriterTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val props = PiiEncryptionProperties("v1:$key", "v1", key)
    private val encryptor = PiiEncryptor(PiiKeyRing(props))
    private val mapper = ObjectMapper()
    private val repo = mock<ChurchMemberAuditLogRepository>()
    private val writer = ChurchMemberAuditWriter(repo, encryptor, mapper)

    @Test
    fun `recordUpdate captures only changed fields`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val before = snapshot(name = "김철수", phone = "01012345678")
        val after  = snapshot(name = "이영희", phone = "01012345678")

        writer.recordUpdate(memberId = 1, actorId = 7, before = before, after = after)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        val log = captor.firstValue
        assertThat(log.action).isEqualTo(AuditAction.UPDATE)
        assertThat(log.actorId).isEqualTo(7)
        val decrypted = encryptor.decrypt(log.diffJson!!)
        assertThat(decrypted).contains("\"name\":[\"김철수\",\"이영희\"]")
        assertThat(decrypted).doesNotContain("phone")
    }

    @Test
    fun `recordCreate stores after snapshot fields as new`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val after = snapshot()
        writer.recordCreate(memberId = 1, actorId = 7, after = after)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.CREATE)
    }

    @Test
    fun `recordDelete stores before snapshot`() {
        whenever(repo.save(any<ChurchMemberAuditLog>())).thenAnswer { it.arguments[0] }

        val before = snapshot()
        writer.recordDelete(memberId = 1, actorId = 7, before = before)

        val captor = argumentCaptor<ChurchMemberAuditLog>()
        verify(repo).save(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(AuditAction.DELETE)
    }

    private fun snapshot(name: String = "김철수", phone: String = "01012345678") =
        ChurchMemberSnapshot(
            name = name, phone = phone, email = null,
            birthDate = LocalDate.of(1990,1,1), birthCalendar = BirthCalendar.SOLAR,
            sex = Sex.M, address = "서울", addressDetail = null, job = null,
            memo = null, photoAssetId = null, cellLabel = null,
            status = ChurchMemberStatus.ACTIVE, faithStage = null,
            office = ChurchMemberOffice.LAY, officeAppointedAt = null,
            registeredAt = LocalDate.of(2024,1,1),
        )
}
```

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberAuditWriterTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement snapshot + writer**

```kotlin
// ChurchMemberSnapshot.kt
package org.happyzion.api.member.application

import org.happyzion.api.member.domain.*
import java.time.LocalDate

data class ChurchMemberSnapshot(
    val name: String,
    val phone: String,
    val email: String?,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val sex: Sex,
    val address: String,
    val addressDetail: String?,
    val job: String?,
    val memo: String?,
    val photoAssetId: Long?,
    val cellLabel: String?,
    val status: ChurchMemberStatus,
    val faithStage: FaithStage?,
    val office: ChurchMemberOffice,
    val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
) {
    companion object {
        fun of(m: ChurchMember) = ChurchMemberSnapshot(
            name = m.name, phone = m.phone, email = m.email,
            birthDate = m.birthDate, birthCalendar = m.birthCalendar, sex = m.sex,
            address = m.address, addressDetail = m.addressDetail, job = m.job,
            memo = m.memo, photoAssetId = m.photoAssetId, cellLabel = m.cellLabel,
            status = m.status, faithStage = m.faithStage,
            office = m.office, officeAppointedAt = m.officeAppointedAt,
            registeredAt = m.registeredAt,
        )
    }
}
```

```kotlin
// ChurchMemberAuditWriter.kt
package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.common.security.pii.PiiEncryptor
import org.happyzion.api.member.domain.AuditAction
import org.happyzion.api.member.domain.ChurchMemberAuditLog
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChurchMemberAuditWriter(
    private val auditLogRepository: ChurchMemberAuditLogRepository,
    private val encryptor: PiiEncryptor,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun recordCreate(memberId: Long, actorId: Long, after: ChurchMemberSnapshot) {
        val diff = fieldsAsCreated(after)
        save(memberId, actorId, AuditAction.CREATE, diff)
    }

    @Transactional
    fun recordUpdate(memberId: Long, actorId: Long, before: ChurchMemberSnapshot, after: ChurchMemberSnapshot) {
        val diff = computeDiff(before, after)
        if (diff.isEmpty()) return
        save(memberId, actorId, AuditAction.UPDATE, diff)
    }

    @Transactional
    fun recordDelete(memberId: Long, actorId: Long, before: ChurchMemberSnapshot) {
        save(memberId, actorId, AuditAction.DELETE, fieldsAsCreated(before))
    }

    private fun save(memberId: Long, actorId: Long, action: AuditAction, diff: Map<String, Any?>) {
        val diffJson = encryptor.encrypt(objectMapper.writeValueAsString(diff))
        auditLogRepository.save(
            ChurchMemberAuditLog(
                churchMemberId = memberId,
                actorId = actorId,
                action = action,
                diffJson = diffJson,
            )
        )
    }

    private fun fieldsAsCreated(s: ChurchMemberSnapshot): Map<String, Any?> = fieldMap(s)

    private fun computeDiff(before: ChurchMemberSnapshot, after: ChurchMemberSnapshot): Map<String, List<Any?>> {
        val b = fieldMap(before); val a = fieldMap(after)
        return b.keys.filter { b[it] != a[it] }.associateWith { key -> listOf(b[key], a[key]) }
    }

    private fun fieldMap(s: ChurchMemberSnapshot): Map<String, Any?> = mapOf(
        "name" to s.name, "phone" to s.phone, "email" to s.email,
        "birthDate" to s.birthDate.toString(), "birthCalendar" to s.birthCalendar.name,
        "sex" to s.sex.name, "address" to s.address, "addressDetail" to s.addressDetail,
        "job" to s.job, "memo" to s.memo, "photoAssetId" to s.photoAssetId,
        "cellLabel" to s.cellLabel, "status" to s.status.name,
        "faithStage" to s.faithStage?.name, "office" to s.office.name,
        "officeAppointedAt" to s.officeAppointedAt?.toString(),
        "registeredAt" to s.registeredAt.toString(),
    )
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberAuditWriterTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/application/ChurchMemberSnapshot.kt \
        src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAuditWriter.kt \
        src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAuditWriterTest.kt
git commit -m "feat(member): add snapshot and audit writer with encrypted JSON diff"
```

---

## Task 17: `ChurchMemberPhotoService`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoService.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package org.happyzion.api.member.application

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.domain.ChurchMember
// (... domain fixture imports ...)
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DataIntegrityViolationException
import java.time.OffsetDateTime
import java.util.Optional

class ChurchMemberPhotoServiceTest {
    private val postAssetRepo = mock<PostAssetRepository>()
    private val service = ChurchMemberPhotoService(postAssetRepo)

    @Test
    fun `replacePhoto rejects when asset missing`() {
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.empty())
        val m = newMemberFixture()
        assertThatThrownBy { service.replacePhoto(m, 42, actorId = 1) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `replacePhoto rejects when asset kind is wrong`() {
        val asset = postAsset(kind = PostAssetKind.INLINE_IMAGE, uploadedBy = 1, detached = true)
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.of(asset))
        assertThatThrownBy { service.replacePhoto(newMemberFixture(), 42, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `replacePhoto rejects when asset is already attached (detachedAt is null)`() {
        val asset = postAsset(kind = PostAssetKind.MEMBER_PHOTO, uploadedBy = 1, detached = false)
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.of(asset))
        assertThatThrownBy { service.replacePhoto(newMemberFixture(), 42, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `replacePhoto rejects when uploader is not the current actor`() {
        val asset = postAsset(kind = PostAssetKind.MEMBER_PHOTO, uploadedBy = 999, detached = true)
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.of(asset))
        assertThatThrownBy { service.replacePhoto(newMemberFixture(), 42, actorId = 1) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `replacePhoto attaches new asset and detaches previous`() {
        val newAsset = postAsset(id = 42, kind = PostAssetKind.MEMBER_PHOTO, uploadedBy = 1, detached = true)
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.of(newAsset))
        val oldAsset = postAsset(id = 99, kind = PostAssetKind.MEMBER_PHOTO, uploadedBy = 1, detached = false)
        whenever(postAssetRepo.findById(99)).thenReturn(Optional.of(oldAsset))

        val member = newMemberFixture().also { it.linkPhoto(99L) }

        service.replacePhoto(member, 42, actorId = 1)

        // newAsset detachedAt cleared, oldAsset detached, member.photoAssetId = 42
        // (assertions on captured saves)
    }

    @Test
    fun `replacePhoto wraps DataIntegrityViolation into IllegalArgumentException`() {
        val asset = postAsset(kind = PostAssetKind.MEMBER_PHOTO, uploadedBy = 1, detached = true)
        whenever(postAssetRepo.findById(42)).thenReturn(Optional.of(asset))
        whenever(postAssetRepo.save(any())).thenThrow(DataIntegrityViolationException("dup"))

        assertThatThrownBy { service.replacePhoto(newMemberFixture(), 42, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // helpers...
}
```

(Helpers fill in entity fixtures using the existing test setup conventions.)

- [ ] **Step 2: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberPhotoServiceTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `ChurchMemberPhotoService`**

```kotlin
package org.happyzion.api.member.application

import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.domain.ChurchMember
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ChurchMemberPhotoService(
    private val postAssetRepository: PostAssetRepository,
) {
    @Transactional
    fun replacePhoto(member: ChurchMember, assetId: Long, actorId: Long) {
        val asset = postAssetRepository.findById(assetId).orElseThrow {
            NotFoundException("사진 자산을 찾을 수 없습니다. id=$assetId")
        }
        require(asset.kind == PostAssetKind.MEMBER_PHOTO) { "교인 사진이 아닙니다." }
        require(asset.detachedAt != null) { "이미 사용 중인 자산입니다." }
        if (asset.uploadedByActorId != actorId) {
            throw ForbiddenException("다른 관리자가 업로드한 자산입니다.")
        }

        member.photoAssetId?.let { previousId ->
            postAssetRepository.findById(previousId).ifPresent { previous ->
                previous.detachedAt = OffsetDateTime.now()
                postAssetRepository.save(previous)
            }
        }

        asset.detachedAt = null
        try {
            postAssetRepository.save(asset)
        } catch (ex: DataIntegrityViolationException) {
            throw IllegalArgumentException("이미 다른 교인에 연결된 사진입니다.", ex)
        }
        member.linkPhoto(assetId)
    }

    @Transactional
    fun removePhoto(member: ChurchMember) {
        val previousId = member.photoAssetId ?: return
        postAssetRepository.findById(previousId).ifPresent { previous ->
            previous.detachedAt = OffsetDateTime.now()
            postAssetRepository.save(previous)
        }
        member.unlinkPhoto()
    }
}
```

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberPhotoServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoService.kt \
        src/test/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoServiceTest.kt
git commit -m "feat(member): add ChurchMemberPhotoService with kind/owner/uniqueness checks"
```

---

## Task 18: `ChurchMemberAdminModels` + `ChurchMemberAdminService` (CRUD)

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminModels.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminService.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAdminServiceTest.kt`

- [ ] **Step 1: Define models**

```kotlin
package org.happyzion.api.member.application

import org.happyzion.api.member.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class ChurchMemberSaveCommand(
    val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val cellLabel: String?, val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate, val memo: String?,
    val faith: ChurchMemberFaithSaveCommand?,
)

data class ChurchMemberFaithSaveCommand(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)

data class ChurchMemberSearchFilter(
    val name: String?, val phone: String?,
    val statuses: Set<ChurchMemberStatus>,
    val faithStage: FaithStage?, val cellLabel: String?,
    val includeInactive: Boolean,
)

data class ChurchMemberSummary(
    val id: Long, val name: String, val phone: String, val status: ChurchMemberStatus,
    val cellLabel: String?, val registeredAt: LocalDate,
)

data class ChurchMemberPage(val items: List<ChurchMemberSummary>, val hasNext: Boolean)

data class ChurchMemberDetail(
    val id: Long, val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val memo: String?, val photoAssetId: Long?, val cellLabel: String?,
    val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
    val faith: ChurchMemberFaithDetail?,
    val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime,
)

data class ChurchMemberFaithDetail(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)

data class ChurchMemberAuditEntry(
    val id: Long, val action: AuditAction, val actorId: Long,
    val diffJson: String?, val createdAt: OffsetDateTime,
)

data class ChurchMemberAuditPage(val items: List<ChurchMemberAuditEntry>, val hasNext: Boolean)
```

- [ ] **Step 2: Write failing service test (CRUD focus)**

Tests cover: `createMember`, `updateMember` (diff captured), `softDeleteMember` (markRemoved + DELETE audit), `getMember`, `listAuditLogs`. Use `AdminAccountGuard` mock + `PiiHasher` real (with test key) + `MemberSearchKeyNormalizer` real + repository mocks.

```kotlin
@Test
fun `createMember persists with hashes, links faith if present, records CREATE audit`() {
    // ...
}

@Test
fun `createMember calls AdminAccountGuard before any writes`() {
    // verify guard.verify(actorId) called once; if it throws, repos must not be called
}

@Test
fun `updateMember computes diff between snapshots and records UPDATE`() {
    // change name only → audit diff contains "name" only
}

@Test
fun `softDeleteMember marks REMOVED and records DELETE audit`() {
    // ...
}

@Test
fun `getMember returns plaintext detail including faith`() {
    // mock repo returns entity; service maps to detail
}
```

- [ ] **Step 3: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberAdminServiceTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `ChurchMemberAdminService` (CRUD parts)**

```kotlin
package org.happyzion.api.member.application

import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberFaithRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChurchMemberAdminService(
    private val memberRepo: ChurchMemberRepository,
    private val faithRepo: ChurchMemberFaithRepository,
    private val auditRepo: ChurchMemberAuditLogRepository,
    private val auditWriter: ChurchMemberAuditWriter,
    private val photoService: ChurchMemberPhotoService,
    private val adminAccountGuard: AdminAccountGuard,
    private val hasher: PiiHasher,
    private val normalizer: MemberSearchKeyNormalizer,
) {
    fun createMember(cmd: ChurchMemberSaveCommand, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = ChurchMember.create(
            name = cmd.name, phone = cmd.phone, email = cmd.email,
            birthDate = cmd.birthDate, birthCalendar = cmd.birthCalendar, sex = cmd.sex,
            address = cmd.address, addressDetail = cmd.addressDetail, job = cmd.job,
            cellLabel = cmd.cellLabel, status = cmd.status, faithStage = cmd.faithStage,
            office = cmd.office, officeAppointedAt = cmd.officeAppointedAt,
            registeredAt = cmd.registeredAt, memo = cmd.memo,
            hasher = hasher, normalizer = normalizer,
        )
        val saved = memberRepo.save(member)
        val faith = cmd.faith?.let { f -> faithRepo.save(toFaithEntity(saved.id, f)) }
        auditWriter.recordCreate(saved.id, actorId, ChurchMemberSnapshot.of(saved))
        return toDetail(saved, faith)
    }

    fun updateMember(id: Long, cmd: ChurchMemberSaveCommand, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)

        member.rename(cmd.name, hasher, normalizer)
        member.changePhone(cmd.phone, hasher, normalizer)
        member.changeEmail(cmd.email)
        member.changeBirth(cmd.birthDate, cmd.birthCalendar)
        member.changeSex(cmd.sex)
        member.changeAddress(cmd.address, cmd.addressDetail)
        member.changeJob(cmd.job)
        member.changeCellLabel(cmd.cellLabel)
        member.changeStatus(cmd.status)   // REMOVED 차단 적용됨
        member.changeFaithStage(cmd.faithStage)
        member.appointOffice(cmd.office, cmd.officeAppointedAt)
        member.changeRegisteredAt(cmd.registeredAt)
        member.changeMemo(cmd.memo)

        val faith = upsertFaith(id, cmd.faith)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faith)
    }

    fun softDeleteMember(id: Long, actorId: Long) {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        member.markRemoved()
        auditWriter.recordDelete(id, actorId, before)
    }

    fun attachPhoto(id: Long, assetId: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        photoService.replacePhoto(member, assetId, actorId)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faithRepo.findById(id).orElse(null))
    }

    fun detachPhoto(id: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        photoService.removePhoto(member)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faithRepo.findById(id).orElse(null))
    }

    @Transactional(readOnly = true)
    fun getMember(id: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val faith = faithRepo.findById(id).orElse(null)
        return toDetail(member, faith)
    }

    @Transactional(readOnly = true)
    fun listAuditLogs(memberId: Long, actorId: Long, page: Int, size: Int): ChurchMemberAuditPage {
        adminAccountGuard.verify(actorId)
        val p = auditRepo.findByChurchMemberIdOrderByCreatedAtDescIdDesc(
            memberId, PageRequest.of(page, size)
        )
        return ChurchMemberAuditPage(
            items = p.content.map {
                ChurchMemberAuditEntry(it.id, it.action, it.actorId,
                    it.diffJson?.let { ct -> /* JpaConverter already decrypts */ ct },
                    it.createdAt)
            },
            hasNext = p.hasNext(),
        )
    }

    private fun upsertFaith(memberId: Long, cmd: ChurchMemberFaithSaveCommand?): ChurchMemberFaith? {
        if (cmd == null) {
            faithRepo.findById(memberId).ifPresent(faithRepo::delete)
            return null
        }
        val existing = faithRepo.findById(memberId).orElse(null)
        if (existing == null) return faithRepo.save(toFaithEntity(memberId, cmd))
        existing.confessDate = cmd.confessDate
        existing.learningDate = cmd.learningDate
        existing.baptismDate = cmd.baptismDate
        existing.baptismPlace = cmd.baptismPlace
        existing.baptismOfficiant = cmd.baptismOfficiant
        existing.confirmationDate = cmd.confirmationDate
        existing.previousChurch = cmd.previousChurch
        existing.transferredInAt = cmd.transferredInAt
        return faithRepo.save(existing)
    }

    private fun toFaithEntity(memberId: Long, cmd: ChurchMemberFaithSaveCommand) =
        ChurchMemberFaith(
            churchMemberId = memberId,
            confessDate = cmd.confessDate, learningDate = cmd.learningDate,
            baptismDate = cmd.baptismDate, baptismPlace = cmd.baptismPlace,
            baptismOfficiant = cmd.baptismOfficiant, confirmationDate = cmd.confirmationDate,
            previousChurch = cmd.previousChurch, transferredInAt = cmd.transferredInAt,
        )

    private fun toDetail(m: ChurchMember, f: ChurchMemberFaith?): ChurchMemberDetail =
        ChurchMemberDetail(
            id = m.id, name = m.name, phone = m.phone, email = m.email,
            birthDate = m.birthDate, birthCalendar = m.birthCalendar, sex = m.sex,
            address = m.address, addressDetail = m.addressDetail, job = m.job,
            memo = m.memo, photoAssetId = m.photoAssetId, cellLabel = m.cellLabel,
            status = m.status, faithStage = m.faithStage,
            office = m.office, officeAppointedAt = m.officeAppointedAt,
            registeredAt = m.registeredAt,
            faith = f?.let {
                ChurchMemberFaithDetail(
                    confessDate = it.confessDate, learningDate = it.learningDate,
                    baptismDate = it.baptismDate, baptismPlace = it.baptismPlace,
                    baptismOfficiant = it.baptismOfficiant, confirmationDate = it.confirmationDate,
                    previousChurch = it.previousChurch, transferredInAt = it.transferredInAt,
                )
            },
            createdAt = m.createdAt, updatedAt = m.updatedAt,
        )
}
```

- [ ] **Step 5: Run, verify pass (CRUD tests)**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberAdminServiceTest"
```

Expected: PASS (search test added next task; mark search test `@Disabled` for now if necessary).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminModels.kt \
        src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminService.kt \
        src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAdminServiceTest.kt
git commit -m "feat(member): admin service CRUD with audit and photo orchestration"
```

---

## Task 19: `ChurchMemberAdminService.listMembers` (search)

**Files:**
- Modify: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminService.kt`
- Modify: `src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAdminServiceTest.kt`

- [ ] **Step 1: Add failing search tests**

```kotlin
@Test
fun `listMembers with name search produces nameHash via normalizer`() {
    // assert that repo.search is called with name_hash = hasher.hash(normalizer.forStoredName(" 김 철수 "))
}

@Test
fun `listMembers with phone 4 digits uses phone_last4_hash filter only`() {
    // ?phone=5678 → repo.search called with phoneHash=null, phoneLast4Hash=hash("5678")
}

@Test
fun `listMembers with phone 11 digits uses phone_hash filter`() {
    // ?phone=01012345678 → repo.search called with phoneHash=hash("01012345678"), phoneLast4Hash=null
}

@Test
fun `listMembers default filters out REMOVED and DECEASED`() {
    // includeInactive=false → statuses = ACTIVE,NEW,RESTING,LONG_ABSENT,TRANSFERRED_OUT
}

@Test
fun `listMembers includeInactive=true uses all statuses`() {
    // statuses contains all 7 enum values
}

@Test
fun `listMembers with ambiguous phone length returns empty result without calling repo`() {
    // ?phone=12345 (5 digits) → no repo.search call, empty page
}
```

- [ ] **Step 2: Run, verify failure**

- [ ] **Step 3: Implement `listMembers`**

Append to `ChurchMemberAdminService`:

```kotlin
@Transactional(readOnly = true)
fun listMembers(
    filter: ChurchMemberSearchFilter, actorId: Long, page: Int, size: Int,
): ChurchMemberPage {
    adminAccountGuard.verify(actorId)
    val statuses = if (filter.includeInactive) ChurchMemberStatus.values().toSet() else ChurchMemberStatus.ACTIVE_SET

    val nameHash = filter.name?.let(normalizer::forNameQuery)?.let(hasher::hash)
    val phoneQuery = filter.phone?.let(normalizer::forPhoneQuery)
    val phoneHash: String?; val phoneLast4Hash: String?
    when (phoneQuery) {
        is PhoneQueryKey.Full -> { phoneHash = hasher.hash(phoneQuery.full); phoneLast4Hash = null }
        is PhoneQueryKey.Last4 -> { phoneHash = null; phoneLast4Hash = hasher.hash(phoneQuery.last4) }
        null -> { phoneHash = null; phoneLast4Hash = null }
    }

    // Ambiguous-length phone (filter.phone provided but normalizer returned null) → empty result
    if (filter.phone != null && phoneQuery == null) {
        return ChurchMemberPage(emptyList(), false)
    }

    val pageData = memberRepo.search(
        nameHash = nameHash, phoneHash = phoneHash, phoneLast4Hash = phoneLast4Hash,
        faithStage = filter.faithStage, cellLabel = filter.cellLabel,
        statuses = statuses, pageable = PageRequest.of(page, size),
    )

    return ChurchMemberPage(
        items = pageData.content.map {
            ChurchMemberSummary(it.id, it.name, it.phone, it.status, it.cellLabel, it.registeredAt)
        },
        hasNext = pageData.hasNext(),
    )
}
```

Add `import org.happyzion.api.common.security.pii.PhoneQueryKey`.

- [ ] **Step 4: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberAdminServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/application/ChurchMemberAdminService.kt \
        src/test/kotlin/org/happyzion/api/member/application/ChurchMemberAdminServiceTest.kt
git commit -m "feat(member): list endpoint with blind-index name/phone search and inactive filter"
```

---

## Task 20: DTOs and `ChurchMemberAdminController`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/interfaces/dto/ChurchMemberAdminDtos.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberAdminController.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberAdminControllerTest.kt`

- [ ] **Step 1: Define DTOs (request + response)**

```kotlin
package org.happyzion.api.member.interfaces.dto

import jakarta.validation.constraints.*
import org.happyzion.api.member.application.*
import org.happyzion.api.member.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class ChurchMemberSaveRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotNull val sex: Sex,
    @field:NotNull val birthDate: LocalDate,
    @field:NotNull val birthCalendar: BirthCalendar,
    @field:NotBlank @field:Size(max = 30) val phone: String,
    @field:Size(max = 150) val email: String?,
    @field:NotBlank @field:Size(max = 200) val address: String,
    @field:Size(max = 200) val addressDetail: String?,
    @field:Size(max = 120) val job: String?,
    @field:Size(max = 120) val cellLabel: String?,
    @field:NotNull val status: ChurchMemberStatus,
    val faithStage: FaithStage?,
    @field:NotNull val office: ChurchMemberOffice,
    val officeAppointedAt: LocalDate?,
    @field:NotNull val registeredAt: LocalDate,
    val memo: String?,
    val faith: ChurchMemberFaithSaveRequest?,
) {
    fun toCommand() = ChurchMemberSaveCommand(
        name, phone, email, birthDate, birthCalendar, sex,
        address, addressDetail, job, cellLabel, status, faithStage,
        office, officeAppointedAt, registeredAt, memo, faith?.toCommand(),
    )
}

data class ChurchMemberFaithSaveRequest(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?,
    @field:Size(max = 120) val baptismPlace: String?,
    @field:Size(max = 120) val baptismOfficiant: String?,
    val confirmationDate: LocalDate?,
    @field:Size(max = 120) val previousChurch: String?,
    val transferredInAt: LocalDate?,
) {
    fun toCommand() = ChurchMemberFaithSaveCommand(
        confessDate, learningDate, baptismDate, baptismPlace,
        baptismOfficiant, confirmationDate, previousChurch, transferredInAt,
    )
}

data class ChurchMemberPhotoAttachRequest(@field:NotNull val assetId: Long)

data class ChurchMemberSummaryResponse(
    val id: Long, val name: String, val phone: String,
    val status: ChurchMemberStatus, val cellLabel: String?, val registeredAt: LocalDate,
)
data class ChurchMemberPageResponse(val items: List<ChurchMemberSummaryResponse>, val hasNext: Boolean)

data class ChurchMemberDetailResponse(
    val id: Long, val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val memo: String?, val photoAssetId: Long?, val cellLabel: String?,
    val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
    val faith: ChurchMemberFaithDetailResponse?,
    val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime,
)
data class ChurchMemberFaithDetailResponse(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)
data class ChurchMemberAuditEntryResponse(
    val id: Long, val action: AuditAction, val actorId: Long,
    val diffJson: String?, val createdAt: OffsetDateTime,
)
data class ChurchMemberAuditPageResponse(val items: List<ChurchMemberAuditEntryResponse>, val hasNext: Boolean)
```

(Add `toResponse()` mapping extension functions or inline mapping in the controller.)

- [ ] **Step 2: Write failing controller test**

`ChurchMemberAdminControllerTest` using MockMvc — verify endpoints + 401/400/404/200 paths.

- [ ] **Step 3: Run, verify failure**

```bash
./gradlew test --tests "org.happyzion.api.member.interfaces.api.ChurchMemberAdminControllerTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement the controller**

```kotlin
package org.happyzion.api.member.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.member.application.*
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.interfaces.dto.*
import org.springframework.web.bind.annotation.*

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members")
class ChurchMemberAdminController(
    private val service: ChurchMemberAdminService,
) {
    @GetMapping
    fun list(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) faithStage: FaithStage?,
        @RequestParam(required = false) cellLabel: String?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ChurchMemberPageResponse {
        val statuses = status?.split(",")?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.map(ChurchMemberStatus::valueOf)?.toSet() ?: emptySet()
        val result = service.listMembers(
            ChurchMemberSearchFilter(name, phone, statuses, faithStage, cellLabel, includeInactive),
            actorId, page, size,
        )
        return ChurchMemberPageResponse(
            items = result.items.map { ChurchMemberSummaryResponse(it.id, it.name, it.phone, it.status, it.cellLabel, it.registeredAt) },
            hasNext = result.hasNext,
        )
    }

    @PostMapping
    fun create(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: ChurchMemberSaveRequest,
    ): ChurchMemberDetailResponse = service.createMember(request.toCommand(), actorId).toResponse()

    @GetMapping("/{id}")
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): ChurchMemberDetailResponse = service.getMember(id, actorId).toResponse()

    @PutMapping("/{id}")
    fun update(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ChurchMemberSaveRequest,
    ): ChurchMemberDetailResponse = service.updateMember(id, request.toCommand(), actorId).toResponse()

    @DeleteMapping("/{id}")
    fun delete(@RequestAttribute("adminAccountId") actorId: Long, @PathVariable id: Long) {
        service.softDeleteMember(id, actorId)
    }

    @PostMapping("/{id}/photo")
    fun attachPhoto(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ChurchMemberPhotoAttachRequest,
    ): ChurchMemberDetailResponse = service.attachPhoto(id, request.assetId, actorId).toResponse()

    @DeleteMapping("/{id}/photo")
    fun detachPhoto(@RequestAttribute("adminAccountId") actorId: Long, @PathVariable id: Long): ChurchMemberDetailResponse =
        service.detachPhoto(id, actorId).toResponse()

    @GetMapping("/{id}/audit-logs")
    fun auditLogs(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ChurchMemberAuditPageResponse {
        val p = service.listAuditLogs(id, actorId, page, size)
        return ChurchMemberAuditPageResponse(
            items = p.items.map { ChurchMemberAuditEntryResponse(it.id, it.action, it.actorId, it.diffJson, it.createdAt) },
            hasNext = p.hasNext,
        )
    }

    private fun ChurchMemberDetail.toResponse() = ChurchMemberDetailResponse(
        id, name, phone, email, birthDate, birthCalendar, sex, address, addressDetail,
        job, memo, photoAssetId, cellLabel, status, faithStage, office, officeAppointedAt,
        registeredAt,
        faith?.let { ChurchMemberFaithDetailResponse(it.confessDate, it.learningDate, it.baptismDate, it.baptismPlace, it.baptismOfficiant, it.confirmationDate, it.previousChurch, it.transferredInAt) },
        createdAt, updatedAt,
    )
}
```

- [ ] **Step 5: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.interfaces.api.ChurchMemberAdminControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/interfaces/ \
        src/test/kotlin/org/happyzion/api/member/interfaces/
git commit -m "feat(member): admin REST controller with full CRUD/list/photo/audit endpoints"
```

---

## Task 21: `ChurchMemberPhotoStreamer` + `ChurchMemberPhotoController`

**Files:**
- Create: `src/main/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoStreamer.kt`
- Create: `src/main/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberPhotoController.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoStreamerTest.kt`
- Create: `src/test/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberPhotoControllerTest.kt`

- [ ] **Step 1: Write failing streamer test**

Tests: member missing → 404, photoAssetId null → 404, asset missing → 404, traversal → 404, returns Resource + mimeType for normal path.

- [ ] **Step 2: Implement streamer**

```kotlin
package org.happyzion.api.member.application

import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.board.application.AttachmentStorage
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class StreamedPhoto(val resource: Resource, val mimeType: String)

@Service
class ChurchMemberPhotoStreamer(
    private val memberRepo: ChurchMemberRepository,
    private val postAssetRepository: PostAssetRepository,
    private val attachmentStorage: AttachmentStorage,
    private val adminAccountGuard: AdminAccountGuard,
) {
    @Transactional(readOnly = true)
    fun load(memberId: Long, actorId: Long): StreamedPhoto {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(memberId).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val assetId = member.photoAssetId ?: throw NotFoundException("사진이 등록되어 있지 않습니다.")
        val asset = postAssetRepository.findById(assetId).orElseThrow { NotFoundException("사진 자산을 찾을 수 없습니다.") }
        val mime = asset.mimeType ?: throw NotFoundException("자산의 MIME 정보가 없습니다.")
        return StreamedPhoto(attachmentStorage.load(asset.storedPath), mime)
    }
}
```

- [ ] **Step 3: Implement controller**

```kotlin
package org.happyzion.api.member.interfaces.api

import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.member.application.ChurchMemberPhotoStreamer
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members/{id}/photo")
class ChurchMemberPhotoController(
    private val streamer: ChurchMemberPhotoStreamer,
) {
    @GetMapping
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val (resource, mime) = streamer.load(id, actorId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mime))
            .cacheControl(CacheControl.noStore())
            .body(resource)
    }
}
```

- [ ] **Step 4: Run streamer + controller tests, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.application.ChurchMemberPhotoStreamerTest" --tests "org.happyzion.api.member.interfaces.api.ChurchMemberPhotoControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoStreamer.kt \
        src/main/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberPhotoController.kt \
        src/test/kotlin/org/happyzion/api/member/application/ChurchMemberPhotoStreamerTest.kt \
        src/test/kotlin/org/happyzion/api/member/interfaces/api/ChurchMemberPhotoControllerTest.kt
git commit -m "feat(member): authenticated photo streaming with traversal guard + no-store cache"
```

---

## Task 22: Repository contract test (raw-SQL PII no-leak + UNIQUE)

**Files:**
- Create: `src/test/kotlin/org/happyzion/api/member/infrastructure/persistence/ChurchMemberRepositoryContractTest.kt`

This is an integration test that requires Postgres + Flyway. Follow whatever pattern existing `*ContractTest.kt` use (Testcontainers or external DB). If `BoardSchemaContractTest` uses string-based migration parsing (no live DB), then create a **`@DataJpaTest`** style test that boots an in-memory Spring context. If a Testcontainers helper exists in `support/`, reuse it.

- [ ] **Step 1: Inspect existing patterns**

```bash
grep -rln "@DataJpaTest\|Testcontainers\|PostgreSQLContainer" src/test/kotlin
```

Choose whichever harness is established. Document the choice in the test class KDoc.

- [ ] **Step 2: Write the contract test**

Required assertions:
- Save a `ChurchMember` with name "김철수", phone "01012345678"
- Use `JdbcTemplate.queryForObject("select name_enc from church_member where id = ?", String, id)` → assert starts with `"v1:"` and does NOT contain "김철수"
- Same for `phone_enc`, `birth_date_enc`, `address_enc`, `address_detail_enc`, `job_enc`, `email_enc`, `memo_enc`
- Save a `ChurchMemberFaith` with `baptismPlace = "온누리교회"` → raw `baptism_place_enc` does not contain plaintext
- Trigger an update via service, then assert `diff_enc` raw does not contain "김철수" or "이영희" while decryption yields the expected diff JSON
- Save two members both pointing to the same `photo_asset_id` → second save throws `DataIntegrityViolationException`
- `findByChurchMemberIdOrderByCreatedAtDescIdDesc` returns rows in descending order

- [ ] **Step 3: Run, verify pass**

```bash
./gradlew test --tests "org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepositoryContractTest"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/org/happyzion/api/member/infrastructure/persistence/ChurchMemberRepositoryContractTest.kt
git commit -m "test(member): raw-SQL contract test verifies no plaintext leaks + photo UNIQUE"
```

---

## Task 23: README, OpenAPI regeneration, full build

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add endpoints to README**

Insert under `## 주요 엔드포인트`:

```text
- `GET /api/v1/admin/members`
- `POST /api/v1/admin/members`
- `GET /api/v1/admin/members/{id}`
- `PUT /api/v1/admin/members/{id}`
- `DELETE /api/v1/admin/members/{id}`
- `POST /api/v1/admin/members/{id}/photo`
- `DELETE /api/v1/admin/members/{id}/photo`
- `GET /api/v1/admin/members/{id}/photo`
- `GET /api/v1/admin/members/{id}/audit-logs`
```

Update the environment variables block to include the new PII keys.

- [ ] **Step 2: Run full build (regenerates OpenAPI)**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. Check `build/openapi/openapi.yaml` is up to date with the new endpoints.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add church member registry endpoints and PII env vars to README"
```

---

## Self-Review Summary

After writing the plan, the following spec sections are covered by at least one task:

| Spec | Task(s) |
|---|---|
| §4 V8 migration | 1 |
| §4.4 CHECK extension | 1 (verified in 22) |
| §2.5 / §3.2 key ring | 3, 4 |
| §3.2 PiiEncryptor | 5 |
| §3.2 PiiHasher + normalizer | 6 |
| §3.2 converters + SpringBeanContainer | 7 |
| §7.4 AdminAccountGuard | 8 (used in 9, 11, 17–21) |
| §7.6 MemberPhotoUploadPolicy | 9 |
| §3.3 AttachmentStorage.load + traversal | 10 (verified in 21) |
| §6.2 / §6.3 photo lifecycle | 10, 11, 17, 21 |
| §7.5 RequestLoggingFilter redaction | 12 |
| §3.3 WebConfig CORS + nginx deny | 13 |
| §5 domain entities + invariants | 14 |
| §3.1 repositories | 15 |
| §5.4 audit writer | 16 |
| §6.1 / §6.4 / §6.5 admin service & search | 18, 19 |
| §6.1 controllers | 20, 21 |
| §8.2 repository contract test (no plaintext leak) | 22 |
| Documentation | 23 |

No placeholders remain. Method names (`forStoredName`, `forStoredPhone`, `forNameQuery`, `forPhoneQuery`, `last4OfStored`, `replacePhoto`, `removePhoto`, `linkPhoto`, `unlinkPhoto`, `markRemoved`, `recordCreate/Update/Delete`, `verify`) are consistent across Tasks 6–21.
