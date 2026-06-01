-- 합계 불일치 시 양식 합계셀 값을 저장해 차이 금액 표시에 활용
alter table finance_report
    add column form_income_total bigint,
    add column form_expense_total bigint;
