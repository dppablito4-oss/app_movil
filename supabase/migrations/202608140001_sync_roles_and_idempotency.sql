-- SpaceSale: permisos de escritura por rol y operaciones append-only idempotentes.
-- Aplicar después de 202608110001_fix_business_creation_rls.sql.

begin;

alter table public.businesses add column if not exists currency text not null default 'PEN';
alter table public.businesses add column if not exists low_stock_enabled boolean not null default true;
alter table public.businesses add column if not exists receipt_message text not null default '';
alter table public.businesses drop constraint if exists businesses_currency_check;
alter table public.businesses add constraint businesses_currency_check check (currency in ('PEN'));

create table if not exists public.business_invitations (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    email text not null,
    role text not null check (role in ('admin', 'staff', 'viewer')),
    token_hash text not null unique,
    invited_by uuid not null references auth.users(id) on delete restrict,
    expires_at timestamptz not null default (now() + interval '7 days'),
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists business_invitations_business_idx
    on public.business_invitations(business_id, created_at desc);
alter table public.business_invitations enable row level security;

create or replace function private.can_write_business(target_business_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.business_members member
        where member.business_id = target_business_id
          and member.user_id = (select auth.uid())
          and member.role in ('owner', 'admin', 'staff')
    );
$$;

revoke all on function private.can_write_business(uuid) from public;
grant execute on function private.can_write_business(uuid) to authenticated;

create or replace function private.can_manage_business(target_business_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.business_members member
        where member.business_id = target_business_id
          and member.user_id = (select auth.uid())
          and member.role in ('owner', 'admin')
    );
$$;
revoke all on function private.can_manage_business(uuid) from public;
grant execute on function private.can_manage_business(uuid) to authenticated;

drop policy if exists business_invitations_select_manager on public.business_invitations;
create policy business_invitations_select_manager
on public.business_invitations for select to authenticated
using (private.can_manage_business(business_id));

drop policy if exists products_insert_member on public.products;
drop policy if exists products_update_member on public.products;
create policy products_insert_writer on public.products for insert to authenticated
with check (private.can_write_business(business_id));
create policy products_update_writer on public.products for update to authenticated
using (private.can_write_business(business_id))
with check (private.can_write_business(business_id));

drop policy if exists customers_insert_member on public.customers;
drop policy if exists customers_update_member on public.customers;
create policy customers_insert_writer on public.customers for insert to authenticated
with check (private.can_write_business(business_id));
create policy customers_update_writer on public.customers for update to authenticated
using (private.can_write_business(business_id))
with check (private.can_write_business(business_id));

drop policy if exists sales_insert_member on public.sales;
create policy sales_insert_writer on public.sales for insert to authenticated
with check (private.can_write_business(business_id));

drop policy if exists sale_items_insert_member on public.sale_items;
create policy sale_items_insert_writer on public.sale_items for insert to authenticated
with check (private.can_write_business(business_id));

drop policy if exists credit_payments_insert_member on public.credit_payments;
create policy credit_payments_insert_writer on public.credit_payments for insert to authenticated
with check (private.can_write_business(business_id));

drop policy if exists stock_movements_insert_member on public.stock_movements;
create policy stock_movements_insert_writer on public.stock_movements for insert to authenticated
with check (private.can_write_business(business_id));

drop policy if exists product_images_insert_member on storage.objects;
drop policy if exists product_images_update_member on storage.objects;
drop policy if exists product_images_delete_member on storage.objects;
create policy product_images_insert_writer on storage.objects for insert to authenticated
with check (
    bucket_id = 'product-images'
    and private.can_write_business(((storage.foldername(name))[1])::uuid)
);
create policy product_images_update_writer on storage.objects for update to authenticated
using (
    bucket_id = 'product-images'
    and private.can_write_business(((storage.foldername(name))[1])::uuid)
)
with check (
    bucket_id = 'product-images'
    and private.can_write_business(((storage.foldername(name))[1])::uuid)
);
create policy product_images_delete_writer on storage.objects for delete to authenticated
using (
    bucket_id = 'product-images'
    and private.can_write_business(((storage.foldername(name))[1])::uuid)
);

-- El UUID enviado por Android vuelve idempotente el reintento por timeout sin
-- habilitar UPDATE ni DELETE general sobre el historial financiero.
create or replace function public.insert_sale_if_absent(payload jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    inserted_id uuid := (payload ->> 'id')::uuid;
begin
    insert into public.sales (
        id, business_id, customer_id, total_cents, payment_method,
        status, sold_at, evidence_path, paid_at
    ) values (
        inserted_id,
        (payload ->> 'business_id')::uuid,
        nullif(payload ->> 'customer_id', '')::uuid,
        (payload ->> 'total_cents')::bigint,
        payload ->> 'payment_method',
        payload ->> 'status',
        (payload ->> 'sold_at')::timestamptz,
        nullif(payload ->> 'evidence_path', ''),
        nullif(payload ->> 'paid_at', '')::timestamptz
    )
    on conflict (id) do nothing;
    return inserted_id;
end;
$$;

revoke all on function public.insert_sale_if_absent(jsonb) from public;
grant execute on function public.insert_sale_if_absent(jsonb) to authenticated;

-- Inserta cabecera y lineas en una sola transaccion. Si Android pierde la
-- respuesta, repetir el mismo UUID completa cualquier dato antiguo faltante
-- sin crear una segunda venta.
create or replace function public.insert_sale_bundle_if_absent(payload jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    sale_payload jsonb := payload -> 'sale';
    item_payload jsonb;
    inserted_id uuid := (sale_payload ->> 'id')::uuid;
begin
    insert into public.sales (
        id, business_id, customer_id, total_cents, payment_method,
        status, sold_at, evidence_path, paid_at
    ) values (
        inserted_id,
        (sale_payload ->> 'business_id')::uuid,
        nullif(sale_payload ->> 'customer_id', '')::uuid,
        (sale_payload ->> 'total_cents')::bigint,
        sale_payload ->> 'payment_method',
        sale_payload ->> 'status',
        (sale_payload ->> 'sold_at')::timestamptz,
        nullif(sale_payload ->> 'evidence_path', ''),
        nullif(sale_payload ->> 'paid_at', '')::timestamptz
    )
    on conflict (id) do nothing;

    for item_payload in
        select value from jsonb_array_elements(coalesce(payload -> 'items', '[]'::jsonb)) as item(value)
    loop
        insert into public.sale_items (
            id, business_id, sale_id, product_id, product_name_snapshot,
            quantity, unit_price_cents, unit_cost_cents
        ) values (
            (item_payload ->> 'id')::uuid,
            (item_payload ->> 'business_id')::uuid,
            (item_payload ->> 'sale_id')::uuid,
            nullif(item_payload ->> 'product_id', '')::uuid,
            item_payload ->> 'product_name_snapshot',
            (item_payload ->> 'quantity')::integer,
            (item_payload ->> 'unit_price_cents')::bigint,
            (item_payload ->> 'unit_cost_cents')::bigint
        )
        on conflict (id) do nothing;
    end loop;

    return inserted_id;
end;
$$;

revoke all on function public.insert_sale_bundle_if_absent(jsonb) from public;
grant execute on function public.insert_sale_bundle_if_absent(jsonb) to authenticated;

create or replace function public.insert_sale_item_if_absent(payload jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    inserted_id uuid := (payload ->> 'id')::uuid;
begin
    insert into public.sale_items (
        id, business_id, sale_id, product_id, product_name_snapshot,
        quantity, unit_price_cents, unit_cost_cents
    ) values (
        inserted_id,
        (payload ->> 'business_id')::uuid,
        (payload ->> 'sale_id')::uuid,
        nullif(payload ->> 'product_id', '')::uuid,
        payload ->> 'product_name_snapshot',
        (payload ->> 'quantity')::integer,
        (payload ->> 'unit_price_cents')::bigint,
        (payload ->> 'unit_cost_cents')::bigint
    )
    on conflict (id) do nothing;
    return inserted_id;
end;
$$;

revoke all on function public.insert_sale_item_if_absent(jsonb) from public;
grant execute on function public.insert_sale_item_if_absent(jsonb) to authenticated;

create or replace function public.insert_credit_payment_if_absent(payload jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    inserted_id uuid := (payload ->> 'id')::uuid;
begin
    insert into public.credit_payments (
        id, business_id, customer_id, sale_id, amount_cents,
        payment_method, notes, paid_at
    ) values (
        inserted_id,
        (payload ->> 'business_id')::uuid,
        (payload ->> 'customer_id')::uuid,
        (payload ->> 'sale_id')::uuid,
        (payload ->> 'amount_cents')::bigint,
        payload ->> 'payment_method',
        coalesce(payload ->> 'notes', ''),
        (payload ->> 'paid_at')::timestamptz
    )
    on conflict (id) do nothing;
    return inserted_id;
end;
$$;

revoke all on function public.insert_credit_payment_if_absent(jsonb) from public;
grant execute on function public.insert_credit_payment_if_absent(jsonb) to authenticated;

alter table public.stock_movements
    drop constraint if exists stock_movements_type_check;
alter table public.stock_movements
    add constraint stock_movements_type_check
    check (type in ('INITIAL', 'PURCHASE', 'SALE', 'SALE_CANCEL', 'ADJUSTMENT', 'RETURN', 'LOSS', 'DAMAGE', 'EXPIRED'));

create or replace function public.insert_stock_movement_if_absent(payload jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    inserted_id uuid := (payload ->> 'id')::uuid;
begin
    insert into public.stock_movements (
        id, business_id, product_id, sale_id, type,
        quantity_delta, notes, created_at
    ) values (
        inserted_id,
        (payload ->> 'business_id')::uuid,
        (payload ->> 'product_id')::uuid,
        nullif(payload ->> 'sale_id', '')::uuid,
        payload ->> 'type',
        (payload ->> 'quantity_delta')::integer,
        coalesce(payload ->> 'notes', ''),
        (payload ->> 'created_at')::timestamptz
    )
    on conflict (id) do nothing;
    return inserted_id;
end;
$$;

revoke all on function public.insert_stock_movement_if_absent(jsonb) from public;
grant execute on function public.insert_stock_movement_if_absent(jsonb) to authenticated;

create or replace function public.update_business_settings(
    target_business_id uuid,
    target_currency text,
    target_daily_goal_cents bigint,
    target_low_stock_enabled boolean,
    target_receipt_message text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'No autorizado para modificar este negocio' using errcode = '42501';
    end if;
    if target_currency <> 'PEN' or target_daily_goal_cents < 0 then
        raise exception 'Configuracion invalida' using errcode = '22023';
    end if;

    update public.businesses
    set currency = target_currency,
        daily_goal_cents = target_daily_goal_cents,
        low_stock_enabled = target_low_stock_enabled,
        receipt_message = left(coalesce(target_receipt_message, ''), 240),
        updated_at = now()
    where id = target_business_id;
    return target_business_id;
end;
$$;

revoke all on function public.update_business_settings(uuid, text, bigint, boolean, text) from public;
grant execute on function public.update_business_settings(uuid, text, bigint, boolean, text) to authenticated;

create or replace function public.create_business_invitation(
    target_business_id uuid,
    target_email text,
    target_role text default 'staff'
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    raw_token text := encode(gen_random_bytes(24), 'hex');
begin
    if not private.can_manage_business(target_business_id) then
        raise exception 'No autorizado para invitar miembros' using errcode = '42501';
    end if;
    if target_role not in ('admin', 'staff', 'viewer') then
        raise exception 'Rol invalido' using errcode = '22023';
    end if;
    if position('@' in trim(target_email)) <= 1 then
        raise exception 'Correo invalido' using errcode = '22023';
    end if;

    insert into public.business_invitations (
        business_id, email, role, token_hash, invited_by
    ) values (
        target_business_id,
        lower(trim(target_email)),
        target_role,
        encode(digest(raw_token, 'sha256'), 'hex'),
        auth.uid()
    );
    return raw_token;
end;
$$;

create or replace function public.accept_business_invitation(raw_token text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    invitation public.business_invitations%rowtype;
    session_email text := lower(coalesce(auth.jwt() ->> 'email', ''));
begin
    select * into invitation
    from public.business_invitations
    where token_hash = encode(digest(raw_token, 'sha256'), 'hex')
      and accepted_at is null
      and revoked_at is null
      and expires_at > now()
    for update;

    if invitation.id is null or invitation.email <> session_email then
        raise exception 'Invitacion invalida o vencida' using errcode = '22023';
    end if;

    insert into public.business_members (business_id, user_id, role)
    values (invitation.business_id, auth.uid(), invitation.role)
    on conflict (business_id, user_id) do update set role = excluded.role;
    update public.business_invitations set accepted_at = now() where id = invitation.id;
    return invitation.business_id;
end;
$$;

revoke all on function public.create_business_invitation(uuid, text, text) from public;
grant execute on function public.create_business_invitation(uuid, text, text) to authenticated;
revoke all on function public.accept_business_invitation(text) from public;
grant execute on function public.accept_business_invitation(text) to authenticated;
grant select on public.business_invitations to authenticated;
revoke all on public.business_invitations from anon;

-- El historial no expone UPDATE directo. Esta funcion permite solamente las
-- transiciones de estado que usa el POS y valida negocio/rol dentro del servidor.
create or replace function public.transition_sale_status(
    target_sale_id uuid,
    target_business_id uuid,
    new_status text,
    target_paid_at timestamptz default null,
    target_cancellation_reason text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_status text;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'No autorizado para modificar este negocio' using errcode = '42501';
    end if;

    if new_status not in ('PENDIENTE', 'CERRADO', 'ANULADO') then
        raise exception 'Estado de venta invalido' using errcode = '22023';
    end if;

    select status into current_status
    from public.sales
    where id = target_sale_id and business_id = target_business_id
    for update;

    if current_status is null then
        raise exception 'Venta no encontrada' using errcode = 'P0002';
    end if;

    if current_status = new_status then
        return target_sale_id;
    end if;

    if not (
        (current_status = 'PENDIENTE' and new_status in ('CERRADO', 'ANULADO'))
        or (current_status = 'CERRADO' and new_status = 'ANULADO')
    ) then
        raise exception 'Transicion de venta no permitida' using errcode = '22023';
    end if;

    update public.sales as sale
    set status = new_status,
        paid_at = case when new_status = 'CERRADO' then coalesce(target_paid_at, now()) else sale.paid_at end,
        cancellation_reason = case when new_status = 'ANULADO' then coalesce(nullif(trim(target_cancellation_reason), ''), 'Anulada desde SpaceSale') else sale.cancellation_reason end,
        cancelled_at = case when new_status = 'ANULADO' then now() else sale.cancelled_at end,
        updated_at = now()
    where id = target_sale_id and business_id = target_business_id;

    return target_sale_id;
end;
$$;

revoke all on function public.transition_sale_status(uuid, uuid, text, timestamptz, text) from public;
grant execute on function public.transition_sale_status(uuid, uuid, text, timestamptz, text) to authenticated;

commit;
