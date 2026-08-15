-- SpaceSale: timestamps monotónicos de servidor para el pull paginado.
-- Los timestamps de negocio conservan la fecha real del evento; estas columnas
-- registran cuándo Supabase recibió la fila y permiten no omitir datos offline.

begin;

alter table public.sale_items
    add column if not exists server_created_at timestamptz not null default now();

alter table public.credit_payments
    add column if not exists server_created_at timestamptz not null default now();

alter table public.stock_movements
    add column if not exists server_created_at timestamptz not null default now();

create index if not exists sale_items_business_server_cursor_idx
    on public.sale_items(business_id, server_created_at, id);

create index if not exists credit_payments_business_server_cursor_idx
    on public.credit_payments(business_id, server_created_at, id);

create index if not exists stock_movements_business_server_cursor_idx
    on public.stock_movements(business_id, server_created_at, id);

comment on column public.sale_items.server_created_at is
    'Cursor inmutable asignado por Supabase; no representa la fecha comercial.';
comment on column public.credit_payments.server_created_at is
    'Cursor inmutable asignado por Supabase; no representa la fecha comercial.';
comment on column public.stock_movements.server_created_at is
    'Cursor inmutable asignado por Supabase; no representa la fecha comercial.';

commit;
