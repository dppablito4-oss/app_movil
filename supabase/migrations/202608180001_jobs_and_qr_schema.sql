-- SpaceSale: Esquema para Módulo de Trabajos, Servicios y Códigos QR Físicos.
-- Aplicar después de 202608160001_fix_profile_registration_rls.sql.
-- Compatible 100% con la versión actual de ventas e inventario.

begin;

-- ---------------------------------------------------------------------------
-- 1. EXTENSIÓN DE TABLAS EXISTENTES
-- ---------------------------------------------------------------------------

-- Permite vincular una venta generada a partir de un trabajo terminado
alter table public.sales
    add column if not exists job_id uuid;

-- Permite líneas de venta asociadas a servicios o trabajos sin requerir producto físico
alter table public.sale_items
    add column if not exists job_item_id uuid,
    add column if not exists item_type text not null default 'PRODUCT'
        check (item_type in ('PRODUCT', 'SERVICE', 'JOB'));

create index if not exists sales_job_id_idx
    on public.sales(job_id)
    where job_id is not null;

create index if not exists sale_items_job_item_id_idx
    on public.sale_items(job_item_id)
    where job_item_id is not null;

-- ---------------------------------------------------------------------------
-- 2. TABLA: qr_batches (Lotes de Generación e Impresión de QR)
-- ---------------------------------------------------------------------------

create table if not exists public.qr_batches (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    quantity integer not null check (quantity > 0),
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    constraint qr_batches_business_id_id_unique unique (business_id, id)
);

create index if not exists qr_batches_business_created_idx
    on public.qr_batches(business_id, created_at desc, id);

-- ---------------------------------------------------------------------------
-- 3. TABLA: jobs (Órdenes de Trabajo / Copiadora e Imprenta)
-- ---------------------------------------------------------------------------

create table if not exists public.jobs (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    customer_id uuid references public.customers(id) on delete set null,
    customer_name_snapshot text not null check (char_length(trim(customer_name_snapshot)) between 1 and 160),
    customer_phone_snapshot text,
    description text,
    status text not null check (status in ('received', 'in_progress', 'ready', 'delivered', 'cancelled')) default 'received',
    total_cents bigint not null default 0 check (total_cents >= 0),
    notes text not null default '',
    sale_id uuid references public.sales(id) on delete set null,
    ready_at timestamptz,
    delivered_at timestamptz,
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    server_created_at timestamptz not null default now(),
    constraint jobs_business_id_id_unique unique (business_id, id),
    constraint jobs_business_customer_fk
        foreign key (business_id, customer_id)
        references public.customers(business_id, id)
        on delete set null
);

create index if not exists jobs_business_status_idx
    on public.jobs(business_id, status, created_at desc);
create index if not exists jobs_business_updated_idx
    on public.jobs(business_id, updated_at, id);
create index if not exists jobs_business_server_cursor_idx
    on public.jobs(business_id, server_created_at, id);
create index if not exists jobs_customer_idx
    on public.jobs(customer_id, created_at desc)
    where customer_id is not null;
create index if not exists jobs_sale_id_idx
    on public.jobs(sale_id)
    where sale_id is not null;

-- ---------------------------------------------------------------------------
-- 4. TABLA: job_items (Desglose de Servicios / Bloques del Trabajo)
-- ---------------------------------------------------------------------------

create table if not exists public.job_items (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    job_id uuid not null references public.jobs(id) on delete cascade,
    service_type text not null default 'GENERAL',
    description text not null check (char_length(trim(description)) between 1 and 200),
    paper_size text,
    color_mode text,
    side_mode text,
    quantity integer not null default 1 check (quantity > 0),
    pages integer default 1 check (pages > 0),
    copies integer default 1 check (copies > 0),
    unit_price_cents bigint not null default 0 check (unit_price_cents >= 0),
    subtotal_cents bigint not null default 0 check (subtotal_cents >= 0),
    notes text not null default '',
    created_at timestamptz not null default now(),
    server_created_at timestamptz not null default now(),
    constraint job_items_business_job_fk
        foreign key (business_id, job_id)
        references public.jobs(business_id, id)
        on delete cascade
);

create index if not exists job_items_job_id_idx
    on public.job_items(job_id);
create index if not exists job_items_business_created_idx
    on public.job_items(business_id, created_at, id);
create index if not exists job_items_business_server_cursor_idx
    on public.job_items(business_id, server_created_at, id);

-- ---------------------------------------------------------------------------
-- 5. TABLA: qr_tokens (Etiquetas QR Físicas Preimpresas y Reutilizables)
-- ---------------------------------------------------------------------------

create table if not exists public.qr_tokens (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    token text not null unique check (char_length(token) between 6 and 16),
    status text not null check (status in ('unused', 'assigned', 'disabled')) default 'unused',
    job_id uuid references public.jobs(id) on delete set null,
    batch_id uuid references public.qr_batches(id) on delete set null,
    assigned_at timestamptz,
    released_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint qr_tokens_business_id_id_unique unique (business_id, id)
);

create index if not exists qr_tokens_business_status_idx
    on public.qr_tokens(business_id, status);
create index if not exists qr_tokens_token_idx
    on public.qr_tokens(token);
create index if not exists qr_tokens_job_id_idx
    on public.qr_tokens(job_id);
create index if not exists qr_tokens_business_updated_idx
    on public.qr_tokens(business_id, updated_at, id);

-- ---------------------------------------------------------------------------
-- 6. TRIGGERS DE ACTUALIZACIÓN DE TIMESTAMP
-- ---------------------------------------------------------------------------

create trigger jobs_set_updated_at
before update on public.jobs
for each row execute function public.set_updated_at();

create trigger qr_tokens_set_updated_at
before update on public.qr_tokens
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- 7. FUNCIONES PL/PGSQL: GENERACIÓN Y GESTIÓN DE TOKENS QR
-- ---------------------------------------------------------------------------

-- Generador de tokens aleatorios sin caracteres ambiguos (excluye 0, O, 1, I, L)
create or replace function public.generate_qr_token_code()
returns text
language plpgsql
as $$
declare
    chars text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    result text := '';
    i integer := 0;
    rand_index integer;
begin
    for i in 1..8 loop
        rand_index := floor(random() * length(chars) + 1)::integer;
        result := result || substr(chars, rand_index, 1);
    end loop;
    return result;
end;
$$;

-- Generación por lotes de etiquetas QR para un negocio
create or replace function public.batch_generate_qr_tokens(
    target_business_id uuid,
    p_count integer
)
returns setof public.qr_tokens
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_batch_id uuid := gen_random_uuid();
    v_inserted_count integer := 0;
    v_token text;
    v_rec public.qr_tokens;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'QR_BATCH_FORBIDDEN' using errcode = '42501';
    end if;

    if p_count is null or p_count <= 0 or p_count > 500 then
        raise exception 'QR_BATCH_COUNT_INVALID' using errcode = '22023';
    end if;

    insert into public.qr_batches (id, business_id, quantity, created_by)
    values (v_batch_id, target_business_id, p_count, auth.uid());

    while v_inserted_count < p_count loop
        v_token := public.generate_qr_token_code();
        begin
            insert into public.qr_tokens (
                id, business_id, token, status, job_id, batch_id, created_at, updated_at
            ) values (
                gen_random_uuid(), target_business_id, v_token, 'unused', null, v_batch_id, now(), now()
            )
            returning * into v_rec;

            v_inserted_count := v_inserted_count + 1;
            return next v_rec;
        exception when unique_violation then
            -- En caso de colisión extremadamente rara del token aleatorio, reintenta automáticamente
            null;
        end;
    end loop;
    return;
end;
$$;

-- Asignación de un token QR a una orden de trabajo
create or replace function public.assign_qr_token_to_job(
    target_business_id uuid,
    target_token text,
    target_job_id uuid
)
returns public.qr_tokens
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_token_rec public.qr_tokens%rowtype;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'QR_ASSIGN_FORBIDDEN' using errcode = '42501';
    end if;

    select * into v_token_rec
    from public.qr_tokens
    where business_id = target_business_id
      and token = upper(trim(target_token))
    for update;

    if not found then
        raise exception 'QR_TOKEN_NOT_FOUND' using errcode = 'P0002';
    end if;

    if v_token_rec.status = 'disabled' then
        raise exception 'QR_TOKEN_DISABLED' using errcode = '22023';
    end if;

    -- Actualiza el token a 'assigned'
    update public.qr_tokens
    set status = 'assigned',
        job_id = target_job_id,
        assigned_at = now(),
        updated_at = now()
    where id = v_token_rec.id
    returning * into v_token_rec;

    return v_token_rec;
end;
$$;

-- Liberación de un token QR (reutilización de etiqueta física)
create or replace function public.release_qr_token(
    target_business_id uuid,
    target_token text
)
returns public.qr_tokens
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_token_rec public.qr_tokens%rowtype;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'QR_RELEASE_FORBIDDEN' using errcode = '42501';
    end if;

    select * into v_token_rec
    from public.qr_tokens
    where business_id = target_business_id
      and token = upper(trim(target_token))
    for update;

    if not found then
        raise exception 'QR_TOKEN_NOT_FOUND' using errcode = 'P0002';
    end if;

    update public.qr_tokens
    set status = 'unused',
        job_id = null,
        released_at = now(),
        updated_at = now()
    where id = v_token_rec.id
    returning * into v_token_rec;

    return v_token_rec;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. ACTUALIZACIÓN DE RPC: confirm_sale_bundle (VENTAS MIXTAS PRODUCTO + SERVICIO)
-- ---------------------------------------------------------------------------

create or replace function public.confirm_sale_bundle(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    sale_payload jsonb := payload -> 'sale';
    item_payload jsonb;
    movement_payload jsonb;
    target_sale_id uuid := (sale_payload ->> 'id')::uuid;
    target_business_id uuid := (sale_payload ->> 'business_id')::uuid;
    target_status text := sale_payload ->> 'status';
    target_job_id uuid := nullif(sale_payload ->> 'job_id', '')::uuid;
    existing_sale public.sales%rowtype;
    expected_total bigint;
    affected_count integer;
    result_stocks jsonb;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'SALE_FORBIDDEN' using errcode = '42501';
    end if;

    if target_status not in ('PENDIENTE', 'CERRADO', 'ANULADO') then
        raise exception 'SALE_STATUS_INVALID' using errcode = '22023';
    end if;

    select * into existing_sale
    from public.sales
    where id = target_sale_id
    for update;

    if found then
        if existing_sale.business_id <> target_business_id
           or existing_sale.total_cents <> (sale_payload ->> 'total_cents')::bigint
           or existing_sale.payment_method <> sale_payload ->> 'payment_method'
           or existing_sale.sold_at <> (sale_payload ->> 'sold_at')::timestamptz then
            raise exception 'SALE_IDEMPOTENCY_CONFLICT' using errcode = '23505';
        end if;

        if target_status = 'ANULADO' and existing_sale.status <> 'ANULADO' then
            return public.cancel_sale_bundle(
                jsonb_build_object(
                    'sale_id', target_sale_id,
                    'business_id', target_business_id,
                    'reason', 'Anulada desde SpaceSale',
                    'movements', coalesce((
                        select jsonb_agg(value)
                        from jsonb_array_elements(coalesce(payload -> 'movements', '[]'::jsonb)) movement(value)
                        where value ->> 'type' = 'SALE_CANCEL'
                    ), '[]'::jsonb)
                )
            );
        elsif existing_sale.status = 'PENDIENTE' and target_status = 'CERRADO' then
            update public.sales
            set status = 'CERRADO',
                paid_at = coalesce(nullif(sale_payload ->> 'paid_at', '')::timestamptz, now()),
                updated_at = now()
            where id = target_sale_id and business_id = target_business_id;
        end if;

        select coalesce(jsonb_agg(jsonb_build_object(
            'product_id', product.id,
            'stock', product.stock
        ) order by product.id), '[]'::jsonb)
        into result_stocks
        from public.products product
        where product.business_id = target_business_id
          and product.id in (
              select distinct saved_item.product_id
              from public.sale_items saved_item
              where saved_item.business_id = target_business_id
                and saved_item.sale_id = target_sale_id
                and saved_item.product_id is not null
          );

        return jsonb_build_object('sale_id', target_sale_id, 'stocks', result_stocks);
    end if;

    if jsonb_typeof(coalesce(payload -> 'items', 'null'::jsonb)) <> 'array'
       or jsonb_array_length(payload -> 'items') = 0
       or jsonb_typeof(coalesce(payload -> 'movements', 'null'::jsonb)) <> 'array' then
        raise exception 'SALE_LINES_REQUIRED' using errcode = '22023';
    end if;

    if nullif(sale_payload ->> 'customer_id', '') is not null and not exists (
        select 1 from public.customers customer
        where customer.id = (sale_payload ->> 'customer_id')::uuid
          and customer.business_id = target_business_id
          and customer.deleted_at is null
    ) then
        raise exception 'SALE_CUSTOMER_INVALID' using errcode = '23503';
    end if;

    if exists (
        select 1
        from jsonb_array_elements(payload -> 'items') item(value)
        where (value ->> 'business_id')::uuid <> target_business_id
           or (value ->> 'sale_id')::uuid <> target_sale_id
           or (value ->> 'quantity')::integer <= 0
           or (value ->> 'unit_price_cents')::bigint < 0
           or (value ->> 'unit_cost_cents')::bigint < 0
           or nullif(trim(value ->> 'product_name_snapshot'), '') is null
    ) then
        raise exception 'SALE_LINE_INVALID' using errcode = '22023';
    end if;

    select coalesce(sum(
        (value ->> 'quantity')::bigint * (value ->> 'unit_price_cents')::bigint
    ), 0)
    into expected_total
    from jsonb_array_elements(payload -> 'items') item(value);

    if expected_total <= 0 or expected_total <> (sale_payload ->> 'total_cents')::bigint then
        raise exception 'SALE_TOTAL_MISMATCH' using errcode = '22023';
    end if;

    -- Validar movimientos de stock solo para items que tengan product_id (mercadería con inventario)
    if exists (
        select 1
        from (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity')::integer) quantity
            from jsonb_array_elements(payload -> 'items') item(value)
            where nullif(value ->> 'product_id', '') is not null
            group by (value ->> 'product_id')::uuid
        ) lines
        full join (
            select (value ->> 'product_id')::uuid product_id,
                   -(sum((value ->> 'quantity_delta')::integer)
                       filter (where value ->> 'type' = 'SALE')) quantity
            from jsonb_array_elements(payload -> 'movements') movement(value)
            where nullif(value ->> 'product_id', '') is not null
            group by (value ->> 'product_id')::uuid
        ) sale_moves using (product_id)
        where lines.quantity is distinct from sale_moves.quantity
    ) then
        raise exception 'SALE_MOVEMENT_QUANTITY_MISMATCH' using errcode = '22023';
    end if;

    -- Bloqueo y verificación de stock para productos físicos
    perform product.id
    from public.products product
    join (
        select (value ->> 'product_id')::uuid product_id,
               sum((value ->> 'quantity')::integer) quantity
        from jsonb_array_elements(payload -> 'items') item(value)
        where nullif(value ->> 'product_id', '') is not null
        group by (value ->> 'product_id')::uuid
    ) requested on requested.product_id = product.id
    where product.business_id = target_business_id
      and product.deleted_at is null
    order by product.id
    for update of product;

    select count(*) into affected_count
    from (
        select distinct (value ->> 'product_id')::uuid product_id
        from jsonb_array_elements(payload -> 'items') item(value)
        where nullif(value ->> 'product_id', '') is not null
    ) requested
    join public.products product on product.id = requested.product_id
    where product.business_id = target_business_id and product.deleted_at is null;

    if affected_count <> (
        select count(distinct (value ->> 'product_id')::uuid)
        from jsonb_array_elements(payload -> 'items') item(value)
        where nullif(value ->> 'product_id', '') is not null
    ) then
        raise exception 'SALE_PRODUCT_INVALID' using errcode = '23503';
    end if;

    if target_status <> 'ANULADO' and exists (
        select 1
        from public.products product
        join (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity')::integer) quantity
            from jsonb_array_elements(payload -> 'items') item(value)
            where nullif(value ->> 'product_id', '') is not null
            group by (value ->> 'product_id')::uuid
        ) requested on requested.product_id = product.id
        where product.business_id = target_business_id
          and product.stock < requested.quantity
    ) then
        raise exception 'STOCK_INSUFFICIENT' using errcode = 'P0001';
    end if;

    -- Inserción de la venta
    insert into public.sales (
        id, business_id, customer_id, job_id, total_cents, payment_method,
        status, sold_at, evidence_path, paid_at, cancellation_reason, cancelled_at
    ) values (
        target_sale_id,
        target_business_id,
        nullif(sale_payload ->> 'customer_id', '')::uuid,
        target_job_id,
        (sale_payload ->> 'total_cents')::bigint,
        sale_payload ->> 'payment_method',
        target_status,
        (sale_payload ->> 'sold_at')::timestamptz,
        nullif(sale_payload ->> 'evidence_path', ''),
        nullif(sale_payload ->> 'paid_at', '')::timestamptz,
        case when target_status = 'ANULADO' then 'Anulada antes de sincronizar' end,
        case when target_status = 'ANULADO' then now() end
    );

    -- Si se vinculó a un trabajo, marcar el trabajo como entregado y enlazar la venta
    if target_job_id is not null then
        update public.jobs
        set sale_id = target_sale_id,
            status = 'delivered',
            delivered_at = coalesce(delivered_at, now()),
            updated_at = now()
        where id = target_job_id and business_id = target_business_id;
    end if;

    -- Inserción de líneas de venta (productos y servicios)
    for item_payload in
        select value from jsonb_array_elements(payload -> 'items') item(value)
    loop
        insert into public.sale_items (
            id, business_id, sale_id, product_id, job_item_id, item_type,
            product_name_snapshot, quantity, unit_price_cents, unit_cost_cents, created_at
        ) values (
            (item_payload ->> 'id')::uuid,
            target_business_id,
            target_sale_id,
            nullif(item_payload ->> 'product_id', '')::uuid,
            nullif(item_payload ->> 'job_item_id', '')::uuid,
            coalesce(nullif(item_payload ->> 'item_type', ''), case when nullif(item_payload ->> 'product_id', '') is null then 'SERVICE' else 'PRODUCT' end),
            item_payload ->> 'product_name_snapshot',
            (item_payload ->> 'quantity')::integer,
            (item_payload ->> 'unit_price_cents')::bigint,
            (item_payload ->> 'unit_cost_cents')::bigint,
            coalesce(nullif(item_payload ->> 'created_at', '')::timestamptz, now())
        );
    end loop;

    -- Inserción de movimientos de stock
    for movement_payload in
        select value from jsonb_array_elements(payload -> 'movements') movement(value)
    loop
        if exists (select 1 from public.stock_movements where id = (movement_payload ->> 'id')::uuid) then
            raise exception 'SALE_MOVEMENT_ID_CONFLICT' using errcode = '23505';
        end if;
        insert into public.stock_movements (
            id, business_id, product_id, sale_id, type,
            quantity_delta, notes, created_at
        ) values (
            (movement_payload ->> 'id')::uuid,
            target_business_id,
            (movement_payload ->> 'product_id')::uuid,
            target_sale_id,
            movement_payload ->> 'type',
            (movement_payload ->> 'quantity_delta')::integer,
            left(coalesce(movement_payload ->> 'notes', ''), 240),
            coalesce(nullif(movement_payload ->> 'created_at', '')::timestamptz, now())
        );
    end loop;

    -- Descuento de stock físico
    if target_status <> 'ANULADO' then
        update public.products product
        set stock = product.stock - requested.quantity,
            updated_at = now()
        from (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity')::integer) quantity
            from jsonb_array_elements(payload -> 'items') item(value)
            where nullif(value ->> 'product_id', '') is not null
            group by (value ->> 'product_id')::uuid
        ) requested
        where product.id = requested.product_id
          and product.business_id = target_business_id;
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'product_id', product.id,
        'stock', product.stock
    ) order by product.id), '[]'::jsonb)
    into result_stocks
    from public.products product
    where product.business_id = target_business_id
      and product.id in (
          select distinct (value ->> 'product_id')::uuid
          from jsonb_array_elements(payload -> 'items') item(value)
          where nullif(value ->> 'product_id', '') is not null
      );

    return jsonb_build_object('sale_id', target_sale_id, 'stocks', result_stocks);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. ROW LEVEL SECURITY (RLS)
-- ---------------------------------------------------------------------------

alter table public.qr_batches enable row level security;
alter table public.jobs enable row level security;
alter table public.job_items enable row level security;
alter table public.qr_tokens enable row level security;

-- Políticas de miembros de negocio (staff / admin / owner)
drop policy if exists qr_batches_select_member on public.qr_batches;
drop policy if exists qr_batches_insert_writer on public.qr_batches;
create policy qr_batches_select_member on public.qr_batches for select to authenticated
using (private.is_business_member(business_id));
create policy qr_batches_insert_writer on public.qr_batches for insert to authenticated
with check (private.can_write_business(business_id));

drop policy if exists jobs_select_member on public.jobs;
drop policy if exists jobs_insert_writer on public.jobs;
drop policy if exists jobs_update_writer on public.jobs;
create policy jobs_select_member on public.jobs for select to authenticated
using (private.is_business_member(business_id));
create policy jobs_insert_writer on public.jobs for insert to authenticated
with check (private.can_write_business(business_id));
create policy jobs_update_writer on public.jobs for update to authenticated
using (private.can_write_business(business_id))
with check (private.can_write_business(business_id));

drop policy if exists job_items_select_member on public.job_items;
drop policy if exists job_items_insert_writer on public.job_items;
drop policy if exists job_items_update_writer on public.job_items;
create policy job_items_select_member on public.job_items for select to authenticated
using (private.is_business_member(business_id));
create policy job_items_insert_writer on public.job_items for insert to authenticated
with check (private.can_write_business(business_id));
create policy job_items_update_writer on public.job_items for update to authenticated
using (private.can_write_business(business_id))
with check (private.can_write_business(business_id));

drop policy if exists qr_tokens_select_member on public.qr_tokens;
drop policy if exists qr_tokens_insert_writer on public.qr_tokens;
drop policy if exists qr_tokens_update_writer on public.qr_tokens;
create policy qr_tokens_select_member on public.qr_tokens for select to authenticated
using (private.is_business_member(business_id));
create policy qr_tokens_insert_writer on public.qr_tokens for insert to authenticated
with check (private.can_write_business(business_id));
create policy qr_tokens_update_writer on public.qr_tokens for update to authenticated
using (private.can_write_business(business_id))
with check (private.can_write_business(business_id));

-- Políticas de lectura pública por Token para seguimiento Web por clientes (/t/:token)
drop policy if exists qr_tokens_public_read on public.qr_tokens;
create policy qr_tokens_public_read on public.qr_tokens for select to anon
using (true);

drop policy if exists jobs_public_read on public.jobs;
create policy jobs_public_read on public.jobs for select to anon
using (id in (select job_id from public.qr_tokens where status = 'assigned'));

drop policy if exists job_items_public_read on public.job_items;
create policy job_items_public_read on public.job_items for select to anon
using (job_id in (select job_id from public.qr_tokens where status = 'assigned'));

-- Permisos
grant select, insert, update on public.qr_batches to authenticated;
grant select, insert, update on public.jobs to authenticated;
grant select, insert, update on public.job_items to authenticated;
grant select, insert, update on public.qr_tokens to authenticated;

grant select on public.qr_tokens to anon;
grant select on public.jobs to anon;
grant select on public.job_items to anon;

grant execute on function public.generate_qr_token_code() to authenticated;
grant execute on function public.batch_generate_qr_tokens(uuid, integer) to authenticated;
grant execute on function public.assign_qr_token_to_job(uuid, text, uuid) to authenticated;
grant execute on function public.release_qr_token(uuid, text) to authenticated;

commit;
