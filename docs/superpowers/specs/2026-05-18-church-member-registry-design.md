# 교적부(Church Member Registry) 설계 — Phase 1

- 작성일: 2026-05-18
- 상태: 구현 계획 수립 직전
- 기반 문서: `/Users/T/Downloads/PRD-church-member-registry.html` (PRD v0.1)
- 대상 서비스: `happyzion-api` (Kotlin + Spring Boot 3.5, JDK 21, PostgreSQL)

## 1. 개요

PRD §1~§4 그대로. 관리자 전용 교적부 기능을 신규 슬라이스(`org.happyzion.api.member`)로 구현하며, 인적사항·신앙정보·변경 감사 로그를 다룬다. 이전 시도(V1 도입 → V7 제거)와 달리 PRD가 명시한 범위만 구현한다.

본 설계는 PRD가 둔 5개 결정 항목과, 브레인스토밍 중 추가로 식별된 3개 결정 항목에 대한 합의를 반영한다.

## 2. 확정된 결정사항

### PRD §8에서 가져온 5개

| # | 항목 | 결정 |
|---|---|---|
| 1 | `faith_stage` 위치 | `church_member` 본 테이블 (V1 패턴 복귀). 목록 필터에 자주 쓰일 가능성과 단일 인덱스 활용을 고려. |
| 2 | 교적번호(`member_no`) 도입 | 도입하지 않음. `bigserial id`만 사용. 필요 시 Phase 2에서 컬럼 추가로 도입 가능 (YAGNI). |
| 3 | 삭제 정책 | Soft delete만 — `status = 'REMOVED'`로 표시하고 audit log에 `action = 'DELETE'`로 기록. 물리 삭제 API 없음. |
| 4 | 목록 기본 필터 | `REMOVED`·`DECEASED`는 기본 숨김, `includeInactive=true` 토글로 노출. |
| 5 | X-Admin-Key 전환 전 출시 | 무효화됨 — 코드베이스는 이미 JWT(`AdminAuthInterceptor` + `@AdminAuthRequired` + Bearer 토큰)로 전환됨. `audit_log.actor_id`는 처음부터 `NOT NULL`로 설계. |

### 브레인스토밍 중 추가 결정 3개

| # | 항목 | 결정 |
|---|---|---|
| 6 | 사진 업로드 패턴 | 기존 `post_asset` / `UploadTokenService` / `UploadAssetService` 인프라 재사용. `PostAssetKind`에 `MEMBER_PHOTO` 값 추가. `church_member.photo_path`는 평문 보관(자체가 랜덤 토큰이라 식별 정보 없음). |
| 7 | 감사 로그 캡처 방식 | 서비스 레이어에서 명시적 before/after 스냅샷 비교 후 JSON diff 생성. Hibernate Envers·`@EntityListener` 자동화는 도입하지 않음. |
| 8 | 입력 검증 강도 | Bean Validation으로 필수/길이/enum 등 형식만 검증. 전화번호 형식 등 정규식 강제는 하지 않음. 도메인 객체는 의미적 invariant(빈 이름 거부 등)만 추가로 검증. |

### 개인정보 보호 결정 (별도 합의)

교적부는 민감 개인정보(이름·주소·전화·신앙 이력)를 다루므로 application-level AES-256-GCM 암호화를 적용한다.

| 카테고리 | 컬럼 | 처리 |
|---|---|---|
| 검색 필요 + 강한 보호 | `church_member.name`, `phone` | AES-GCM 암호화 + HMAC-SHA256 blind index (`name_hash`, `phone_hash`, `phone_last4_hash`) |
| 강한 보호 (검색 불필요) | `church_member.email`, `address`, `address_detail`, `job`, `memo`, `birth_date` | AES-GCM 확률적 암호화. `birth_date`는 ISO 'YYYY-MM-DD' 문자열로 변환 후 암호화. |
| 종교 민감정보 (PIPA) | `church_member_faith.*` 전 데이터 컬럼 | AES-GCM 확률적 암호화. 날짜는 ISO 문자열 변환. CHECK 제약 제거(application 검증으로 이동). |
| 감사 로그 본문 | `church_member_audit_log.diff_enc` | diff JSON 직렬화 후 통째 AES-GCM 암호화. before/after 값이 평문으로 노출되지 않음. |
| 평문 유지 (운영·필터·정렬) | `id`, `sex`, `birth_calendar`, `status`, `faith_stage`, `office`, `office_appointed_at`, `registered_at`, `cell_label`, `photo_path`, `created_at`, `updated_at`, audit log의 메타 컬럼 | 평문 |

검색 UX: 이름은 완전일치만, 전화번호는 풀번호 완전일치 또는 끝 4자리 완전일치만. 부분일치(예: "김"으로 시작) 검색은 제공하지 않음.

키 관리:
- `HAPPYZION_PII_ENCRYPTION_KEY` — base64 인코딩 32바이트, AES-256 키
- `HAPPYZION_PII_HASH_KEY` — base64 인코딩 32바이트, HMAC-SHA256 키 (암호화 키와 분리)
- 저장 포맷: `v1:<base64(12B IV ‖ ciphertext ‖ 16B GCM tag)>` — 버전 접두사로 향후 키 회전 시 멀티 키 복호 가능

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
│   └── ChurchMemberPhotoService.kt
├── infrastructure/persistence/
│   ├── ChurchMemberRepository.kt
│   ├── ChurchMemberFaithRepository.kt
│   └── ChurchMemberAuditLogRepository.kt
└── interfaces/
    ├── api/
    │   └── ChurchMemberAdminController.kt
    └── dto/
        └── ChurchMemberAdminDtos.kt
```

### 3.2 공용 인프라 (신규)

암호화는 향후 다른 모듈도 쓸 가능성이 있고, 키 관리는 분산되면 안 되므로 공용에 둔다.

```
org.happyzion.api.common.security.pii/
├── PiiEncryptionProperties.kt        (@ConfigurationProperties)
├── PiiEncryptor.kt                    (AES-256-GCM, v1: 포맷)
├── PiiHasher.kt                       (HMAC-SHA256)
├── MemberSearchKeyNormalizer.kt       (NFKC → 공백 제거 → 소문자 등)
├── EncryptedStringConverter.kt        (@Converter)
└── EncryptedLocalDateConverter.kt     (@Converter, ISO 문자열 변환 포함)
```

### 3.3 기존 모듈 변경

| 파일 | 변경 |
|---|---|
| `board/domain/PostAssetKind.kt` | enum에 `MEMBER_PHOTO` 추가 |
| `.env.example`, `.env.production.example` | `HAPPYZION_PII_ENCRYPTION_KEY`, `HAPPYZION_PII_HASH_KEY` 추가 |
| `application.yml` | PII 키 바인딩 |

## 4. DB 스키마 — V8 마이그레이션

파일: `src/main/resources/db/migration/V8__create_church_member_registry.sql`

### 4.1 `church_member` (인적사항)

```sql
create table church_member (
    id                       bigserial primary key,

    -- 암호화 (AES-256-GCM, base64 직렬화 텍스트)
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
    photo_path               varchar(255),
    cell_label               varchar(120),
    status                   varchar(32)  not null,
    faith_stage              varchar(32),
    office                   varchar(32)  not null default 'LAY',
    office_appointed_at      date,
    registered_at            date         not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),

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

```sql
create table church_member_audit_log (
    id                bigserial primary key,
    church_member_id  bigint not null references church_member(id) on delete cascade,
    actor_id          bigint not null references admin_account(id),
    action            varchar(20) not null,
    diff_enc          text,
    created_at        timestamptz not null default now(),
    constraint chk_church_member_audit_action check (action in ('CREATE','UPDATE','DELETE'))
);

create index idx_church_member_audit_member_id
    on church_member_audit_log(church_member_id, created_at desc, id desc);
```

### 4.4 기존 테이블 CHECK 제약 확장

```sql
alter table post_asset
    drop constraint chk_post_asset_kind;
alter table post_asset
    add constraint chk_post_asset_kind
    check (kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MEMBER_PHOTO'));

alter table upload_token
    drop constraint chk_upload_token_asset_kind;
alter table upload_token
    add constraint chk_upload_token_asset_kind
    check (asset_kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MEMBER_PHOTO'));
```

### 4.5 정규화 규칙 (검색 blind index)

`MemberSearchKeyNormalizer`가 저장과 검색 양쪽에서 동일한 함수로 호출되어야 한다.

- 이름(`forName`): NFKC 정규화 → 모든 공백 문자(`\\p{Z}\\s`) 제거 → 소문자 변환
- 전화 풀번호(`forPhone`): 숫자가 아닌 모든 문자 제거. 9자리 미만이면 도메인 위반(거부)
- 전화 끝 4자리(`last4`): `forPhone` 결과의 마지막 4자. 8자리 미만이면 `null`

## 5. 도메인 모델

### 5.1 엔티티 — 코드 스타일

엔티티 필드는 평문(`name: String`, `phone: String`, `birthDate: LocalDate`)로 노출하고, `@Column(name = "name_enc")` + `@Convert(converter = EncryptedStringConverter::class)`로 영속화 경계에서만 암호문으로 바뀐다. 도메인 코드는 평문만 본다.

해시 컬럼(`nameHash`, `phoneHash`, `phoneLast4Hash`)은 별도 필드로 두되, 변경 메서드(`rename`, `changePhone`)에서 원문 변경과 해시 갱신을 항상 한 단위로 처리한다. 즉 외부에서 해시만 따로 바꿀 방법이 없다 — "암호화 컬럼과 hash 컬럼의 동기화"가 도메인 불변식.

### 5.2 변경 메서드 시그니처 (예시)

```kotlin
fun rename(newName: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer)
fun changePhone(newPhone: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer)
fun changeAddress(newAddress: String, newDetail: String?)
fun changeStatus(newStatus: ChurchMemberStatus)
fun markRemoved()    // status=REMOVED 전용 경로
fun appointOffice(office: ChurchMemberOffice, at: LocalDate?)
// ...
```

도메인 객체가 정적 의존(싱글톤 빈)을 잡지 않기 위해 hasher·normalizer는 변경 메서드 인자로 전달.

### 5.3 `ChurchMemberFaith` 매핑

`@OneToOne(fetch = LAZY)` + `@MapsId`로 PK 공유. cascade 정책은 DB 한쪽(`on delete cascade`)에만 두고 JPA 측에는 두지 않는다 (이중 cascade 회피).

### 5.4 `ChurchMemberAuditLog`

append-only. 생성자에서 모든 필드 확정, setter 없음. `diff_enc`에는 `{ "field": [before, after] }` 형태의 JSON 직렬화 결과를 통째 암호화하여 저장.

## 6. API

### 6.1 엔드포인트

모두 `@AdminAuthRequired` (Bearer 토큰 필요).

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/v1/admin/members` | 목록 (검색·필터·페이지네이션) |
| POST | `/api/v1/admin/members` | 등록 (인적 + 선택적 신앙정보) |
| GET | `/api/v1/admin/members/{id}` | 상세 |
| PUT | `/api/v1/admin/members/{id}` | 전체 치환 수정 |
| DELETE | `/api/v1/admin/members/{id}` | soft delete (`status=REMOVED`) |
| POST | `/api/v1/admin/members/{id}/photo` | 사진 연결/교체 (`assetId`) |
| DELETE | `/api/v1/admin/members/{id}/photo` | 사진 제거 |
| GET | `/api/v1/admin/members/{id}/audit-logs` | 변경 이력 페이징 조회 |

사진 업로드 자체는 기존 `POST /api/v1/admin/uploads/token` → `POST /api/v1/admin/uploads`(`kind=MEMBER_PHOTO`)를 그대로 사용한다.

### 6.2 목록 쿼리 파라미터

```
GET /api/v1/admin/members
  ?name=김철수            (정확 일치, 정규화 후 name_hash 검색)
  &phone=01012345678      (풀번호 정확 일치, phone_hash)
  &phone=5678             (4자리, phone_last4_hash)
  &status=ACTIVE,NEW      (CSV, 다중 선택)
  &faithStage=DISCIPLE
  &cellLabel=1구역
  &includeInactive=false  (기본 false — REMOVED·DECEASED 숨김)
  &page=0&size=20
```

정렬은 `registered_at DESC, id DESC` 고정. 응답에 `hasNext` 플래그 (기존 board 모듈 패턴).

전화번호 파라미터 처리: 서비스가 입력 길이로 분기. 정규화 후 4자리면 `phone_last4_hash` 인덱스, 9자리 이상이면 `phone_hash` 인덱스, 그 외 길이는 빈 결과.

### 6.3 서비스 책임 분할

```
ChurchMemberAdminService
  - listMembers, getMember, listAuditLogs (read)
  - createMember, updateMember, softDeleteMember (write)
  - attachPhoto, detachPhoto

ChurchMemberAuditWriter
  - recordCreate(memberId, actorId, after: Snapshot)
  - recordUpdate(memberId, actorId, before: Snapshot, after: Snapshot)
  - recordDelete(memberId, actorId, before: Snapshot)
  내부에서 diff 계산 → JSON 직렬화 → PiiEncryptor 적용

ChurchMemberPhotoService
  - replacePhoto(member, assetId, actorId)
  - removePhoto(member, actorId)
  검증: post_asset.kind == MEMBER_PHOTO, detached_at IS NULL, 다른 멤버에 이미 연결되지 않음
  교체: 기존 photo_path에 해당하는 post_asset.detached_at 세팅 후 새 자산 연결
```

### 6.4 핵심 흐름: `updateMember`

```
1. Controller: actorId(@RequestAttribute), id, ChurchMemberSaveRequest 수신
2. service.updateMember(id, request.toCommand(), actorId)
   ├ memberRepo.findById(id) → 평문 도메인 객체 (자동 복호)
   ├ ChurchMemberSnapshot.of(member) → before (불변 사본)
   ├ 도메인 변경 메서드 호출 (rename / changePhone / ...)
   ├ ChurchMemberSnapshot.of(member) → after
   ├ auditWriter.recordUpdate(id, actorId, before, after)
   │   ├ JsonDiff.compute(before, after) → 변경된 필드만 포함
   │   ├ piiEncryptor.encrypt(jsonString)
   │   └ auditLogRepo.save(...)
   └ memberRepo.save(member)  ← @Convert가 자동 암호화
3. Controller가 도메인 객체를 응답 DTO(평문)로 변환
```

## 7. 에러 처리·검증·트랜잭션

### 7.1 에러 매핑 (기존 GlobalExceptionHandler 그대로 활용)

| 상황 | 예외 | HTTP | 코드 |
|---|---|---|---|
| Bearer 토큰 없음/만료 | `UnauthorizedException` (인터셉터) | 401 | UNAUTHORIZED |
| 존재하지 않는 멤버 id | `NotFoundException` | 404 | NOT_FOUND |
| 자산 검증 실패 (kind 불일치, detach됨) | `IllegalArgumentException` / `NotFoundException` | 400 / 404 | INVALID_REQUEST / NOT_FOUND |
| 필수 필드 누락 등 (`@Valid`) | `MethodArgumentNotValidException` | 400 | INVALID_REQUEST |
| 도메인 위반 (정규화 후 빈 이름 등) | `IllegalArgumentException` | 400 | INVALID_REQUEST |
| 미처리 | `Exception` 폴백 | 500 | INTERNAL_SERVER_ERROR |

### 7.2 검증 레이어

- **DTO**: Bean Validation 어노테이션(`@NotBlank`, `@Size`, `@Email`, `@NotNull`)으로 형식 검증
- **도메인 생성자/변경 메서드**: 정규화 후 빈 값 거부, 전화 9자리 미만 거부, `officeAppointedAt` 미래 거부, `status=REMOVED` 직접 지정 차단(반드시 `markRemoved()` 경로 통해서만)

전화번호·이메일 형식 정규식 강제는 적용하지 않는다(결정 #8).

### 7.3 트랜잭션 경계

- 모든 application 서비스 메서드에 `@Transactional` (read 메서드는 `readOnly = true`)
- 멤버 변경 + audit 기록 + 사진 정리는 단일 트랜잭션
- 낙관적 락(`@Version`) 미도입. last-write-wins, audit log로 사후 추적

## 8. 테스트 전략

### 8.1 단위 테스트 (서비스·도메인·정규화·암호화)

- `ChurchMemberTest` — 변경 메서드의 hash 동시 갱신, 도메인 invariant
- `ChurchMemberAdminServiceTest` — CRUD/검색/사진/감사 통합 동작 (mockito-kotlin)
- `ChurchMemberAuditWriterTest` — diff 계산 정확성, CREATE/UPDATE/DELETE별 기록
- `ChurchMemberPhotoServiceTest` — 자산 검증, 교체 시 기존 detach
- `MemberSearchKeyNormalizerTest` — 한·영·전각/반각·이모지·트레일링 공백
- `PiiEncryptorTest` — round-trip, IV 랜덤성, v1 포맷, tamper 검증, 잘못된 키 거부
- `PiiHasherTest` — 결정성, 정규화 흡수
- `EncryptedStringConverterTest`, `EncryptedLocalDateConverterTest` — null/empty 처리, round-trip

### 8.2 계약 테스트

- `ChurchMemberRepositoryContractTest` (또는 `MemberSchemaContractTest`):
  - V8 마이그레이션 적용 결과 검증 (`BoardSchemaContractTest` 패턴)
  - **raw SQL로 `name_enc`, `phone_enc`, `birth_date_enc`, `address_enc` 등 모든 _enc 컬럼이 평문을 포함하지 않음을 확인**
  - **raw SQL로 `church_member_audit_log.diff_enc`에 변경 전/후 평문이 들어가지 않음을 확인**
  - blind index 정확일치 검색 동작 (`name_hash`로 정규화된 입력 조회)
- `EnvironmentConfigContractTest` 확장: 새 환경변수 키 `HAPPYZION_PII_ENCRYPTION_KEY`, `HAPPYZION_PII_HASH_KEY` 추가 검증

### 8.3 컨트롤러 테스트 (MockMvc)

- `ChurchMemberAdminControllerTest` — `@AdminAuthRequired` 인증 통과, 인증 실패 401, 검증 실패 400, 404 케이스, 정상 경로 응답 스키마

### 8.4 OpenAPI 스펙

`./gradlew build`가 `OpenApiSpecGenerationTest`를 트리거하여 `build/openapi/openapi.yaml`을 자동 갱신. 신규 컨트롤러는 별도 작업 없이 반영됨.

## 9. 비범위

PRD §3 비범위와 동일. 추가로:

- 부분일치 이름 검색 (예: "김"으로 시작) — blind index 한계
- 생년월일 정렬·범위 검색 — `birth_date_enc` 암호화로 인해 불가
- 신앙정보 필드 단위 통계 (예: "세례일 입력된 교인 수") — SQL 단 불가, application 단 풀스캔 필요
- Hibernate Envers 등 자동 감사 — 명시적 캡처 채택
- 교적부 권한 롤 분리 (super-admin/일반 admin) — JWT 전환은 끝났으나 권한 세분화는 PRD §4 callout 그대로 별도 과제

## 10. Phase 2 (변경 없음)

PRD §9 그대로. 본 마이그레이션에서 만들지 않음.
- `education_program`
- `church_member_education_enrollment`

## 11. 운영 출시 전 체크리스트

본 설계 범위 밖이지만 실데이터 입력 전 운영 측에 권고:

1. `HAPPYZION_PII_ENCRYPTION_KEY`, `HAPPYZION_PII_HASH_KEY` 생성·안전 저장 (KMS 또는 Vault 권장)
2. PostgreSQL `pg_dump` 백업의 저장 위치가 암호화되어 있는지 확인
3. 교적부 권한 롤 분리 (PRD §4 callout)
4. nginx `/upload` 정적 경로에 대한 접근 통제 정책 검토 (사진 URL이 토큰이지만 공개 접근 가능)
