# 교적부(Church Member Registry) 설계 — Phase 1

- 작성일: 2026-05-18 (리뷰 반영 개정)
- 상태: 구현 계획 수립 직전
- 기반 문서: `/Users/T/Downloads/PRD-church-member-registry.html` (PRD v0.1)
- 대상 서비스: `happyzion-api` (Kotlin + Spring Boot 3.5, JDK 21, PostgreSQL)

## 1. 개요

PRD §1~§4 그대로. 관리자 전용 교적부 기능을 신규 슬라이스(`org.happyzion.api.member`)로 구현하며, 인적사항·신앙정보·변경 감사 로그를 다룬다. 이전 시도(V1 도입 → V7 제거)와 달리 PRD가 명시한 범위만 구현한다.

본 설계는 PRD가 둔 5개 결정 항목과, 브레인스토밍·코드 리뷰 중 추가로 식별된 결정 항목들에 대한 합의를 반영한다.

## 2. 확정된 결정사항

### 2.1 PRD §8에서 가져온 5개

| # | 항목 | 결정 |
|---|---|---|
| 1 | `faith_stage` 위치 | `church_member` 본 테이블 (V1 패턴 복귀). 목록 필터 활용·단일 인덱스. |
| 2 | 교적번호(`member_no`) 도입 | 도입하지 않음. `bigserial id`만 사용. |
| 3 | 삭제 정책 | Soft delete만 — `status = 'REMOVED'`로 표시, audit log에 `action = 'DELETE'` 기록. 물리 삭제 API 없음. |
| 4 | 목록 기본 필터 | `REMOVED`·`DECEASED`는 기본 숨김, `includeInactive=true` 토글로 노출. |
| 5 | X-Admin-Key 전환 전 출시 | 무효화 — 이미 JWT(`AdminAuthInterceptor` + `@AdminAuthRequired` + Bearer)로 전환됨. `audit_log.actor_id` 처음부터 `NOT NULL`. |

### 2.2 브레인스토밍 추가 결정

| # | 항목 | 결정 |
|---|---|---|
| 6 | 사진 업로드 패턴 | 기존 `post_asset` / `UploadTokenService` / `UploadAssetService` 인프라 재사용 + `PostAssetKind.MEMBER_PHOTO` 추가. 소유권은 `church_member.photo_asset_id` 단일 FK + UNIQUE로 표현. **저장 디렉토리는 별도 private prefix** + **Spring 인증 프록시 스트리밍**으로 서빙. MIME·크기 정책은 서버 상수로 강제. |
| 7 | 감사 로그 캡처 방식 | 서비스 레이어에서 명시적 before/after 스냅샷 비교 후 JSON diff 생성. Envers·`@EntityListener` 자동화 미도입. **append-only는 앱 관례로만 보장**(setter 없음, save 전용). DB trigger 강제는 Phase 1 비범위. |
| 8 | 입력 검증 강도 | Bean Validation은 형식만(필수/길이/enum). 전화·이메일 정규식 강제 없음. 도메인은 의미적 invariant만. |
| 9 | 검색 PII 로그 노출 | `RequestLoggingFilter`가 query string 전체를 INFO 로그에 찍으므로, `name`·`phone` 파라미터 평문이 access 로그에 남음. **path-aware redaction** 추가 — `/api/v1/admin/members` 및 사진/감사 로그 하위 경로에서 `name`, `phone` 키의 값을 `[REDACTED]`로 마스킹. 검색을 POST body 기반으로 바꾸지는 않음(REST 관점 어색·다른 모듈 영향). |
| 10 | MEMBER_PHOTO 업로드 정책 | 사진 토큰 발급 시 클라이언트가 보낸 `allowedMimeTypes`·`maxByteSize`는 **무시**하고 서버 상수로 override: MIME = `image/jpeg`·`image/png`·`image/webp`, max = 5 MiB. `LocalAttachmentStorage.detectMimeType`이 PDF도 허용하지만, 토큰의 화이트리스트와 storage가 받은 MIME 검증을 함께 통과해야 하므로 이미지 외 차단. |
| 11 | 비활성 admin 가드 범위·위치 | `AdminAccountGuard`(이름 변경)를 **`adminaccount.application`** 패키지에 둔다 (`AdminAccountRepository`가 같은 모듈에 있어 계층 정합). **교적부 서비스 진입점**, **MEMBER_PHOTO 토큰 발급**, 그리고 **MEMBER_PHOTO 업로드 실행 시점**(`UploadAssetService.upload`의 토큰 소비 후) 세 군데에 적용. 마지막 항목은 토큰 발급~소비 사이 5분 윈도우 동안 계정이 비활성화되는 잔여 리스크를 닫기 위한 추가 검증. 다른 모듈 토큰/엔드포인트는 본 설계 범위 밖(별도 과제). |
| 12 | `@Converter` DI 방식 | Hibernate의 `SpringBeanContainer`를 활성화하여 컨버터가 빈 의존을 받게 한다. **정적 holder는 명시적으로 채택하지 않음**(테스트 간 상태 오염 위험). |

### 2.3 개인정보 보호 결정

| 카테고리 | 컬럼 | 처리 |
|---|---|---|
| 검색 필요 + 강한 보호 | `church_member.name`, `phone` | AES-GCM + HMAC-SHA256 blind index (`name_hash`, `phone_hash`, `phone_last4_hash`) |
| 강한 보호 (검색 불필요) | `church_member.email`, `address`, `address_detail`, `job`, `memo`, `birth_date` | AES-GCM 확률적 암호화. `birth_date`는 ISO 'YYYY-MM-DD' 문자열로 변환 후 암호화. |
| 종교 민감정보 (PIPA) | `church_member_faith.*` 전 데이터 컬럼 | AES-GCM 확률적 암호화. 날짜는 ISO 문자열 변환. CHECK 제약 제거(application 검증으로 이동). |
| 감사 로그 본문 | `church_member_audit_log.diff_enc` | diff JSON 직렬화 후 통째 AES-GCM 암호화. |
| 평문 유지 (운영·필터·정렬) | `id`, `sex`, `birth_calendar`, `status`, `faith_stage`, `office`, `office_appointed_at`, `registered_at`, `cell_label`, `photo_asset_id`, `created_at`, `updated_at`, audit log의 메타 컬럼 | 평문 |

### 2.4 검색 UX

- 이름: 완전일치만 (정규화 후 HMAC 비교)
- 전화: 풀번호 완전일치 또는 끝 4자리 완전일치
- 부분일치(예: "김"으로 시작) 미제공

### 2.5 키 관리 — key ring 정책 (리뷰 #8 반영)

단일 `v1:` 접두사만으로는 부족하다. Key ID와 key ring을 운영한다.

- 환경변수:
  - `HAPPYZION_PII_ENCRYPTION_KEYS` — CSV. 형식: `<keyId>:<base64 32B>,<keyId>:<base64 32B>,...` (예: `v1:abc=,v2:def=`)
  - `HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID` — 신규 쓰기에 사용할 key id (예: `v2`)
  - `HAPPYZION_PII_HASH_KEY` — base64 32B (HMAC-SHA256, 단일. blind index 일괄 갱신이 어려우므로 회전 미지원이 의도)
- 저장 포맷: `<keyId>:<base64(12B IV ‖ ciphertext ‖ 16B GCM tag)>` (예: `v2:<base64...>`)
- 시작 시 검증:
  - 둘 다 누락 시 애플리케이션 부팅 실패 (`@ConfigurationProperties` + `@Validated`)
  - `ACTIVE_KEY_ID`가 `KEYS` 목록에 없으면 부팅 실패
  - HMAC 키 빈 문자열도 부팅 실패
- 회전 절차 (운영):
  1. `KEYS`에 `v2:<new>` 추가, `ACTIVE_KEY_ID=v2`로 변경 → 신규 데이터는 `v2`로 암호화, 기존 `v1`은 그대로 복호 가능
  2. 점진 재암호화 배치(Phase 2 검토)로 `v1` 데이터를 `v2`로 재기록
  3. 모든 row가 `v2`가 되면 `KEYS`에서 `v1` 제거

## 3. 모듈 구조

### 3.1 신규 모듈

```
org.happyzion.api.member/
├── domain/
│   ├── ChurchMember.kt
│   ├── ChurchMemberFaith.kt
│   ├── ChurchMemberAuditLog.kt
│   ├── ChurchMemberStatus.kt        (enum)
│   ├── ChurchMemberOffice.kt        (enum)
│   ├── FaithStage.kt                (enum)
│   ├── BirthCalendar.kt             (enum)
│   ├── Sex.kt                       (enum)
│   └── AuditAction.kt               (enum: CREATE/UPDATE/DELETE)
├── application/
│   ├── ChurchMemberAdminService.kt
│   ├── ChurchMemberAdminModels.kt   (Command/Snapshot/Detail/Summary/Page)
│   ├── ChurchMemberAuditWriter.kt
│   ├── ChurchMemberPhotoService.kt   (자산 연결/교체/제거)
│   └── ChurchMemberPhotoStreamer.kt  (인증된 사진 스트리밍 서비스)
├── infrastructure/persistence/
│   ├── ChurchMemberRepository.kt
│   ├── ChurchMemberFaithRepository.kt
│   └── ChurchMemberAuditLogRepository.kt
└── interfaces/
    ├── api/
    │   ├── ChurchMemberAdminController.kt
    │   └── ChurchMemberPhotoController.kt   (인증 프록시 스트리밍 엔드포인트)
    └── dto/
        └── ChurchMemberAdminDtos.kt
```

### 3.2 공용 인프라 (신규)

```
org.happyzion.api.common.security.pii/
├── PiiEncryptionProperties.kt        (@ConfigurationProperties + @Validated)
│                                      └ ApiApplication의 @EnableConfigurationProperties에 등록
├── PiiKeyRing.kt                     (keyId → SecretKey, activeKeyId 보유)
├── PiiEncryptor.kt                    (AES-256-GCM, '<keyId>:base64' 포맷)
├── PiiHasher.kt                       (HMAC-SHA256)
├── MemberSearchKeyNormalizer.kt       (검색·저장 정규화 메서드 분리)
├── EncryptedStringConverter.kt        (@Converter, PiiEncryptor 빈 주입)
└── EncryptedLocalDateConverter.kt     (@Converter, ISO 문자열 변환 포함)
```

**`@Converter`의 빈 주입 (결정 #12 반영)**: Hibernate의 `SpringBeanContainer`를 활성화한다. `LocalContainerEntityManagerFactoryBean`(또는 Spring Boot의 `HibernatePropertiesCustomizer`)에서 `hibernate.resource.beans.container`에 `SpringBeanContainer`를 등록하면 컨버터가 Spring 빈 의존을 받는다. 정적 holder(`PiiEncryptorHolder`) 방식은 **채택하지 않는다** — 테스트 간 정적 상태 오염, 시작 시점 의존성이 암묵적, 키 회전·다중 인스턴스 시나리오에서 사고 가능성.

**키 누락 시점**: `PiiEncryptionProperties`의 `@Validated` 검증이 startup 시점에 동작. 키 비어있으면 `ApplicationContextException`으로 부팅 자체가 실패 — 런타임 첫 암복호 시점이 아니라 부팅 시점에 즉시 드러난다.

**테스트 대체 전략**:
- 단위 테스트: 도메인·서비스 로직만 검증, `EncryptedStringConverter` 우회
- JPA 통합 테스트(`@DataJpaTest`/슬라이스): `src/test/resources/application.yml`에 테스트 전용 PII 키 셋 명시, `PiiEncryptionProperties`가 자동 바인딩. `SpringBeanContainer` 설정도 테스트 컨텍스트에서 그대로 활성화

### 3.3 기존 모듈 변경

| 파일/대상 | 변경 |
|---|---|
| `board/domain/PostAssetKind.kt` | enum에 `MEMBER_PHOTO` 추가 (기존: `INLINE_IMAGE`, `FILE_ATTACHMENT`, `MAIN_VIDEO`) |
| `board/application/AttachmentStorage.kt` | `fun load(storedPath: String): Resource` 추가 (인터페이스). 구현체는 **path traversal 방어 의무**: `root.resolve(storedPath).normalize().startsWith(root)` 검증 실패 시 `NotFoundException`(외부에 구조 노출 안 함) |
| `board/application/LocalAttachmentStorage.kt` | `load` 구현 — 위 traversal 가드 + 파일 미존재 시 `NotFoundException` + `UrlResource(target.toUri())` 반환. `buildStoredPath`가 `kind == MEMBER_PHOTO`인 경우 `member-photos/YYYY/MM/UUID.ext` prefix 사용 |
| `board/application/UploadAssetService.kt` | `upload`에서 토큰 소비 후 `kind == MEMBER_PHOTO`이면 `adminAccountGuard.verify(validation.actorId)` 호출 (결정 #11 잔여 리스크 차단). 가드 실패 시 이미 저장된 파일은 `attachmentStorage.delete`로 롤백 |
| `board/interfaces/api/UploadAdminController.kt` | `issueToken`에서 `kind == MEMBER_PHOTO`이면 ① `adminAccountGuard.verify(actorId)` 호출, ② 요청 본문의 `allowedMimeTypes`·`maxByteSize`를 무시하고 `MemberPhotoUploadPolicy` 상수로 override (결정 #10·#11 반영) |
| `adminaccount/application/AdminAccountGuard.kt` (신규) | `adminaccount.application` 패키지. `AdminAccountRepository` 의존, 미존재 시 `UnauthorizedException`, 비활성 시 `ForbiddenException` |
| `common/security/MemberPhotoUploadPolicy.kt` (신규) | `MEMBER_PHOTO_ALLOWED_MIME = listOf("image/jpeg","image/png","image/webp")`, `MEMBER_PHOTO_MAX_BYTES = 5 * 1024 * 1024` |
| `common/logging/RequestLoggingFilter.kt` | `buildRequestPath`가 path-aware redaction 적용 — `/api/v1/admin/members`(이하 사진/감사 로그 하위 경로 포함)에서 `name`·`phone` query 파라미터 값을 `[REDACTED]`로 치환 후 로그 (결정 #9 반영). 키 매칭은 **대소문자 무시**, 중복 파라미터·URL-encoded 값 모두 마스킹 |
| `common/security/AdminAuthInterceptor.kt` | **변경 없음** (active 검증은 별도 가드로 대체) |
| `common/config/WebConfig.kt` | `addCorsMappings`에 `/api/v1/admin/members/**` 매핑 신규 추가. `allowedMethods = ["GET","POST","PUT","DELETE","OPTIONS"]`, `allowedHeaders = ["Content-Type","Authorization"]`. 사진 GET이 fetch+blob 패턴으로 호출될 가능성을 대비해 `Authorization` 허용 |
| `deploy/nginx/api.happyzion.com.conf` | `/upload/` 일반 location보다 **위쪽**에 `location ^~ /upload/member-photos/ { return 404; }` 추가. `^~` modifier로 prefix 일치 시 정규식 location 평가를 중단시켜 우선 매칭 보장. 외부에는 경로 자체가 없는 것처럼 보이도록 404. |
| `common/config/JpaConfig.kt` (신규 또는 기존 확장) | `HibernatePropertiesCustomizer`로 `hibernate.resource.beans.container`에 `SpringBeanContainer` 등록 (결정 #12 반영) |
| `ApiApplication.kt` `@EnableConfigurationProperties` | `PiiEncryptionProperties::class` 등록 |
| `.env.example`, `.env.production.example` | `HAPPYZION_PII_ENCRYPTION_KEYS`, `HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID`, `HAPPYZION_PII_HASH_KEY` 추가 |
| `application.yml` | 위 키 바인딩 |
| `deploy/docker-compose.prod.yml` | `app` 서비스의 `environment:`에 위 세 변수 전달 추가 |
| `src/test/kotlin/org/happyzion/api/support/EnvironmentConfigContractTest.kt` | 새 환경변수 키 계약 추가 |
| `src/test/kotlin/org/happyzion/api/board/BoardSchemaContractTest.kt` | 마이그레이션 목록(`containsExactly`)에 V8 추가 — V1 베이스라인 단언은 그대로 |

## 4. DB 스키마 — V8 마이그레이션

파일: `src/main/resources/db/migration/V8__create_church_member_registry.sql`

### 4.1 `church_member` (인적사항)

```sql
create table church_member (
    id                       bigserial primary key,

    -- 암호화
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

    -- 평문 (운영 필터·정렬·집계)
    sex                      varchar(1)   not null,
    birth_calendar           varchar(10)  not null,
    photo_asset_id           bigint references post_asset(id),       -- ★ 단일 FK + UNIQUE (리뷰 #2 반영)
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
```

`photo_path` 컬럼은 두지 않는다. 사진 URL은 `post_asset.stored_path`를 조인해서 얻고, 응답에는 외부에 직접 노출하지 않는다(인증 프록시 경로만 노출).

### 4.2 `church_member_faith` (신앙정보, 1:1)

```sql
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
```

### 4.3 `church_member_audit_log` (감사 로그)

`on delete restrict`로 변경 (리뷰 #5 반영). soft delete만 두므로 운영 중에는 영향 없지만, 운영 실수·향후 정책 변경 시에도 감사 이력이 보호된다.

```sql
create table church_member_audit_log (
    id                bigserial primary key,
    church_member_id  bigint not null references church_member(id) on delete restrict,  -- ★ restrict
    actor_id          bigint not null references admin_account(id) on delete restrict,  -- ★ restrict
    action            varchar(20) not null,
    diff_enc          text,
    created_at        timestamptz not null default now(),
    constraint chk_church_member_audit_action check (action in ('CREATE','UPDATE','DELETE'))
);

create index idx_church_member_audit_member_id
    on church_member_audit_log(church_member_id, created_at desc, id desc);
```

### 4.4 기존 테이블 CHECK 제약 확장 (리뷰 #1·#7 반영)

두 테이블의 정책이 다르다는 점을 명확히 분리한다.

- **`post_asset`**: V1 이래 `INLINE_IMAGE`·`FILE_ATTACHMENT`만 허용. 메인 영상은 `post_asset` row를 만들지 않기 때문에 V3에서도 추가하지 않았다. V8은 여기에 **`MEMBER_PHOTO`만 추가** — `MAIN_VIDEO`는 의도적으로 미포함.
- **`upload_token`**: V3에서 `MAIN_VIDEO`까지 허용했다. V8은 그 위에 **`MEMBER_PHOTO`를 추가**하여 4종 모두 허용.

```sql
-- post_asset: MAIN_VIDEO는 포함하지 않음 (의도)
alter table post_asset
    drop constraint chk_post_asset_kind;
alter table post_asset
    add constraint chk_post_asset_kind
    check (kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MEMBER_PHOTO'));

-- upload_token: MAIN_VIDEO 보존 + MEMBER_PHOTO 추가
alter table upload_token
    drop constraint chk_upload_token_asset_kind;
alter table upload_token
    add constraint chk_upload_token_asset_kind
    check (asset_kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MAIN_VIDEO','MEMBER_PHOTO'));
```

### 4.5 정규화 규칙 (검색 blind index) — 리뷰 #6 반영

`MemberSearchKeyNormalizer`는 **저장용**과 **검색 입력용**을 명시적으로 분리한다.

**저장용**:
- `forStoredName(raw): String` — NFKC → 모든 공백 제거 → 소문자. 결과가 빈 문자열이면 `IllegalArgumentException("이름은 비어 있을 수 없습니다.")`
- `forStoredPhone(raw): String` — 숫자만 추출. **9자리 미만이면 `IllegalArgumentException`**

**검색 입력용**:
- `forNameQuery(raw): String?` — `forStoredName`과 동일 함수 사용. 입력이 비면 `null` 반환(검색 조건 무시).
- `forPhoneQuery(raw): PhoneQueryKey?` — 숫자만 추출 후:
  - 정확히 4자리 → `PhoneQueryKey.Last4(hash)`
  - 9자리 이상 → `PhoneQueryKey.Full(hash)`
  - 그 외 (0~3, 5~8자리) → `null` (검색 조건은 무시되지 않고 "빈 결과"로 처리)

저장 정규화가 더 엄격한 이유는, 저장 시점의 데이터 무결성을 가능한 한 보장하기 위해서. 검색은 사용자 입력의 다양성을 흡수해야 하므로 더 너그럽게 동작.

## 5. 도메인 모델

### 5.1 엔티티 — 코드 스타일

엔티티 필드는 평문 노출(`name`, `phone`, `birthDate`), `@Column(name = "name_enc")` + `@Convert`로 영속화 경계에서만 암호문으로 변환. 도메인 코드는 평문만 본다.

해시 컬럼은 별도 필드로 두되, 변경 메서드에서 원문 변경과 해시 갱신을 항상 한 단위로 처리한다.

### 5.2 변경 메서드 시그니처 (예시)

```kotlin
fun rename(newName: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer)
fun changePhone(newPhone: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer)
fun changeAddress(newAddress: String, newDetail: String?)
fun changeStatus(newStatus: ChurchMemberStatus)
fun markRemoved()    // status=REMOVED 전용 경로
fun appointOffice(office: ChurchMemberOffice, at: LocalDate?)
fun linkPhoto(asset: PostAsset)     // photo_asset_id 갱신
fun unlinkPhoto()                   // photo_asset_id = null
// ...
```

### 5.3 `ChurchMemberFaith` 매핑

`@OneToOne(fetch = LAZY)` + `@MapsId`로 PK 공유. cascade는 DB 한쪽(`on delete cascade`)에만 두고 JPA 측에는 두지 않는다.

### 5.4 `ChurchMemberAuditLog`

append-only — 단, **DB 차원 강제는 두지 않는다 (결정 #7 반영)**. 보장 메커니즘은 코드 관례:
- 엔티티에 setter 없음, `var` 없음 (모든 필드 `val`)
- 리포지토리는 `save`(INSERT)와 `findAll*` 등 조회만 노출. `delete*`·`saveAndFlush` 같은 변경/삭제 메서드를 막지는 않지만 호출 지점 0개로 유지
- 향후 운영에서 강한 보장이 필요해지면 V9에서 `update`/`delete` 방지 trigger 추가하는 형태로 확장 가능

`diff_enc`에 JSON 직렬화 결과 통째 암호화.

## 6. API

### 6.1 엔드포인트

모두 `@AdminAuthRequired` (Bearer 토큰 + active admin 검증, §7.4 참고).

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/v1/admin/members` | 목록 (검색·필터·페이지네이션) |
| POST | `/api/v1/admin/members` | 등록 (인적 + 선택적 신앙정보) |
| GET | `/api/v1/admin/members/{id}` | 상세 |
| PUT | `/api/v1/admin/members/{id}` | 전체 치환 수정 |
| DELETE | `/api/v1/admin/members/{id}` | soft delete (`status=REMOVED`) |
| POST | `/api/v1/admin/members/{id}/photo` | 사진 연결/교체 (본문 `assetId`) |
| DELETE | `/api/v1/admin/members/{id}/photo` | 사진 제거 |
| **GET** | **`/api/v1/admin/members/{id}/photo`** | **사진 이미지 바이너리 스트리밍** (인증 프록시) |
| GET | `/api/v1/admin/members/{id}/audit-logs` | 변경 이력 페이징 조회 |

사진 업로드 자체는 기존 `POST /api/v1/admin/uploads/token` → `POST /api/v1/admin/uploads`(`kind=MEMBER_PHOTO`)를 그대로 사용한다.

### 6.2 사진 서빙 — Spring 인증 프록시 (리뷰 #4 반영)

```
GET /api/v1/admin/members/{id}/photo
  @AdminAuthRequired
  ├ adminAccountGuard.verify(actorId)
  ├ memberRepo.findById(id) → photoAssetId 확인 (없으면 404)
  ├ postAssetRepo.findById(photoAssetId) → stored_path 확인
  ├ attachmentStorage.load(stored_path) → Resource   ★ path traversal 가드 통과
  └ ResponseEntity
       .ok()
       .contentType(MediaType.parseMediaType(asset.mimeType))
       .cacheControl(CacheControl.noStore())   // 캐시 금지, 민감정보
       .body(resource)
```

**`attachmentStorage.load` 구현 의무 (path traversal 방어)**:
```kotlin
override fun load(storedPath: String): Resource {
    val target = rootPath.resolve(storedPath).normalize()
    if (!target.startsWith(rootPath)) {
        throw NotFoundException("자산을 찾을 수 없습니다.")  // 외부에 구조 노출 방지: 404로 통일
    }
    if (!Files.isRegularFile(target)) {
        throw NotFoundException("자산을 찾을 수 없습니다.")
    }
    return UrlResource(target.toUri())
}
```

`storedPath`가 DB(`post_asset.stored_path`)에서 온 값이라 통제된 입력이긴 하지만, **인증 프록시는 실제 파일 시스템 경계**이므로 코드 손상·잘못된 마이그레이션 시나리오를 막는 의미로 traversal 가드를 의무화한다.

저장 시점에 `LocalAttachmentStorage.buildStoredPath`가 `kind == MEMBER_PHOTO`이면 `member-photos/YYYY/MM/UUID.ext` prefix로 저장. nginx는 `^~` modifier prefix location으로 `/upload/member-photos/`를 우선 차단 — 즉 외부 URL로는 절대 접근 불가하고 오직 위 엔드포인트만 노출한다.

### 6.3 사진 attach 생명주기 (리뷰 #3·#5·#6 반영)

기존 `UploadAssetService`가 업로드 직후 자산을 **`detached_at = now()`** 상태로 저장하고, 게시판은 attach 시 `detached_at = null`로 전환한다. 멤버 사진도 이 모델을 그대로 따른다.

**attach 검증 (`ChurchMemberPhotoService.replacePhoto(member, assetId, actorId)`)**:
- `assetId`로 자산 조회 — 없으면 `NotFoundException`
- `asset.kind == MEMBER_PHOTO` — 아니면 `IllegalArgumentException`
- `asset.detachedAt != null` — null이면 "이미 다른 곳에 사용 중", `IllegalArgumentException`
- **`asset.uploadedByActorId == actorId`** — 아니면 `ForbiddenException`. 다른 관리자가 올린 detached 자산을 가로채는 것 방지 (`BoardAdminService:316` 패턴과 동일, 리뷰 #3 반영)
- `church_member.photo_asset_id`의 UNIQUE 제약이 "다른 멤버에 이미 연결됨"을 DB 차원에서 보장. **`DataIntegrityViolationException`은 서비스에서 try-catch로 잡아 `IllegalArgumentException("이미 다른 교인에 연결된 사진입니다.")`로 변환** — 전역 핸들러에 추가 매핑 도입하지 않음 (모듈 격리, 리뷰 #5 반영)
- 검증 통과 시: 기존 멤버 자산이 있으면 그 자산의 `detached_at = now()` 세팅 → 새 자산의 `detached_at = null`, `post_id = null` 유지 → 멤버의 `photo_asset_id = 새 자산 id`
- **`audit_writer.recordUpdate(memberId, actorId, before, after)`** 호출 — `photoAssetId` 변경이 diff에 포함됨 (리뷰 #6 반영)

**detach (`removePhoto`)**:
- `member.photo_asset_id`가 가리키는 자산의 `detached_at = now()` 세팅
- `member.photo_asset_id = null`
- 실제 파일 삭제는 기존 `PostAssetCleanupService` 스케줄러가 처리
- **`audit_writer.recordUpdate`** 호출하여 photoAssetId의 `[id, null]` diff 기록

attach·detach 모두 멤버 인적사항 변경과 동일하게 audit log를 남긴다 — 얼굴 사진은 교적 정보의 일부.

### 6.4 목록 쿼리 파라미터

```
GET /api/v1/admin/members
  ?name=김철수
  &phone=01012345678   (또는 4자리만 ?phone=5678)
  &status=ACTIVE,NEW
  &faithStage=DISCIPLE
  &cellLabel=1구역
  &includeInactive=false
  &page=0&size=20
```

정렬은 `registered_at DESC, id DESC` 고정. 응답에 `hasNext` 플래그.

`phone` 파라미터는 `MemberSearchKeyNormalizer.forPhoneQuery`가 분기 처리.

### 6.5 서비스 책임 분할

```
ChurchMemberAdminService            list/get/listAuditLogs/create/update/softDelete/attachPhoto/detachPhoto
ChurchMemberAuditWriter             recordCreate / recordUpdate / recordDelete (diff 계산 + 암호화)
ChurchMemberPhotoService            replacePhoto / removePhoto (자산 검증·교체·detach)
ChurchMemberPhotoStreamer           loadForResponse(memberId, actorId): StreamedPhoto (인증 후 Resource + 메타)
```

### 6.6 핵심 흐름: `updateMember`

```
1. Controller: actorId, id, ChurchMemberSaveRequest 수신
2. service.updateMember(id, request.toCommand(), actorId)
   ├ activeAdminGuard.verify(actorId)               (§7.4)
   ├ memberRepo.findById(id) → 평문 도메인 객체
   ├ ChurchMemberSnapshot.of(member) → before
   ├ 도메인 변경 메서드 호출
   ├ ChurchMemberSnapshot.of(member) → after
   ├ auditWriter.recordUpdate(id, actorId, before, after)
   └ memberRepo.save(member)
3. Controller가 도메인 객체를 응답 DTO(평문)로 변환
```

## 7. 에러·검증·트랜잭션·권한

### 7.1 에러 매핑 (기존 GlobalExceptionHandler 활용)

| 상황 | 예외 | HTTP | 코드 |
|---|---|---|---|
| Bearer 토큰 없음/만료 | `UnauthorizedException` (인터셉터) | 401 | UNAUTHORIZED |
| 비활성 admin (§7.4) | `ForbiddenException` | 403 | FORBIDDEN |
| 존재하지 않는 멤버/자산 | `NotFoundException` | 404 | NOT_FOUND |
| 자산 검증 실패 (kind 불일치, 이미 사용 중, 다른 멤버에 연결됨) | `IllegalArgumentException` | 400 | INVALID_REQUEST |
| `@Valid` 실패 | `MethodArgumentNotValidException` | 400 | INVALID_REQUEST |
| 도메인 invariant 위반 | `IllegalArgumentException` | 400 | INVALID_REQUEST |
| 미처리 | `Exception` 폴백 | 500 | INTERNAL_SERVER_ERROR |

### 7.2 검증 레이어

- **DTO**: Bean Validation 어노테이션
- **도메인 생성자/변경 메서드**: 정규화 후 빈 값 거부, 전화 9자리 미만 거부, `officeAppointedAt` 미래 거부, `status=REMOVED` 직접 지정 차단

### 7.3 트랜잭션 경계

- application 서비스 메서드 단위 `@Transactional` (read는 `readOnly = true`)
- 멤버 변경 + audit + 사진 정리는 단일 트랜잭션
- 낙관적 락 미도입. last-write-wins, audit log로 사후 추적

### 7.4 active admin 검증 (리뷰 #9·#11 반영)

`AdminAuthInterceptor`는 JWT만 검증하고 `admin_account.active` 상태는 확인하지 않는다 (`AdminAuthInterceptor.kt:26`). 이 모듈은 PII를 다루므로 추가 가드를 둔다.

**범위·위치 결정**: 인터셉터 자체 수정은 본 모듈 범위 밖(다른 어드민 API도 영향). 가드는 **`adminaccount.application.AdminAccountGuard`** 위치 — `AdminAccountRepository`가 같은 모듈 안에 있으므로 의존이 모듈 내부로 닫힌다. (이전 안의 `common.security` 배치는 common이 feature persistence에 역의존하는 모양이라 폐기.)

호출 지점 **세 군데**:

1. **`ChurchMemberAdminService` 모든 진입점** — read·write 모두
2. **`UploadAdminController.issueToken`** — `kind == MEMBER_PHOTO`일 때만 호출 (다른 kind는 기존 동작 유지, board·video 모듈 영향 0)
3. **`UploadAssetService.upload`** — 토큰 소비 후 `kind == MEMBER_PHOTO`일 때만 호출. 토큰 유효 5분 동안 계정이 비활성화되어도 실제 파일·post_asset row 생성을 차단

```kotlin
// adminaccount/application/AdminAccountGuard.kt
@Component
class AdminAccountGuard(private val adminAccountRepository: AdminAccountRepository) {
    fun verify(actorId: Long) {
        val admin = adminAccountRepository.findById(actorId).orElse(null)
            ?: throw UnauthorizedException("관리자 계정을 찾을 수 없습니다.")
        if (!admin.active) throw ForbiddenException("비활성 관리자입니다.")
    }
}
```

`UploadAssetService.upload`에서 가드가 실패하면 이미 저장된 파일은 `attachmentStorage.delete(storedPath)`로 롤백한다 (기존 `RuntimeException` 처리 흐름과 동일 패턴 적용).

audit log 조회(`listAuditLogs`)도 동일 가드 통과한 admin만 허용. 권한 롤 세분화(super-admin만 audit 조회 등)는 Phase 1 비범위(PRD §4 callout과 동일 결정).

### 7.5 검색 PII redaction (리뷰 #9 반영)

`RequestLoggingFilter.kt:49-52`가 query string 전체를 INFO 레벨로 기록하므로, `name=김철수&phone=01012345678` 같은 검색이 access 로그에 평문으로 남는다. blind index를 써도 운영 로그에서 평문이 새는 셈.

**해결**: `buildRequestPath`에 path-aware redaction을 추가한다.

- **redaction 대상 path**: `/api/v1/admin/members`로 시작하는 모든 경로 (목록·상세·사진·audit 포함)
- **redaction 대상 파라미터 키**: `name`, `phone` (대소문자 무시)
- **방식**: query string을 `&` 분리 후 각 토큰의 `key=value`에서 key를 case-insensitive 비교. 매칭되는 key의 값만 `[REDACTED]`로 치환. **중복 파라미터(`name=A&name=B`) 모두 마스킹**, **URL-encoded 값(`name=%EA%B9%80%EC%B2%A0%EC%88%98`)은 디코드 없이 그대로 마스킹** (디코드 시점 자체가 새로운 누수 경로가 되므로). value가 없는 경우(`name=`, `name`) 그대로 둠 (평문 누수 아님).
- 다른 path는 query 무손실 — 기존 동작 회귀 방지

검색을 POST body로 전환하는 옵션은 채택하지 않는다 — REST 컨벤션과 캐싱·북마크 UX, 다른 모듈과의 컨벤션 정합이 깨짐. 필터 한 곳 수정이 보다 가성비 좋다.

### 7.6 MEMBER_PHOTO 업로드 정책 강제 (리뷰 #4·#10 반영)

`UploadTokenService.issueToken`은 인자로 받은 `allowedMimeTypes`·`maxByteSize`를 그대로 저장한다 (클라이언트가 보낸 값 신뢰). `LocalAttachmentStorage.detectMimeType`은 PDF까지 받는다. 회원 사진은 별도 정책을 서버가 강제한다.

**`MemberPhotoUploadPolicy` 상수** (`common.security`):
```kotlin
object MemberPhotoUploadPolicy {
    val ALLOWED_MIME = listOf("image/jpeg", "image/png", "image/webp")
    const val MAX_BYTES = 5L * 1024 * 1024   // 5 MiB
}
```

**`UploadAdminController.issueToken` 수정 로직**:
```kotlin
val effectiveMimes = if (request.kind == PostAssetKind.MEMBER_PHOTO) MemberPhotoUploadPolicy.ALLOWED_MIME else request.allowedMimeTypes
val effectiveMax  = if (request.kind == PostAssetKind.MEMBER_PHOTO) MemberPhotoUploadPolicy.MAX_BYTES else request.maxByteSize
// 그 후 uploadTokenService.issueToken(..., kind, effectiveMax, effectiveMimes)
```

이렇게 하면 PDF·과대 파일 업로드가 토큰 단에서 차단되고, 후속 `LocalAttachmentStorage.detectMimeType`이 PDF 처리 분기로 빠지더라도 토큰 화이트리스트와 MIME 매칭 검증(`UploadTokenService.validateAndConsume` line 65)에서 막힌다 — **이중 안전망**.

## 8. 테스트 전략

### 8.1 단위 테스트

- `ChurchMemberTest` — 변경 메서드의 hash 동시 갱신, 도메인 invariant
- `ChurchMemberAdminServiceTest` — CRUD/검색/사진/감사 통합 동작 (mockito-kotlin)
- `ChurchMemberAuditWriterTest` — diff 계산, CREATE/UPDATE/DELETE별 기록
- `ChurchMemberPhotoServiceTest` — 자산 검증(kind, detached_at != null, 다른 멤버 사용 중), 교체 시 기존 detach
- `ChurchMemberPhotoStreamerTest` — 인증 가드, 자산 없음 → 404, mime 분기
- `MemberSearchKeyNormalizerTest` — 한·영·전각/반각·이모지·트레일링 공백, 저장 vs 검색 분기 동작
- `PiiEncryptorTest` — round-trip, IV 랜덤성, key id 접두사, 다중 key id 복호, tamper 거부
- `PiiHasherTest` — 결정성
- `PiiKeyRingTest` — KEYS 파싱, activeKeyId 누락 시 부팅 실패, 키 누락 시 부팅 실패
- `AdminAccountGuardTest` — 미존재/비활성/정상 케이스
- `EncryptedStringConverterTest`, `EncryptedLocalDateConverterTest` — null/empty round-trip

### 8.2 계약 테스트

- **`BoardSchemaContractTest` 갱신**: 21-29번 `containsExactly` 목록에 `"V8__create_church_member_registry.sql"` 추가. V1 기준 단언은 그대로 유지.
- **`MemberSchemaContractTest` 신규**: V8 마이그레이션 적용 결과 검증
  - 테이블·인덱스·CHECK 제약·trigger 존재
  - **`post_asset` CHECK는 `INLINE_IMAGE`·`FILE_ATTACHMENT`·`MEMBER_PHOTO`만 (MAIN_VIDEO 포함되지 않음)** — 리뷰 #7
  - **`upload_token` CHECK는 4종 모두 (`MAIN_VIDEO` 보존 + `MEMBER_PHOTO` 신규)** — 리뷰 #7
  - audit log FK가 `on delete restrict`
- **`ChurchMemberRepositoryContractTest`**: 
  - raw SQL로 `name_enc` / `phone_enc` / `birth_date_enc` / `address_enc` / `address_detail_enc` / `job_enc` / `email_enc` / `memo_enc` / `confess_date_enc` 등 모든 `_enc` 컬럼이 평문을 포함하지 않음
  - raw SQL로 `church_member_audit_log.diff_enc`에 변경 전/후 평문이 들어가지 않음
  - blind index 정확일치 검색 동작 (정규화 적용)
  - `photo_asset_id` UNIQUE — 같은 자산을 두 멤버에 연결 시 `DataIntegrityViolationException`
- **`EnvironmentConfigContractTest` 갱신**: `HAPPYZION_PII_ENCRYPTION_KEYS`, `HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID`, `HAPPYZION_PII_HASH_KEY` 키 계약 추가
- **`RequestLoggingFilterTest` 추가 (PII redaction)** — 다음 케이스 모두:
  - 기본: `/api/v1/admin/members?name=김철수&phone=01012345678&page=0` → `name`/`phone` 값에 평문 미포함, `page=0`은 그대로
  - 대소문자: `?NAME=김철수&Phone=01012345678` → 마스킹됨 (case-insensitive)
  - 중복 파라미터: `?name=A&name=B` → 두 값 모두 `[REDACTED]`
  - URL-encoded: `?name=%EA%B9%80%EC%B2%A0%EC%88%98` → 인코딩된 토큰조차 로그에 남지 않음 (디코드 없이 마스킹)
  - 빈 값: `?name=&phone=` → 형태 그대로 (마스킹 의미 없음, 누수 없음)
  - 사진/감사 로그 하위 경로: `/api/v1/admin/members/123/photo`, `/api/v1/admin/members/123/audit-logs?name=...`도 동일 마스킹
  - **회귀 방지**: `/api/v1/admin/boards/...`, `/api/v1/admin/menu/...` 등 다른 path는 query 무손실
- **`UploadAdminController` 테스트 갱신**:
  - `kind = MEMBER_PHOTO` + 비활성 admin → 403
  - `kind = MEMBER_PHOTO` + 클라이언트가 `allowedMimeTypes = ["application/pdf"]` 보내도 토큰의 `allowed_mime_types`는 서버 상수 3종으로 override됨
  - `kind = INLINE_IMAGE`는 active 가드·정책 override 없이 기존 동작 유지 (회귀 방지)
- **`UploadAssetService` 테스트 갱신**:
  - `kind = MEMBER_PHOTO` 토큰 발급 후 admin이 비활성화된 상태에서 `upload` 호출 → 403, 저장된 파일은 `attachmentStorage.delete` 호출 (롤백)
  - `kind = INLINE_IMAGE`는 기존 동작 유지
- **`LocalAttachmentStorage` 테스트 갱신**:
  - `load` path traversal: `../../etc/passwd` 같은 입력 → `NotFoundException`
  - `load` 정상 경로 → `UrlResource` 반환, byte 일치
  - `load` 파일 미존재 → `NotFoundException`

### 8.3 컨트롤러 테스트 (MockMvc)

- `ChurchMemberAdminControllerTest` — 인증/검증/404/정상 응답
- `ChurchMemberPhotoControllerTest` — 인증 가드, 자산 없는 멤버 → 404, mime 분기, `Cache-Control: no-store` 헤더

### 8.4 OpenAPI

`./gradlew build` 시 `OpenApiSpecGenerationTest` 자동 트리거. 신규 컨트롤러 자동 반영.

## 9. 비범위

PRD §3 비범위 + 추가:
- 부분일치 이름 검색 (blind index 한계)
- 생년월일 정렬·범위 검색 (`birth_date_enc` 암호화)
- 신앙정보 필드 단위 통계
- Hibernate Envers 등 자동 감사
- audit log append-only DB trigger 강제 (앱 관례로만 보장, V9에서 도입 가능)
- 교적부 권한 롤 분리 (PRD §4 callout)
- `AdminAuthInterceptor` 자체에 active admin 검증 도입 (다른 모듈 영향 범위로 별도 과제. 교적부·MEMBER_PHOTO 경로는 `AdminAccountGuard`로 커버)
- 키 회전 자동화 배치 (수동 절차만 문서화)
- 사진 별도 비공개 저장소(S3 등) 이전
- 검색 PII 외 다른 파라미터(예: 향후 추가될 `email`, `birthDate` 등) redaction (현재 결정 #9는 `name`·`phone` 두 키만 처리)

## 10. Phase 2 (변경 없음)

PRD §9 그대로.

## 11. 운영 출시 전 체크리스트

1. `HAPPYZION_PII_ENCRYPTION_KEYS`, `HAPPYZION_PII_ENCRYPTION_ACTIVE_KEY_ID`, `HAPPYZION_PII_HASH_KEY` 생성·안전 저장 (KMS/Vault 권장)
2. PostgreSQL 백업 저장 위치 암호화 여부 확인
3. 교적부 권한 롤 분리 (PRD §4 callout)
4. nginx `/upload/member-photos/` deny 규칙 실제 배포 환경 적용 확인
5. `AdminAuthInterceptor`에 active admin 검증 도입 (별도 과제)
