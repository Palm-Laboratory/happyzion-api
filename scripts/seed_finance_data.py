"""
2025~2026년 교회 재정 테스트 데이터 생성 + API 자동 업로드
사용법: python3 scripts/seed_finance_data.py
요구사항: pip install requests openpyxl
서버: http://localhost:8080 (./gradlew bootRun 상태)
"""

import io
import math
import random
import sys
import requests
from openpyxl import load_workbook

# ── 설정 ──────────────────────────────────────────────────────────────────────
API_BASE   = "http://localhost:8080"
USERNAME   = "happyzion.admin"
PASSWORD   = "password-123"
TEMPLATE   = "/Users/eunchanmac/Downloads/시온재정.xlsx"  # 원본 양식
YEARS      = [2025, 2026]

# 월별 헌금 계수 (1.0 = 평균, 절기·명절 반영)
MONTH_FACTOR = {
    1: 1.10,   # 신년
    2: 0.90,   # 설날 연휴
    3: 1.05,
    4: 1.15,   # 부활절
    5: 1.00,   # 어버이날·스승의날
    6: 0.95,
    7: 0.85,   # 여름 방학
    8: 0.85,
    9: 1.10,   # 추석 감사헌금
    10: 1.05,
    11: 1.00,
    12: 1.20,  # 성탄절
}

# 수입 항목 기준 금액 (단위: 원)
BASE_INCOME = {
    "C5":  2_800_000,  # 십일조
    "C7":    700_000,  # 감사헌금
    "C8":  1_400_000,  # 주정헌금
    "C9":    250_000,  # 목장헌금
    "C10":         0,  # 절기감사 (절기 시 별도 처리)
    "C12":   350_000,  # 선교헌금
    "C13":   500_000,  # 건축헌금
    "C14":    80_000,  # 꽃헌금
    "C15":         0,  # 목적헌금
    "C17":   150_000,  # 행사찬조
}

# 지출 항목 기준 금액
BASE_EXPENSE = {
    "G5":          0,  # 목회자>십일조
    "G6":  2_300_000,  # 목회자>헌금(사례비)
    "G7":          0,  # 은급비
    "G8":    230_000,  # 연금
    "G9":     88_000,  # 실손보험
    "G10":   180_000,  # 은퇴비적립
    "G11":   270_000,  # 자녀교육비
    "G13":         0,  # 하늘보화
    "G14":   300_000,  # 교회선교
    "G15":         0,  # 성도선교
    "G16":   450_000,  # 해외선교
    "G18":   180_000,  # 해외선교적립
    "G20":    88_000,  # 세스코
    "G21":   120_000,  # 화재보험
    "G22":         0,  # 건물유지소모품비
    "G25":   350_000,  # 특별헌금>선교헌금
    "G26":         0,
    "G27":         0,
    "G28":         0,
    "G30":   130_000,  # 행사비>교회행사
    "G31":         0,
    "G32":         0,
    "G34":         0,  # 찬조헌금
    "G38":         0,  # 고정자산
    "G41":   200_000,  # 카드성물>농협
    "G42":         0,
    "G43":         0,
    "G51":   170_000,  # 주일식사비
    "G52":         0,
    "G54":    55_000,  # 전도활동비
    "G55":    90_000,  # 공과금
    "G57":         0,
    "G58":         0,  # 노회비 (분기별 처리)
    "G59":    45_000,  # 통신비
    "G60":         0,
    "G61":         0,
    "G63":         0,
    "G65":         0,
    "G67":         0,
    "G68":         0,
    "G69":         0,
}

# 절기 감사헌금 추가 (연/월/주 → 추가 금액)
SPECIAL_EVENTS = {
    (None, 1, 1):  ("C10", 800_000),   # 신년감사
    (None, 4, 2):  ("C10", 600_000),   # 부활절 (4월 2주)
    (None, 9, 3):  ("C10", 700_000),   # 추석 (9월 3주)
    (None, 12, 3): ("C10", 500_000),   # 성탄절 (12월 3주)
    (None, 3, 1):  ("G58", 300_000),   # 1분기 노회비
    (None, 6, 1):  ("G58", 300_000),   # 2분기 노회비
    (None, 9, 1):  ("G58", 300_000),   # 3분기 노회비
    (None, 12, 1): ("G58", 300_000),   # 4분기 노회비
}

ROUND_UNIT = 10_000  # 만원 단위로 반올림

def rounded(v: float) -> int:
    return int(round(v / ROUND_UNIT) * ROUND_UNIT)

def vary(base: int, pct: float = 0.15) -> int:
    """base 금액을 ±pct 범위에서 랜덤 변동"""
    if base == 0:
        return 0
    delta = base * pct
    return rounded(base + random.uniform(-delta, delta))

def week_amounts(year: int, month: int, week: int):
    """한 주의 수입·지출 셀 값 딕셔너리 반환"""
    factor = MONTH_FACTOR[month]
    rng = random.Random(year * 10000 + month * 100 + week)  # 재현 가능한 시드

    amounts = {}
    # 수입
    for cell, base in BASE_INCOME.items():
        amounts[cell] = int(vary(int(base * factor), 0.15) if base > 0 else 0)
        amounts[cell] = max(0, amounts[cell])

    # 지출 (고정 비용은 변동 적게)
    fixed = {"G6", "G8", "G9", "G10", "G11", "G20", "G21", "G55", "G59"}
    for cell, base in BASE_EXPENSE.items():
        if base == 0:
            amounts[cell] = 0
        elif cell in fixed:
            amounts[cell] = vary(base, 0.03)  # 고정비 ±3%
        else:
            amounts[cell] = vary(int(base * factor), 0.20)
        amounts[cell] = max(0, amounts[cell])

    # 절기 특별 처리 (연도 무관)
    for (y, m, w), (cell, add) in SPECIAL_EVENTS.items():
        if (y is None or y == year) and m == month and w == week:
            amounts[cell] = amounts.get(cell, 0) + add

    return amounts

def build_xlsx(year: int, month: int, week: int) -> bytes:
    wb = load_workbook(TEMPLATE)
    ws = wb.active
    ws["A1"] = f"{year}년 {month}월 {week}주"
    for cell, value in week_amounts(year, month, week).items():
        ws[cell] = value
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()

def login() -> str:
    r = requests.post(f"{API_BASE}/api/v1/admin/auth/login",
                      json={"username": USERNAME, "password": PASSWORD}, timeout=10)
    r.raise_for_status()
    token = r.json()["token"]
    print(f"✓ 로그인 성공")
    return token

def upload(token: str, year: int, month: int, week: int) -> bool:
    xlsx_bytes = build_xlsx(year, month, week)
    filename = f"시온재정_{year}년{month}월{week}주.xlsx"
    headers = {"Authorization": f"Bearer {token}"}
    files = {"file": (filename, xlsx_bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    data  = {"year": str(year), "month": str(month), "week": str(week)}
    r = requests.post(f"{API_BASE}/api/v1/admin/finance/reports",
                      headers=headers, files=files, data=data, timeout=30)
    if r.status_code in (200, 201):
        return True
    if r.status_code == 409:
        print(f"  ↳ 이미 존재 (skip)")
        return True
    print(f"  ✗ 실패 {r.status_code}: {r.text[:120]}")
    return False

def weeks_in_month(year: int, month: int) -> int:
    """한 달에 몇 주인지 (일요일 기준 최대 5주)"""
    import calendar
    first_day, days = calendar.monthrange(year, month)
    # 첫 일요일까지 남은 일수
    days_to_sunday = (6 - first_day) % 7
    sundays = 0
    d = days_to_sunday + 1
    while d <= days:
        sundays += 1
        d += 7
    return sundays

def main():
    try:
        token = login()
    except Exception as e:
        print(f"로그인 실패: {e}")
        sys.exit(1)

    total = success = 0
    for year in YEARS:
        for month in range(1, 13):
            w_count = weeks_in_month(year, month)
            for week in range(1, w_count + 1):
                total += 1
                label = f"{year}년 {month:02d}월 {week}주"
                sys.stdout.write(f"\r업로드 중: {label} ({total}번째)  ")
                sys.stdout.flush()
                ok = upload(token, year, month, week)
                if ok:
                    success += 1

    print(f"\n\n완료: {success}/{total}건 업로드 성공")

if __name__ == "__main__":
    main()
