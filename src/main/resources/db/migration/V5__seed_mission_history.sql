insert into mission_year (year, caption, tone, sort_order) values
  ('2007',    '선교의 첫 발을 내딛다',            'gold', 0),
  ('2008',    '두 번째 여름, 같은 땅에서',          null,  1),
  ('2009',    '말레이시아로 지경을 넓히다',          'gold', 2),
  ('2010',    '두 땅에서 복음을 전하다',             null,  3),
  ('2011',    '아시아를 향한 꾸준한 발걸음',         null,  4),
  ('2012',    '흔들림 없이, 해마다',                null,  5),
  ('2013',    '캄보디아 첫 사역',                  'gold', 6),
  ('2014',    '중국까지, 새로운 사역의 문',          'gold', 7),
  ('2015',    '선교사역은 계속되고',                'gold', 8),
  ('2016',    '인도네시아로, 복음의 걸음 더하기',     'gold', 9),
  ('2017',    '미얀마까지, 사역을 넓히며',           'gold', 10),
  ('2018',    '다음 땅을 바라보며',                 'gold', 11),
  ('2019',    '다시 미얀마로',                     null,  12),
  ('2020-21', '잠시 멈춘 기간',                    'red',  13),
  ('2022',    '그리움 뒤 다시, 다시',               'gold', 14),
  ('2023',    '회복의 걸음을 이어가다',              null,  15),
  ('2024',    '몽골까지, 땅끝을 향해',              'gold', 16),
  ('2025',    '변함없이, 오늘도',                   null,  17),
  ('2026',    '선교는 계속됩니다',                  null,  18);

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '필리핀 팡가시난', true,  0 from mission_year y where y.year = '2007';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '필리핀 팡가시난', false, 0 from mission_year y where y.year = '2008';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '말레이시아', true,  0 from mission_year y where y.year = '2009';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '필리핀',    false, 1 from mission_year y where y.year = '2009';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '말레이시아', false, 0 from mission_year y where y.year = '2010';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '필리핀',    false, 1 from mission_year y where y.year = '2010';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀',    false, 0 from mission_year y where y.year = '2011';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '말레이시아', false, 1 from mission_year y where y.year = '2011';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀',    false, 0 from mission_year y where y.year = '2012';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '말레이시아', false, 1 from mission_year y where y.year = '2012';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '캄보디아',   true,  0 from mission_year y where y.year = '2013';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '필리핀 팡가시난', false, 1 from mission_year y where y.year = '2013';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀 팡가시난', false, 0 from mission_year y where y.year = '2014';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '말레이시아',      false, 1 from mission_year y where y.year = '2014';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '중국',           true,  2 from mission_year y where y.year = '2014';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀 팡가시난',  false, 0 from mission_year y where y.year = '2015';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '태국 칸차나부리',  false, 1 from mission_year y where y.year = '2015';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '캄보디아',        true,  2 from mission_year y where y.year = '2015';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '인도네시아', true,  0 from mission_year y where y.year = '2016';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '캄보디아',  false, 1 from mission_year y where y.year = '2016';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '중국',     false, 2 from mission_year y where y.year = '2016';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '미얀마',    true,  0 from mission_year y where y.year = '2017';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '인도네시아', false, 1 from mission_year y where y.year = '2017';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '중국',     false, 2 from mission_year y where y.year = '2017';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '인도네시아', false, 0 from mission_year y where y.year = '2018';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '미얀마',    false, 1 from mission_year y where y.year = '2018';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '파라과이',  true,  2 from mission_year y where y.year = '2018';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '인도네시아', false, 0 from mission_year y where y.year = '2019';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '미얀마',    false, 1 from mission_year y where y.year = '2019';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '필리핀',   false, 2 from mission_year y where y.year = '2019';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, null, '코로나19로 인해 제한된 선교 중단', false, 0 from mission_year y where y.year = '2020-21';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀',    false, 0 from mission_year y where y.year = '2022';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '인도네시아', false, 1 from mission_year y where y.year = '2022';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '필리핀',   false, 2 from mission_year y where y.year = '2022';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀',    false, 0 from mission_year y where y.year = '2023';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '인도네시아', false, 1 from mission_year y where y.year = '2023';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '파라과이',  false, 2 from mission_year y where y.year = '2023';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀', false, 0 from mission_year y where y.year = '2024';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '몽골',   true,  1 from mission_year y where y.year = '2024';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '태국',   false, 2 from mission_year y where y.year = '2024';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀',    false, 0 from mission_year y where y.year = '2025';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'May', '태국',     true,  1 from mission_year y where y.year = '2025';
insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Oct', '인도네시아', false, 2 from mission_year y where y.year = '2025';

insert into mission_entry (year_id, month, place, is_first, sort_order)
select y.id, 'Feb', '필리핀', false, 0 from mission_year y where y.year = '2026';
