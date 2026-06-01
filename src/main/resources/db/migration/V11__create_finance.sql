-- 교회 재정 관리 스키마
-- 기반 설계: docs/superpowers/specs/2026-05-28-church-finance-management-design.md

-- ── 1. 계정과목 트리 ────────────────────────────────────────────────────────
create table finance_category (
    id         bigserial primary key,
    direction  varchar(10)  not null,
    major      varchar(60)  not null,
    minor      varchar(60)  not null,
    sort_order int          not null default 0,
    active     boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint chk_finance_category_direction check (direction in ('INCOME', 'EXPENSE')),
    constraint uk_finance_category unique (direction, major, minor)
);

create trigger trg_finance_category_updated_at
    before update on finance_category
    for each row execute function set_current_timestamp_updated_at();

-- ── 2. 주간 보고서 ──────────────────────────────────────────────────────────
create table finance_report (
    id                  bigserial primary key,
    year                int          not null,
    month               int          not null,
    week                int          not null,
    income_total        bigint       not null default 0,
    expense_total       bigint       not null default 0,
    balance             bigint       not null default 0,
    source_filename     varchar(255) not null,
    uploaded_by         bigint       not null references admin_account(id),
    checksum_mismatch   boolean      not null default false,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    constraint uk_finance_report_period unique (year, month, week),
    constraint chk_finance_report_month check (month between 1 and 12),
    constraint chk_finance_report_week  check (week  between 1 and 5)
);

create index idx_finance_report_year_month on finance_report(year, month);

create trigger trg_finance_report_updated_at
    before update on finance_report
    for each row execute function set_current_timestamp_updated_at();

-- ── 3. 보고서 × 계정과목 금액 ───────────────────────────────────────────────
create table finance_report_line (
    id          bigserial primary key,
    report_id   bigint      not null references finance_report(id) on delete cascade,
    category_id bigint      not null references finance_category(id),
    amount      bigint      not null default 0,
    created_at  timestamptz not null default now(),
    constraint uk_finance_report_line unique (report_id, category_id)
);

create index idx_finance_report_line_report   on finance_report_line(report_id);
create index idx_finance_report_line_category on finance_report_line(category_id);

-- ── 4. 미집행 품목 ──────────────────────────────────────────────────────────
create table finance_unexecuted_item (
    id            bigserial primary key,
    report_id     bigint       not null references finance_report(id) on delete cascade,
    content       varchar(200) not null,
    amount        bigint       not null default 0,
    executed_date date,
    note          varchar(200),
    sort_order    int          not null default 0,
    created_at    timestamptz  not null default now()
);

create index idx_finance_unexecuted_item_report on finance_unexecuted_item(report_id);

-- ── 5. 계정과목 시드 (셀 맵 §4 기준, 50개 항목) ───────────────────────────
insert into finance_category (direction, major, minor, sort_order) values
    -- 수입 (10개)
    ('INCOME', '십일조',   '십일조',   1),
    ('INCOME', '헌금',     '감사헌금', 2),
    ('INCOME', '헌금',     '주정헌금', 3),
    ('INCOME', '헌금',     '목장헌금', 4),
    ('INCOME', '헌금',     '절기감사', 5),
    ('INCOME', '특별헌금', '선교헌금', 6),
    ('INCOME', '특별헌금', '건축헌금', 7),
    ('INCOME', '특별헌금', '꽃헌금',   8),
    ('INCOME', '특별헌금', '목적헌금', 9),
    ('INCOME', '찬조헌금', '행사찬조', 10),
    -- 지출 (40개)
    ('EXPENSE', '목회자',    '십일조',           11),
    ('EXPENSE', '목회자',    '헌금',             12),
    ('EXPENSE', '목회자',    '은급비',           13),
    ('EXPENSE', '목회자',    '연금',             14),
    ('EXPENSE', '목회자',    '실손보험',         15),
    ('EXPENSE', '목회자',    '은퇴비적립',       16),
    ('EXPENSE', '목회자',    '자녀교육비',       17),
    ('EXPENSE', '선교비',    '하늘보화',         18),
    ('EXPENSE', '선교비',    '교회선교',         19),
    ('EXPENSE', '선교비',    '성도선교',         20),
    ('EXPENSE', '선교비',    '해외선교',         21),
    ('EXPENSE', '선교비적립','해외선교적립',     22),
    ('EXPENSE', '교회유지',  '세스코',           23),
    ('EXPENSE', '교회유지',  '화재보험',         24),
    ('EXPENSE', '교회유지',  '건물유지소모품비', 25),
    ('EXPENSE', '특별헌금',  '선교헌금',         26),
    ('EXPENSE', '특별헌금',  '건축헌금',         27),
    ('EXPENSE', '특별헌금',  '꽃헌금',           28),
    ('EXPENSE', '특별헌금',  '목적헌금',         29),
    ('EXPENSE', '행사비',    '교회행사',         30),
    ('EXPENSE', '행사비',    '기도원',           31),
    ('EXPENSE', '행사비',    '기타',             32),
    ('EXPENSE', '찬조헌금',  '행사찬조',         33),
    ('EXPENSE', '고정자산',  '교회비품',         34),
    ('EXPENSE', '카드성물',  '농협',             35),
    ('EXPENSE', '카드성물',  '삼성',             36),
    ('EXPENSE', '카드성물',  '신한',             37),
    ('EXPENSE', '경비',      '주일식사비',       38),
    ('EXPENSE', '경비',      '주간부식비',       39),
    ('EXPENSE', '경비',      '전도활동비',       40),
    ('EXPENSE', '경비',      '공과금',           41),
    ('EXPENSE', '경비',      '세금',             42),
    ('EXPENSE', '경비',      '노회비',           43),
    ('EXPENSE', '경비',      '통신비',           44),
    ('EXPENSE', '경비',      '의료비',           45),
    ('EXPENSE', '경비',      '차량유지비',       46),
    ('EXPENSE', '경비',      '소모품비',         47),
    ('EXPENSE', '경비',      '사무용품비',       48),
    ('EXPENSE', '경비',      '선물비',           49),
    ('EXPENSE', '경비',      '심방비',           50),
    ('EXPENSE', '경비',      '경조비',           51);
