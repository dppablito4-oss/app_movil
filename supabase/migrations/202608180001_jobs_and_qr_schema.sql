-- SpaceSale: Esquema Refinado para Módulos de Trabajos, Servicios y Códigos QR Físicos.
-- Aplicar después de 202608160001_fix_profile_registration_rls.sql.
-- Se eliminan políticas anon directas y dependencias circulares (jobs.sale_id).
-- Se agregan RPCs transaccionales con bloqueo FOR UPDATE y función de consulta pública get_public_job_by_token.

begin;

-- ---------------------------------------------------------------------------
-- 1. EXTENSIÓN DE TABLAS EXISTENTES (SIN RELACIÓN CIRCULAR)
-- ---------------------------------------------------------------------------

-- Permite vincular una venta a un trabajo (relación unidireccional: sales.job_id -> jobs.id)
alter table public.sales
    add column if not exists job_id uuid;

-- Permite líneas de venta asociadas a servicios o ítems de trabajo sin requerir producto físico
alter table public.sale_items
    add column if not exists job_item_id uuid,
    add column if not exists item_type text not null default 'PRODUCT';

-- Ajustar restricción check de item_type a solo PRODUCT y SERVICE
alter table public.sale_items
    drop constraint if exists sale_items_item_type_check;

alter table public.sale_items
    add constraint sale_items_item_type_check
    check (item_type in ('PRODUCT', 'SERVICE'));

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
    ready_at timestamptz,
    delivered_at timestamptz,
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    server_created_at timestamptz not null default now(),
    constraint jobs_business_id_id_unique unique (business_id, id)
);

-- Si la columna obsoleta sale_id existía de intentos anteriores, la eliminamos para evitar relación circular
alter table public.jobs drop column if exists sale_id;

-- Constraint de FK entre sales.job_id y jobs(id)
alter table public.sales
    drop constraint if exists sales_business_job_fk,
    drop constraint if exists sales_job_fk;

alter table public.sales
    add constraint sales_job_fk
        foreign key (job_id)
        references public.jobs(id)
        on delete set null;

create index if not exists jobs_business_status_idx
    on public.jobs(business_id, status, created_at desc);
create index if not exists jobs_business_updated_idx
    on public.jobs(business_id, updated_at, id);
create index if not exists jobs_business_server_cursor_idx
    on public.jobs(business_id, server_created_at, id);
create index if not exists jobs_customer_idx
    on public.jobs(business_id, customer_id);

-- ---------------------------------------------------------------------------
-- 4. TABLA: job_items (Detalle de Servicios de Trabajo)
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
    constraint job_items_business_id_id_unique unique (business_id, id)
);

create index if not exists job_items_job_id_idx
    on public.job_items(business_id, job_id);

-- ---------------------------------------------------------------------------
-- 5. TABLA: qr_tokens (Códigos QR Físicos Preimpresos)
-- ---------------------------------------------------------------------------

create table if not exists public.qr_tokens (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    token text not null check (char_length(token) between 6 and 16),
    status text not null check (status in ('unused', 'assigned', 'disabled')) default 'unused',
    job_id uuid references public.jobs(id) on delete set null,
    batch_id uuid references public.qr_batches(id) on delete set null,
    assigned_at timestamptz,
    released_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint qr_tokens_business_id_id_unique unique (business_id, id),
    constraint qr_tokens_business_token_unique unique (business_id, token)
);

create index if not exists qr_tokens_business_status_idx
    on public.qr_tokens(business_id, status);
create index if not exists qr_tokens_token_idx
    on public.qr_tokens(business_id, token);
create index if not exists qr_tokens_job_id_idx
    on public.qr_tokens(business_id, job_id)
    where job_id is not null;

-- Triggers de actualización de updated_at
drop trigger if exists jobs_set_updated_at on public.jobs;
create trigger jobs_set_updated_at
before update on public.jobs
for each row execute function public.set_updated_at();

drop trigger if exists qr_tokens_set_updated_at on public.qr_tokens;
create trigger qr_tokens_set_updated_at
before update on public.qr_tokens
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- 6. FUNCIONES RPC TRANSACCIONALES PARA GESTIÓN DE TRABAJOS Y QR
-- ---------------------------------------------------------------------------

-- Generador de códigos aleatorios únicos (8 caracteres sin confusos O,0,I,1,L)
create or replace function public.generate_qr_token_code()
returns text
language plpgsql
as $$
declare
    chars text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    result text := '';
    i integer;
begin
    for i in 1..8 loop
        result := result || substr(chars, floor(random() * length(chars) + 1)::integer, 1);
    end loop;
    return result;
end;
$$;

-- RPC: Generación Masiva de Tokens QR en Lotes
create or replace function public.batch_generate_qr_tokens(
    target_business_id uuid,
    p_quantity integer
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_batch_id uuid := gen_random_uuid();
    v_created_tokens text[] := '{}';
    v_token text;
    v_attempts integer;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'INSUFFICIENT_PERMISSIONS' using errcode = '42501';
    end if;

    if p_quantity is null or p_quantity <= 0 or p_quantity > 500 then
        raise exception 'INVALID_BATCH_QUANTITY' using errcode = '22023';
    end if;

    insert into public.qr_batches (id, business_id, quantity, created_by)
    values (v_batch_id, target_business_id, p_quantity, auth.uid());

    for i in 1..p_quantity loop
        v_attempts := 0;
        loop
            v_token := public.generate_qr_token_code();
            v_attempts := v_attempts + 1;
            
            begin
                insert into public.qr_tokens (
                    business_id, token, status, batch_id
                ) values (
                    target_business_id, v_token, 'unused', v_batch_id
                );
                v_created_tokens := array_append(v_created_tokens, v_token);
                exit; -- insert exitoso
            exception when unique_violation then
                if v_attempts > 10 then
                    raise exception 'TOKEN_GENERATION_COLLISION_RETRY_LIMIT' using errcode = '54000';
                end if;
            end;
        end loop;
    end loop;

    return jsonb_build_object(
        'batch_id', v_batch_id,
        'quantity', p_quantity,
        'tokens', to_jsonb(v_created_tokens)
    );
end;
$$;

-- RPC: Creación Atómica/Idempotente de Trabajo Completo con sus Ítems y opcional vinculación de QR
create or replace function public.create_job_bundle(
    target_business_id uuid,
    payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_job_id uuid;
    v_customer_id uuid;
    v_token_code text;
    v_token_rec record;
    item_elem jsonb;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'INSUFFICIENT_PERMISSIONS' using errcode = '42501';
    end if;

    v_job_id := coalesce(nullif(payload ->> 'id', '')::uuid, gen_random_uuid());
    v_customer_id := nullif(payload ->> 'customer_id', '')::uuid;
    v_token_code := nullif(trim(payload ->> 'token'), '');

    -- Verificar cliente pertenezca al mismo negocio si fue especificado
    if v_customer_id is not null then
        if not exists (
            select 1 from public.customers
            where business_id = target_business_id and id = v_customer_id and deleted_at is null
        ) then
            raise exception 'CUSTOMER_NOT_FOUND_OR_BUSINESS_MISMATCH' using errcode = '23503';
        end if;
    end if;

    -- Si ya existe el trabajo (idempotencia)
    if exists (select 1 from public.jobs where business_id = target_business_id and id = v_job_id) then
        return jsonb_build_object('job_id', v_job_id, 'status', 'already_exists');
    end if;

    -- Insertar Cabecera de Trabajo
    insert into public.jobs (
        id, business_id, customer_id, customer_name_snapshot, customer_phone_snapshot,
        description, status, total_cents, notes, ready_at, delivered_at, created_by, created_at
    ) values (
        v_job_id,
        target_business_id,
        v_customer_id,
        payload ->> 'customer_name_snapshot',
        payload ->> 'customer_phone_snapshot',
        payload ->> 'description',
        coalesce(nullif(payload ->> 'status', ''), 'received'),
        coalesce((payload ->> 'total_cents')::bigint, 0),
        coalesce(payload ->> 'notes', ''),
        nullif(payload ->> 'ready_at', '')::timestamptz,
        nullif(payload ->> 'delivered_at', '')::timestamptz,
        auth.uid(),
        coalesce(nullif(payload ->> 'created_at', '')::timestamptz, now())
    );

    -- Insertar Ítems
    for item_elem in select value from jsonb_array_elements(payload -> 'items') loop
        insert into public.job_items (
            id, business_id, job_id, service_type, description,
            paper_size, color_mode, side_mode, quantity, pages, copies,
            unit_price_cents, subtotal_cents, notes, created_at
        ) values (
            coalesce(nullif(item_elem ->> 'id', '')::uuid, gen_random_uuid()),
            target_business_id,
            v_job_id,
            coalesce(nullif(item_elem ->> 'service_type', ''), 'GENERAL'),
            item_elem ->> 'description',
            item_elem ->> 'paper_size',
            item_elem ->> 'color_mode',
            item_elem ->> 'side_mode',
            coalesce((item_elem ->> 'quantity')::integer, 1),
            coalesce((item_elem ->> 'pages')::integer, 1),
            coalesce((item_elem ->> 'copies')::integer, 1),
            coalesce((item_elem ->> 'unit_price_cents')::bigint, 0),
            coalesce((item_elem ->> 'subtotal_cents')::bigint, 0),
            coalesce(item_elem ->> 'notes', ''),
            coalesce(nullif(item_elem ->> 'created_at', '')::timestamptz, now())
        );
    end loop;

    -- Vincular Token QR si se proporcionó en la creación
    if v_token_code is not null then
        select * into v_token_rec
        from public.qr_tokens
        where business_id = target_business_id and token = v_token_code
        for update;

        if not found then
            raise exception 'TOKEN_NOT_FOUND' using errcode = 'P0002';
        end if;

        if v_token_rec.status <> 'unused' and v_token_rec.job_id <> v_job_id then
            raise exception 'TOKEN_ALREADY_ASSIGNED' using errcode = '23505';
        end if;

        update public.qr_tokens
        set status = 'assigned',
            job_id = v_job_id,
            assigned_at = coalesce(assigned_at, now()),
            updated_at = now()
        where id = v_token_rec.id;
    end if;

    return jsonb_build_object('job_id', v_job_id, 'status', 'created');
end;
$$;

-- RPC: Asignación Transaccional/Concurrente de Token QR a un Trabajo (Con FOR UPDATE)
create or replace function public.assign_qr_to_job(
    target_business_id uuid,
    p_token text,
    p_job_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_token_rec record;
    v_job_rec record;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'INSUFFICIENT_PERMISSIONS' using errcode = '42501';
    end if;

    -- Verificar trabajo existente y coherencia de business_id
    select id, status into v_job_rec
    from public.jobs
    where business_id = target_business_id and id = p_job_id and deleted_at is null;

    if not found then
        raise exception 'JOB_NOT_FOUND' using errcode = 'P0002';
    end if;

    -- Bloqueo explícito de fila de token para impedir asignaciones concurrentes
    select * into v_token_rec
    from public.qr_tokens
    where business_id = target_business_id and token = p_token
    for update;

    if not found then
        raise exception 'TOKEN_NOT_FOUND' using errcode = 'P0002';
    end if;

    -- Idempotencia: Si ya estaba asignado al mismo trabajo
    if v_token_rec.status = 'assigned' and v_token_rec.job_id = p_job_id then
        return jsonb_build_object('status', 'already_assigned', 'token', p_token, 'job_id', p_job_id);
    end if;

    if v_token_rec.status <> 'unused' then
        raise exception 'TOKEN_NOT_AVAILABLE' using errcode = '23505';
    end if;

    update public.qr_tokens
    set status = 'assigned',
        job_id = p_job_id,
        assigned_at = now(),
        updated_at = now()
    where id = v_token_rec.id;

    return jsonb_build_object('status', 'success', 'token', p_token, 'job_id', p_job_id);
end;
$$;

-- RPC: Liberación Transaccional Explícita de Token QR (NO automática en delivered)
create or replace function public.release_qr(
    target_business_id uuid,
    p_token text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_token_rec record;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'INSUFFICIENT_PERMISSIONS' using errcode = '42501';
    end if;

    -- Bloqueo FOR UPDATE
    select * into v_token_rec
    from public.qr_tokens
    where business_id = target_business_id and token = p_token
    for update;

    if not found then
        raise exception 'TOKEN_NOT_FOUND' using errcode = 'P0002';
    end if;

    if v_token_rec.status = 'unused' then
        return jsonb_build_object('status', 'already_unused', 'token', p_token);
    end if;

    update public.qr_tokens
    set status = 'unused',
        job_id = null,
        released_at = now(),
        updated_at = now()
    where id = v_token_rec.id;

    return jsonb_build_object('status', 'success', 'released_token', p_token);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. CONSULTA PÚBLICA SEGURA (RPC PARA SEGUIMIENTO WEB SIN LECTURA DIRECTA ANON)
-- Expose exclusivamente los datos necesarios sin permitir enumeración de tablas.
-- ---------------------------------------------------------------------------

create or replace function public.get_public_job_by_token(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_token_rec record;
    v_job_rec record;
    v_items jsonb;
begin
    if p_token is null or trim(p_token) = '' then
        return jsonb_build_object('status', 'not_found');
    end if;

    select t.id, t.token, t.status, t.job_id
    into v_token_rec
    from public.qr_tokens t
    where t.token = trim(p_token);

    if not found then
        return jsonb_build_object('status', 'not_found');
    end if;

    if v_token_rec.status = 'unused' then
        return jsonb_build_object('status', 'unused', 'token', v_token_rec.token);
    end if;

    if v_token_rec.status = 'disabled' or v_token_rec.job_id is null then
        return jsonb_build_object('status', 'disabled', 'token', v_token_rec.token);
    end if;

    -- Obtener datos públicos del trabajo asignado
    select j.id, j.customer_name_snapshot, j.customer_phone_snapshot,
           j.description, j.status, j.total_cents, j.created_at, j.ready_at, j.delivered_at
    into v_job_rec
    from public.jobs j
    where j.id = v_token_rec.job_id and j.deleted_at is null;

    if not found then
        return jsonb_build_object('status', 'job_not_found', 'token', v_token_rec.token);
    end if;

    -- Obtener ítems públicos
    select coalesce(jsonb_agg(jsonb_build_object(
        'description', ji.description,
        'service_type', ji.service_type,
        'quantity', ji.quantity,
        'unit_price_cents', ji.unit_price_cents,
        'subtotal_cents', ji.subtotal_cents
    ) order by ji.created_at), '[]'::jsonb)
    into v_items
    from public.job_items ji
    where ji.job_id = v_job_rec.id;

    return jsonb_build_object(
        'status', 'assigned',
        'token', v_token_rec.token,
        'job', jsonb_build_object(
            'customer_name', v_job_rec.customer_name_snapshot,
            'customer_phone', v_job_rec.customer_phone_snapshot,
            'description', v_job_rec.description,
            'job_status', v_job_rec.status,
            'total_cents', v_job_rec.total_cents,
            'created_at', v_job_rec.created_at,
            'ready_at', v_job_rec.ready_at,
            'delivered_at', v_job_rec.delivered_at
        ),
        'items', v_items
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. VENTA ATÓMICA DESDE TRABAJO (ACTUALIZADO: SIN CIRCULARIDAD)
-- ---------------------------------------------------------------------------

create or replace function public.confirm_sale_bundle_with_job(
    target_business_id uuid,
    payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    target_sale_id uuid;
    target_job_id uuid;
    result jsonb;
begin
    target_job_id := nullif(payload ->> 'job_id', '')::uuid;

    -- Ejecutar confirmación estándar de venta (inserta sales y sale_items)
    result := public.confirm_sale_bundle(target_business_id, payload);
    target_sale_id := (result ->> 'sale_id')::uuid;

    -- Si se vinculó a un trabajo, marcar el trabajo como entregado y enlazar en sales.job_id
    if target_job_id is not null then
        update public.jobs
        set status = 'delivered',
            delivered_at = coalesce(delivered_at, now()),
            updated_at = now()
        where id = target_job_id and business_id = target_business_id;

        update public.sales
        set job_id = target_job_id
        where id = target_sale_id and business_id = target_business_id;
    end if;

    return result;
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. ROW LEVEL SECURITY (RLS) & PRIVILEGIOS DE ACCESO
-- ---------------------------------------------------------------------------

alter table public.qr_batches enable row level security;
alter table public.jobs enable row level security;
alter table public.job_items enable row level security;
alter table public.qr_tokens enable row level security;

-- REVOCAR ACCESO DIRECTO ANON (SE CONSULTA EXCLUSIVAMENTE POR RPC get_public_job_by_token)
revoke select on public.qr_tokens from anon;
revoke select on public.jobs from anon;
revoke select on public.job_items from anon;
revoke select on public.qr_batches from anon;

drop policy if exists qr_tokens_public_read on public.qr_tokens;
drop policy if exists jobs_public_read on public.jobs;
drop policy if exists job_items_public_read on public.job_items;

-- Políticas de miembros de negocio autenticados (staff / admin / owner)
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

-- Permisos
grant select, insert, update on public.qr_batches to authenticated;
grant select, insert, update on public.jobs to authenticated;
grant select, insert, update on public.job_items to authenticated;
grant select, insert, update on public.qr_tokens to authenticated;

-- Otorgar ejecución de RPCs
grant execute on function public.generate_qr_token_code() to authenticated;
grant execute on function public.batch_generate_qr_tokens(uuid, integer) to authenticated;
grant execute on function public.create_job_bundle(uuid, jsonb) to authenticated;
grant execute on function public.assign_qr_to_job(uuid, text, uuid) to authenticated;
grant execute on function public.release_qr(uuid, text) to authenticated;
grant execute on function public.confirm_sale_bundle_with_job(uuid, jsonb) to authenticated;

-- Consulta pública (anon + authenticated) para el seguimiento por token
grant execute on function public.get_public_job_by_token(text) to anon;
grant execute on function public.get_public_job_by_token(text) to authenticated;

commit;
