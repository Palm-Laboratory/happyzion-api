update board as b
set type = bt.key
from board_type as bt
where b.board_type_id = bt.id
  and b.type is distinct from bt.key;

alter table board
    drop column if exists board_type_id;

drop table if exists board_type;
