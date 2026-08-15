-- SpaceSale - Esquema remoto inicial
-- Ejecutar una sola vez mediante Supabase SQL Editor o Supabase CLI.
-- Room continúa siendo la fuente local; los UUID de estas tablas serán sync_id.

begin;

create extension if not exists pgcrypto;
create schema if not exists private;

-- ---------------------------------------------------------------------------
-- Funciones compartidas
-- ---------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Usuarios y negocios
-- ---------------------------------------------------------------------------

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.businesses (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete restrict,
    name text not null check (char_length(trim(name)) between 1 and 120),
    address text,
    phone text,
    daily_goal_cents bigint not null default 0 check (daily_goal_cents >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.business_members (
    business_id uuid not null references public.businesses(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'staff' check (role in ('owner', 'admin', 'staff', 'viewer')),
    created_at timestamptz not null default now(),
    primary key (business_id, user_id)
);

create index business_members_user_id_idx
    on public.business_members(user_id, business_id);
create index businesses_owner_id_idx
    on public.businesses(owner_id);

create or replace function private.is_business_member(target_business_id uuid)
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
    );
$$;

create or replace function private.is_business_owner(target_business_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.businesses business
        where business.id = target_business_id
          and business.owner_id = (select auth.uid())
    );
$$;

revoke all on function private.is_business_member(uuid) from public;
revoke all on function private.is_business_owner(uuid) from public;
grant usage on schema private to authenticated;
grant execute on function private.is_business_member(uuid) to authenticated;
grant execute on function private.is_business_owner(uuid) to authenticated;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles(id, display_name)
    values (
        new.id,
        coalesce(
            nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
            split_part(coalesce(new.email, ''), '@', 1),
            ''
        )
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

create or replace function public.add_business_owner_as_member()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.business_members(business_id, user_id, role)
    values (new.id, new.owner_id, 'owner')
    on conflict (business_id, user_id)
    do update set role = 'owner';
    return new;
end;
$$;

revoke all on function public.set_updated_at() from public;
revoke all on function public.handle_new_user() from public;
revoke all on function public.add_business_owner_as_member() from public;

drop trigger if exists on_business_created on public.businesses;
create trigger on_business_created
after insert on public.businesses
for each row execute function public.add_business_owner_as_member();

-- ---------------------------------------------------------------------------
-- Catálogo y clientes
-- ---------------------------------------------------------------------------

create table public.products (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 160),
    barcode text,
    cost_cents bigint not null default 0 check (cost_cents >= 0),
    sale_cents bigint not null check (sale_cents >= 0),
    stock integer not null default 0 check (stock >= 0),
    min_stock integer not null default 0 check (min_stock >= 0),
    image_path text,
    normalized_search text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint products_business_id_id_unique unique (business_id, id)
);

create unique index products_business_barcode_unique
    on public.products(business_id, barcode)
    where barcode is not null and deleted_at is null;
create index products_business_updated_idx
    on public.products(business_id, updated_at, id);
create index products_low_stock_idx
    on public.products(business_id, stock, min_stock)
    where deleted_at is null;

create table public.customers (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 160),
    phone text,
    notes text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint customers_business_id_id_unique unique (business_id, id)
);

create index customers_business_updated_idx
    on public.customers(business_id, updated_at, id);
create index customers_business_name_idx
    on public.customers(business_id, name)
    where deleted_at is null;

-- ---------------------------------------------------------------------------
-- Historial de ventas, abonos y stock
-- No se conceden UPDATE/DELETE al cliente para estas tablas.
-- ---------------------------------------------------------------------------

create table public.sales (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    customer_id uuid,
    total_cents bigint not null check (total_cents >= 0),
    payment_method text not null,
    status text not null,
    sold_at timestamptz not null,
    evidence_path text,
    paid_at timestamptz,
    cancellation_reason text,
    cancelled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint sales_business_id_id_unique unique (business_id, id),
    constraint sales_business_customer_fk
        foreign key (business_id, customer_id)
        references public.customers(business_id, id)
        on delete restrict
);

create index sales_business_sold_idx
    on public.sales(business_id, sold_at desc, id);
create index sales_business_updated_idx
    on public.sales(business_id, updated_at, id);
create index sales_customer_idx
    on public.sales(customer_id, sold_at desc)
    where customer_id is not null;

create table public.sale_items (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    sale_id uuid not null,
    product_id uuid,
    product_name_snapshot text not null,
    quantity integer not null check (quantity > 0),
    unit_price_cents bigint not null check (unit_price_cents >= 0),
    unit_cost_cents bigint not null default 0 check (unit_cost_cents >= 0),
    created_at timestamptz not null default now(),
    constraint sale_items_business_sale_fk
        foreign key (business_id, sale_id)
        references public.sales(business_id, id)
        on delete restrict,
    constraint sale_items_business_product_fk
        foreign key (business_id, product_id)
        references public.products(business_id, id)
        on delete restrict
);

create index sale_items_sale_idx on public.sale_items(sale_id);
create index sale_items_business_created_idx
    on public.sale_items(business_id, created_at, id);

create table public.credit_payments (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    customer_id uuid not null,
    sale_id uuid not null,
    amount_cents bigint not null check (amount_cents > 0),
    payment_method text not null,
    notes text not null default '',
    paid_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint credit_payments_business_customer_fk
        foreign key (business_id, customer_id)
        references public.customers(business_id, id)
        on delete restrict,
    constraint credit_payments_business_sale_fk
        foreign key (business_id, sale_id)
        references public.sales(business_id, id)
        on delete restrict
);

create index credit_payments_sale_idx
    on public.credit_payments(sale_id, paid_at, id);
create index credit_payments_customer_idx
    on public.credit_payments(customer_id, paid_at desc);
create index credit_payments_business_created_idx
    on public.credit_payments(business_id, created_at, id);

create table public.stock_movements (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    product_id uuid not null,
    sale_id uuid,
    type text not null check (type in ('INITIAL', 'PURCHASE', 'SALE', 'SALE_CANCEL', 'ADJUSTMENT')),
    quantity_delta integer not null check (quantity_delta <> 0),
    notes text not null default '',
    created_at timestamptz not null default now(),
    constraint stock_movements_business_product_fk
        foreign key (business_id, product_id)
        references public.products(business_id, id)
        on delete restrict,
    constraint stock_movements_business_sale_fk
        foreign key (business_id, sale_id)
        references public.sales(business_id, id)
        on delete restrict
);

create index stock_movements_product_idx
    on public.stock_movements(product_id, created_at, id);
create index stock_movements_business_created_idx
    on public.stock_movements(business_id, created_at, id);

-- Triggers de timestamps solo para datos que sí pueden editarse.
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger businesses_set_updated_at
before update on public.businesses
for each row execute function public.set_updated_at();

create trigger products_set_updated_at
before update on public.products
for each row execute function public.set_updated_at();

create trigger customers_set_updated_at
before update on public.customers
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.businesses enable row level security;
alter table public.business_members enable row level security;
alter table public.products enable row level security;
alter table public.customers enable row level security;
alter table public.sales enable row level security;
alter table public.sale_items enable row level security;
alter table public.credit_payments enable row level security;
alter table public.stock_movements enable row level security;

create policy profiles_select_own
on public.profiles for select to authenticated
using ((select auth.uid()) = id);

create policy profiles_update_own
on public.profiles for update to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

create policy businesses_select_member
on public.businesses for select to authenticated
using (private.is_business_member(id));

create policy businesses_insert_owner
on public.businesses for insert to authenticated
with check ((select auth.uid()) = owner_id);

create policy businesses_update_owner
on public.businesses for update to authenticated
using (private.is_business_owner(id))
with check (private.is_business_owner(id) and owner_id = (select auth.uid()));

create policy business_members_select_member
on public.business_members for select to authenticated
using (private.is_business_member(business_id));

create policy business_members_insert_owner
on public.business_members for insert to authenticated
with check (private.is_business_owner(business_id));

create policy business_members_update_owner
on public.business_members for update to authenticated
using (private.is_business_owner(business_id))
with check (private.is_business_owner(business_id));

create policy business_members_delete_owner
on public.business_members for delete to authenticated
using (private.is_business_owner(business_id) and role <> 'owner');

create policy products_select_member
on public.products for select to authenticated
using (private.is_business_member(business_id));

create policy products_insert_member
on public.products for insert to authenticated
with check (private.is_business_member(business_id));

create policy products_update_member
on public.products for update to authenticated
using (private.is_business_member(business_id))
with check (private.is_business_member(business_id));

create policy customers_select_member
on public.customers for select to authenticated
using (private.is_business_member(business_id));

create policy customers_insert_member
on public.customers for insert to authenticated
with check (private.is_business_member(business_id));

create policy customers_update_member
on public.customers for update to authenticated
using (private.is_business_member(business_id))
with check (private.is_business_member(business_id));

create policy sales_select_member
on public.sales for select to authenticated
using (private.is_business_member(business_id));

create policy sales_insert_member
on public.sales for insert to authenticated
with check (private.is_business_member(business_id));

create policy sale_items_select_member
on public.sale_items for select to authenticated
using (private.is_business_member(business_id));

create policy sale_items_insert_member
on public.sale_items for insert to authenticated
with check (private.is_business_member(business_id));

create policy credit_payments_select_member
on public.credit_payments for select to authenticated
using (private.is_business_member(business_id));

create policy credit_payments_insert_member
on public.credit_payments for insert to authenticated
with check (private.is_business_member(business_id));

create policy stock_movements_select_member
on public.stock_movements for select to authenticated
using (private.is_business_member(business_id));

create policy stock_movements_insert_member
on public.stock_movements for insert to authenticated
with check (private.is_business_member(business_id));

grant select, update on public.profiles to authenticated;
grant select, insert, update on public.businesses to authenticated;
grant select, insert, update, delete on public.business_members to authenticated;
grant select, insert, update on public.products to authenticated;
grant select, insert, update on public.customers to authenticated;
grant select, insert on public.sales to authenticated;
grant select, insert on public.sale_items to authenticated;
grant select, insert on public.credit_payments to authenticated;
grant select, insert on public.stock_movements to authenticated;

revoke all on public.profiles from anon;
revoke all on public.businesses from anon;
revoke all on public.business_members from anon;
revoke all on public.products from anon;
revoke all on public.customers from anon;
revoke all on public.sales from anon;
revoke all on public.sale_items from anon;
revoke all on public.credit_payments from anon;
revoke all on public.stock_movements from anon;

-- ---------------------------------------------------------------------------
-- Supabase Storage
-- Ruta esperada: <business_id>/products/<product_id>.<extension>
-- ---------------------------------------------------------------------------

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
    'product-images',
    'product-images',
    false,
    5242880,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy product_images_select_member
on storage.objects for select to authenticated
using (
    bucket_id = 'product-images'
    and private.is_business_member(((storage.foldername(name))[1])::uuid)
);

create policy product_images_insert_member
on storage.objects for insert to authenticated
with check (
    bucket_id = 'product-images'
    and private.is_business_member(((storage.foldername(name))[1])::uuid)
);

create policy product_images_update_member
on storage.objects for update to authenticated
using (
    bucket_id = 'product-images'
    and private.is_business_member(((storage.foldername(name))[1])::uuid)
)
with check (
    bucket_id = 'product-images'
    and private.is_business_member(((storage.foldername(name))[1])::uuid)
);

create policy product_images_delete_member
on storage.objects for delete to authenticated
using (
    bucket_id = 'product-images'
    and private.is_business_member(((storage.foldername(name))[1])::uuid)
);

commit;
