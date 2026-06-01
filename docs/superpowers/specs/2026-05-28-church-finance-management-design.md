# 교회 재정 관리(Church Finance Management) 설계 — Phase 1

- 작성일: 2026-05-28
- 상태: 기획 확정 (프론트/백엔드 구현 계획 수립 직전)
- 기반 자료: `/Users/eunchanmac/Downloads/시온재정.xlsx` (교회 제공 주간 재정보고 양식)
- 검증 자료: `/Users/eunchanmac/Downloads/시온재정_예시작성.xlsx` (금액 기입 예시 — 셀 맵·합계·기간 파싱 검증 완료)
- 대상 서비스: `happyzion-api` (Kotlin + Spring Boot, PostgreSQL) / `happyzion-web` (Next.js App Router, CMS)

## 1. 개요

관리자 전용 **교회 재정 관리** 기능을 추가한다. 교회가 매주 작성하는 **주간 재정보고 엑셀 양식**을 업로드하면, 백엔드가 셀 데이터를 분석·분류해 정규화된 데이터로 저장하고, CMS에서 테이블·통계(주/월/분기/연)로 조회한다.

핵심 통찰: 교회가 제공한 엑셀은 **레이아웃이 고정된 2단(수입/지출) 주간 보고 양식**이다. 계정과목이 대분류→소분류 계층으로 고정되어 있고, 어느 셀에 어떤 항목 금액이 들어가는지 결정적으로 정해진다. 따라서 **고정 셀 맵(cell map)** 방식으로 신뢰도 높은 자동 파싱이 가능하다.

작업 순서: **1) 기획(본 문서) → 2) 프론트 → 3) 백엔드.**

## 2. 확정된 결정사항

| #   | 항목           | 결정                                                                                                                                                     |
| --- | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 데이터 단위    | **주간 보고서(report) 단위**. 보고서 1건 = 1주.                                                                                                          |
| 2   | 양식 가정      | **양식 고정 전제**. 교회에 "매주 이 파일을 복사해 숫자만 채우고, 행 추가·항목 변경 금지"를 안내한다. 항목 변경이 필요하면 사전 협의 → 셀 맵·시드 갱신.   |
| 3   | 파싱 방식      | **고정 셀 맵** (수입 10 + 지출 40 = 50개 항목).                                                                                                          |
| 4   | 기간(연/월/주) | `A1` 자유텍스트에서 정규식으로 자동 추출 → **업로드 미리보기 화면에 미리 채움 → 관리자 확인/수정 후 저장**. 추출 실패 시 수동 선택.                      |
| 5   | 합계 교차검증  | DB 저장값의 기준은 **백엔드가 개별 셀을 직접 합산한 값**. 추가로 양식의 합계셀(`C70`/`G70`)과 대조해 불일치 시 **경고 플래그**만 기록(저장은 정상 진행). |
| 6   | 미집행 품목    | 관리 대상 **포함**. 보고서별 하위 표(내용/금액/집행날짜/비고)로 저장.                                                                                    |
| 7   | 공개 범위      | **관리자 전용**. 사이트(공개) 페이지 없음.                                                                                                               |
| 8   | 접근 권한      | 일단 **모든 ADMIN 허용**(`@AdminAuthRequired`). 추후 `FINANCE`/`SUPER_ADMIN` 제한은 컨트롤러 가드 한 줄 변경으로 가능 — 데이터 모델은 그대로.            |
| 9   | 금액 단위      | 원(KRW) 정수. `bigint` 저장(소수점 없음).                                                                                                                |

## 3. 데이터 모델

PostgreSQL + Flyway 마이그레이션. 기존 스키마 관례(`bigserial`, `timestamptz`, `updated_at` 트리거, CHECK 제약)를 따른다. 신규 슬라이스 `org.happyzion.api.finance`.

### 3.1 `finance_category` — 계정과목 트리

```sql
create table finance_category (
    id bigserial primary key,
    direction varchar(10) not null,        -- INCOME / EXPENSE
    major varchar(60) not null,            -- 대분류
    minor varchar(60) not null,            -- 소분류
    sort_order int not null default 0,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_finance_category_direction check (direction in ('INCOME', 'EXPENSE')),
    constraint uk_finance_category unique (direction, major, minor)
);
```

→ §4의 셀 맵을 그대로 시드 데이터로 삽입한다.

### 3.2 `finance_report` — 주간 보고서

```sql
create table finance_report (
    id bigserial primary key,
    year int not null,
    month int not null,
    week int not null,
    income_total bigint not null default 0,
    expense_total bigint not null default 0,
    balance bigint not null default 0,          -- 남은헌금 = income_total - expense_total
    source_filename varchar(255) not null,
    uploaded_by bigint not null references admin_account(id),
    checksum_mismatch boolean not null default false,   -- 교차검증 경고 여부
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_finance_report_period unique (year, month, week),
    constraint chk_finance_report_month check (month between 1 and 12),
    constraint chk_finance_report_week check (week between 1 and 5)
);
```

- `(year, month, week)` UNIQUE → 같은 주 재업로드 시 덮어쓰기/경고 처리(§5.4).

### 3.3 `finance_report_line` — 보고서 × 계정과목 금액

```sql
create table finance_report_line (
    id bigserial primary key,
    report_id bigint not null references finance_report(id) on delete cascade,
    category_id bigint not null references finance_category(id),
    amount bigint not null default 0,
    created_at timestamptz not null default now(),
    constraint uk_finance_report_line unique (report_id, category_id)
);

create index idx_finance_report_line_category on finance_report_line(category_id);
```

### 3.4 `finance_unexecuted_item` — 미집행 품목

```sql
create table finance_unexecuted_item (
    id bigserial primary key,
    report_id bigint not null references finance_report(id) on delete cascade,
    content varchar(200) not null,
    amount bigint not null default 0,
    executed_date date,
    note varchar(200),
    sort_order int not null default 0,
    created_at timestamptz not null default now()
);

create index idx_finance_unexecuted_item_report on finance_unexecuted_item(report_id);
```

## 4. 셀 맵 (양식 고정 전제, 검증 완료)

`Sheet1` 기준. 수입은 C열, 지출은 G열에 소분류 금액이 들어간다. D열(`=SUM`)·H열(`=SUM`)은 양식이 계산하는 소계 수식이므로 **읽지 않는다**(교차검증 시에만 참조). 병합 셀은 병합 영역 좌상단 셀에서 값을 읽는다.

### 4.1 수입 (INCOME) — C열

| 대분류   | 소분류   | 금액셀 | 소계 수식(참고)  |
| -------- | -------- | ------ | ---------------- |
| 십일조   | 십일조   | C5     | D4=SUM(C5)       |
| 헌금     | 감사헌금 | C7     | D6=SUM(C7:C10)   |
| 헌금     | 주정헌금 | C8     |                  |
| 헌금     | 목장헌금 | C9     |                  |
| 헌금     | 절기감사 | C10    |                  |
| 특별헌금 | 선교헌금 | C12    | D11=SUM(C12:C15) |
| 특별헌금 | 건축헌금 | C13    |                  |
| 특별헌금 | 꽃헌금   | C14    |                  |
| 특별헌금 | 목적헌금 | C15    |                  |
| 찬조헌금 | 행사찬조 | C17    | D16=SUM(C17:C22) |

### 4.2 지출 (EXPENSE) — G열

| 대분류     | 소분류           | 금액셀 | 소계 수식(참고)  |
| ---------- | ---------------- | ------ | ---------------- |
| 목회자     | 십일조           | G5     | H4=SUM(G5:G11)   |
| 목회자     | 헌금             | G6     |                  |
| 목회자     | 은급비           | G7     |                  |
| 목회자     | 연금             | G8     |                  |
| 목회자     | 실손보험         | G9     |                  |
| 목회자     | 은퇴비적립       | G10    |                  |
| 목회자     | 자녀교육비       | G11    |                  |
| 선교비     | 하늘보화         | G13    | H12=SUM(G13:G16) |
| 선교비     | 교회선교         | G14    |                  |
| 선교비     | 성도선교         | G15    |                  |
| 선교비     | 해외선교         | G16    |                  |
| 선교비적립 | 해외선교적립     | G18    | H17=SUM(G18)     |
| 교회유지   | 세스코           | G20    | H19=SUM(G20:G23) |
| 교회유지   | 화재보험         | G21    |                  |
| 교회유지   | 건물유지소모품비 | G22    |                  |
| 특별헌금   | 선교헌금         | G25    | H24=SUM(G25:G28) |
| 특별헌금   | 건축헌금         | G26    |                  |
| 특별헌금   | 꽃헌금           | G27    |                  |
| 특별헌금   | 목적헌금         | G28    |                  |
| 행사비     | 교회행사         | G30    | H29=SUM(G30:G32) |
| 행사비     | 기도원           | G31    |                  |
| 행사비     | 기타             | G32    |                  |
| 찬조헌금   | 행사찬조         | G34    | H33=SUM(G34:G36) |
| 고정자산   | 교회비품         | G38    | H37=SUM(G38:G39) |
| 카드성물   | 농협             | G41    | H40=SUM(G41:G43) |
| 카드성물   | 삼성             | G42    |                  |
| 카드성물   | 신한             | G43    |                  |
| 경비       | 주일식사비       | G51    | H50=SUM(G51:G69) |
| 경비       | 주간부식비       | G52    |                  |
| 경비       | 전도활동비       | G54    |                  |
| 경비       | 공과금           | G55    |                  |
| 경비       | 세금             | G57    |                  |
| 경비       | 노회비           | G58    |                  |
| 경비       | 통신비           | G59    |                  |
| 경비       | 의료비           | G60    |                  |
| 경비       | 차량유지비       | G61    |                  |
| 경비       | 소모품비         | G63    |                  |
| 경비       | 사무용품비       | G65    |                  |
| 경비       | 선물비           | G67    |                  |
| 경비       | 심방비           | G68    |                  |
| 경비       | 경조비           | G69    |                  |

### 4.3 합계·미집행 영역

| 항목                 | 셀                               | 비고                                    |
| -------------------- | -------------------------------- | --------------------------------------- |
| 기간 헤더            | A1                               | 예: "2026년 5월 3주" (자유텍스트, §5.2) |
| 수입 합계(검증용)    | C70=SUM(C5:C63), D70=SUM(D4:D63) |                                         |
| 지출 합계(검증용)    | G70=SUM(G4:G69), H70=SUM(H4:H69) |                                         |
| 남은헌금(검증용)     | C71=C70-G70, D71=D70-H70         |                                         |
| 미집행 품목 데이터행 | 84, 86, 88, 90, 92, 94           | 내용=B / 금액=D / 집행날짜=G / 비고=I   |

## 5. 업로드 · 파싱 · 확인 플로우

### 5.1 전체 흐름

```
1. 관리자가 .xlsx 업로드
2. 백엔드 파싱:
   - A1 → 기간(연/월/주) 추출
   - 셀 맵 50개 → 계정과목별 금액
   - 미집행 품목 행 → 내용/금액/집행일/비고
   - 수입/지출 합계 직접 합산 + 남은헌금 계산
   - C70/G70와 대조 → checksum_mismatch 판정
3. 미리보기 응답 반환 (기간 미리 채움 + 파싱 결과 + 경고 여부)
4. 관리자가 기간 확인/수정 → 저장 요청
5. 저장: (year,month,week) upsert. 라인·미집행 항목 함께 저장.
```

### 5.2 기간 파싱 규칙

- 정규식: `(\d{4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*주`
- `A1`="2026년 5월 3주" → `{year:2026, month:5, week:3}` (검증 완료)
- 매칭 실패 시 미리보기에서 빈 값 → 관리자가 직접 선택(필수 입력).

### 5.3 합계 교차검증

- 기준값 = 셀 맵으로 직접 합산한 `income_total`/`expense_total`.
- 양식의 `C70`(수입)·`G70`(지출) 캐시값과 비교. 실제 엑셀로 저장된 파일은 수식 결과값을 함께 저장하므로 대조 가능.
- 불일치 시 `checksum_mismatch=true` 기록 + 미리보기에 경고 표시. **데이터는 기준값으로 정상 저장**(차단하지 않음).

### 5.4 재업로드(중복 주)

- `(year, month, week)` UNIQUE 충돌 시 미리보기에서 "이미 등록된 주입니다. 덮어쓸까요?" 확인 → 승인 시 기존 보고서 라인·미집행 항목 교체.

## 6. 통계 (주/월/분기/연)

- 단일 데이터원(`finance_report_line` ⨝ `finance_report`)을 기간으로 집계.
  - **주별**: 보고서 1건 그대로.
  - **월별**: `(year, month)` 합산.
  - **분기별**: 해당 분기 월들 합산.
  - **연별**: `year` 합산.
- 집계 축: 계정과목(소분류) 또는 대분류, 수입/지출 구분.
- 화면: 기간 토글 + 요약(수입/지출/잔액) + 차트(수입 추이, 지출 비중, 전기 대비). 차트 라이브러리는 프론트 단계에서 선정.

## 7. 화면 구성 (CMS)

`happyzion-web` 관리자 사이드바 **"교회 관리"** 그룹(교인 관리·SMS 관리 옆)에 "재정 관리" 추가. 라우트 `/admin/finance/*`.

1. **보고서 목록** — 등록된 주간 보고서 목록 + 기간 필터, 업로드 버튼.
2. **업로드/미리보기** — 파일 업로드 → 파싱 결과·기간 미리채움·경고 확인 → 저장.
3. **보고서 상세** — 한 주 보고서를 양식과 동일한 2단(수입/지출) 테이블 + 미집행 품목으로 표시.
4. **통계** — 주/월/분기/연 토글 + 차트 + 요약.
5. (후순위) 카테고리 관리, 예산 대비 실적, 엑셀/PDF 내보내기.

## 8. 백엔드 슬라이스 구조

기존 DDD 계층(`domain`/`application`/`infrastructure`/`interfaces`)을 따른다.

- `interfaces/api/FinanceAdminController` — `@AdminAuthRequired`, `/api/v1/admin/finance/**`
  - `POST /reports/preview` (multipart 업로드 → 파싱 미리보기)
  - `POST /reports` (확정 저장)
  - `GET /reports`, `GET /reports/{id}`, `DELETE /reports/{id}`
  - `GET /statistics?granularity=WEEK|MONTH|QUARTER|YEAR&...`
- `application/FinanceReportService`, `FinanceExcelParser`(셀 맵·기간 정규식·교차검증), `FinanceStatisticsService`
- `infrastructure/persistence` — JPA 리포지토리 4종
- 엑셀 파싱 라이브러리: **Apache POI** 신규 의존성 추가(현재 build.gradle.kts에 없음).
- `happyzion-web`: `src/app/api/admin/finance/**` 프록시 라우트(`adminApiFetch`) + `src/app/(admin)/admin/(cms)/finance/**` 화면.

## 9. 비범위 / 후속 과제

- 공개(사이트) 재정 요약 페이지
- 예산 편성 및 예산 대비 실적
- PDF 재정보고서 자동 생성, 엑셀 내보내기
- 계정과목 셀프서비스 편집 UI(현재는 시드/마이그레이션으로 관리)
- 거래(영수증) 증빙 첨부
- 권한 세분화(`FINANCE` 역할 도입)

## 10. 위험 / 전제

- **양식 고정이 핵심 전제.** 교회가 행을 끼워넣거나 항목을 바꾸면 셀 맵이 어긋난다. 교차검증(§5.3)이 1차 방어선이지만, 운영 안내 + 항목 변경 시 사전 협의 프로세스가 필요하다.
- 기간 헤더가 자유텍스트라 표기 흔들림(예: "5월 셋째주") 가능 → 추출 실패 시 수동 선택으로 흡수.
- 한 파일 = 한 주. 월/분기/연 통계는 여러 주 보고서가 누적되어야 의미가 있다.
