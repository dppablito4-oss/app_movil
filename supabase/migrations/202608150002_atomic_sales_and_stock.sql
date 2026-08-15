-- SpaceSale: ventas, cancelaciones y movimientos de stock atomicos.
-- Room conserva la escritura optimista offline; Supabase confirma cada paquete
-- completo y nunca acepta que Android sobrescriba products.stock directamente.

begin;

create or replace function public.upsert_product_metadata(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_id uuid := (payload ->> 'id')::uuid;
    target_business_id uuid := (payload ->> 'business_id')::uuid;
    existing_business_id uuid;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'PRODUCT_FORBIDDEN' using errcode = '42501';
    end if;

    if nullif(trim(payload ->> 'name'), '') is null
       or (payload ->> 'cost_cents')::bigint < 0
       or (payload ->> 'sale_cents')::bigint < 0
       or (payload ->> 'min_stock')::integer < 0 then
        raise exception 'PRODUCT_INVALID' using errcode = '22023';
    end if;

    select business_id into existing_business_id
    from public.products
    where id = target_id;

    if existing_business_id is not null and existing_business_id <> target_business_id then
        raise exception 'PRODUCT_BUSINESS_MISMATCH' using errcode = '42501';
    end if;

    insert into public.products (
        id, business_id, name, barcode, cost_cents, sale_cents, stock,
        min_stock, image_path, normalized_search, deleted_at
    ) values (
        target_id,
        target_business_id,
        trim(payload ->> 'name'),
        nullif(trim(payload ->> 'barcode'), ''),
        (payload ->> 'cost_cents')::bigint,
        (payload ->> 'sale_cents')::bigint,
        0,
        (payload ->> 'min_stock')::integer,
        nullif(payload ->> 'image_path', ''),
        coalesce(payload ->> 'normalized_search', ''),
        nullif(payload ->> 'deleted_at', '')::timestamptz
    )
    on conflict (id) do update set
        name = excluded.name,
        barcode = excluded.barcode,
        cost_cents = excluded.cost_cents,
        sale_cents = excluded.sale_cents,
        min_stock = excluded.min_stock,
        image_path = excluded.image_path,
        normalized_search = excluded.normalized_search,
        deleted_at = excluded.deleted_at;

    return target_id;
end;
$$;

-- Declaracion temporal para que confirm_sale_bundle pueda resolver la llamada
-- de recuperacion si el servidor confirmo una venta y Android perdio la respuesta.
create or replace function public.cancel_sale_bundle(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    raise exception 'ATOMIC_MIGRATION_INCOMPLETE' using errcode = '55000';
end;
$$;

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

    if exists (
        select 1
        from jsonb_array_elements(payload -> 'movements') movement(value)
        where (value ->> 'business_id')::uuid <> target_business_id
           or (value ->> 'sale_id')::uuid <> target_sale_id
           or value ->> 'type' not in ('SALE', 'SALE_CANCEL')
           or (value ->> 'quantity_delta')::integer = 0
    ) or (
        select count(*) from jsonb_array_elements(payload -> 'movements') movement(value)
    ) <> (
        select count(distinct (value ->> 'id')::uuid)
        from jsonb_array_elements(payload -> 'movements') movement(value)
    ) then
        raise exception 'SALE_MOVEMENT_INVALID' using errcode = '22023';
    end if;

    if exists (
        select 1
        from (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity')::integer) quantity
            from jsonb_array_elements(payload -> 'items') item(value)
            group by (value ->> 'product_id')::uuid
        ) lines
        full join (
            select (value ->> 'product_id')::uuid product_id,
                   -(sum((value ->> 'quantity_delta')::integer)
                       filter (where value ->> 'type' = 'SALE')) quantity
            from jsonb_array_elements(payload -> 'movements') movement(value)
            group by (value ->> 'product_id')::uuid
        ) sale_moves using (product_id)
        where lines.quantity is distinct from sale_moves.quantity
    ) then
        raise exception 'SALE_MOVEMENT_QUANTITY_MISMATCH' using errcode = '22023';
    end if;

    if target_status = 'ANULADO' and exists (
        select 1
        from (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity_delta')::integer) net_delta
            from jsonb_array_elements(payload -> 'movements') movement(value)
            group by (value ->> 'product_id')::uuid
        ) movement_totals
        where movement_totals.net_delta <> 0
    ) then
        raise exception 'CANCELLED_SALE_MOVEMENT_MISMATCH' using errcode = '22023';
    elsif target_status <> 'ANULADO' and exists (
        select 1 from jsonb_array_elements(payload -> 'movements') movement(value)
        where value ->> 'type' <> 'SALE'
    ) then
        raise exception 'SALE_CANCEL_MOVEMENT_UNEXPECTED' using errcode = '22023';
    end if;

    perform product.id
    from public.products product
    join (
        select (value ->> 'product_id')::uuid product_id,
               sum((value ->> 'quantity')::integer) quantity
        from jsonb_array_elements(payload -> 'items') item(value)
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
    ) requested
    join public.products product on product.id = requested.product_id
    where product.business_id = target_business_id and product.deleted_at is null;

    if affected_count <> (
        select count(distinct (value ->> 'product_id')::uuid)
        from jsonb_array_elements(payload -> 'items') item(value)
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
            group by (value ->> 'product_id')::uuid
        ) requested on requested.product_id = product.id
        where product.business_id = target_business_id
          and product.stock < requested.quantity
    ) then
        raise exception 'STOCK_INSUFFICIENT' using errcode = 'P0001';
    end if;

    insert into public.sales (
        id, business_id, customer_id, total_cents, payment_method,
        status, sold_at, evidence_path, paid_at, cancellation_reason, cancelled_at
    ) values (
        target_sale_id,
        target_business_id,
        nullif(sale_payload ->> 'customer_id', '')::uuid,
        (sale_payload ->> 'total_cents')::bigint,
        sale_payload ->> 'payment_method',
        target_status,
        (sale_payload ->> 'sold_at')::timestamptz,
        nullif(sale_payload ->> 'evidence_path', ''),
        nullif(sale_payload ->> 'paid_at', '')::timestamptz,
        case when target_status = 'ANULADO' then 'Anulada antes de sincronizar' end,
        case when target_status = 'ANULADO' then now() end
    );

    for item_payload in
        select value from jsonb_array_elements(payload -> 'items') item(value)
    loop
        insert into public.sale_items (
            id, business_id, sale_id, product_id, product_name_snapshot,
            quantity, unit_price_cents, unit_cost_cents, created_at
        ) values (
            (item_payload ->> 'id')::uuid,
            target_business_id,
            target_sale_id,
            (item_payload ->> 'product_id')::uuid,
            item_payload ->> 'product_name_snapshot',
            (item_payload ->> 'quantity')::integer,
            (item_payload ->> 'unit_price_cents')::bigint,
            (item_payload ->> 'unit_cost_cents')::bigint,
            coalesce(nullif(item_payload ->> 'created_at', '')::timestamptz, now())
        );
    end loop;

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

    if target_status <> 'ANULADO' then
        update public.products product
        set stock = product.stock - requested.quantity,
            updated_at = now()
        from (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity')::integer) quantity
            from jsonb_array_elements(payload -> 'items') item(value)
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
      );

    return jsonb_build_object('sale_id', target_sale_id, 'stocks', result_stocks);
end;
$$;

create or replace function public.apply_stock_movement(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_id uuid := (payload ->> 'id')::uuid;
    target_business_id uuid := (payload ->> 'business_id')::uuid;
    target_product_id uuid := (payload ->> 'product_id')::uuid;
    target_type text := payload ->> 'type';
    target_delta integer := (payload ->> 'quantity_delta')::integer;
    existing_movement public.stock_movements%rowtype;
    current_product public.products%rowtype;
    resulting_stock integer;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'STOCK_FORBIDDEN' using errcode = '42501';
    end if;

    if nullif(payload ->> 'sale_id', '') is not null
       or target_type in ('SALE', 'SALE_CANCEL') then
        raise exception 'SALE_MOVEMENT_REQUIRES_SALE_RPC' using errcode = '22023';
    end if;

    if target_type not in ('INITIAL', 'PURCHASE', 'RETURN', 'ADJUSTMENT', 'LOSS', 'DAMAGE', 'EXPIRED')
       or target_delta = 0
       or (target_type in ('INITIAL', 'PURCHASE', 'RETURN') and target_delta < 0)
       or (target_type in ('LOSS', 'DAMAGE', 'EXPIRED') and target_delta > 0) then
        raise exception 'STOCK_MOVEMENT_INVALID' using errcode = '22023';
    end if;

    select * into existing_movement
    from public.stock_movements
    where id = target_id;

    if found then
        if existing_movement.business_id <> target_business_id
           or existing_movement.product_id <> target_product_id
           or existing_movement.type <> target_type
           or existing_movement.quantity_delta <> target_delta then
            raise exception 'STOCK_IDEMPOTENCY_CONFLICT' using errcode = '23505';
        end if;
        select stock into resulting_stock from public.products where id = target_product_id;
        return jsonb_build_object(
            'movement_id', target_id,
            'stocks', jsonb_build_array(jsonb_build_object('product_id', target_product_id, 'stock', resulting_stock))
        );
    end if;

    select * into current_product
    from public.products
    where id = target_product_id and business_id = target_business_id and deleted_at is null
    for update;

    if not found then
        raise exception 'STOCK_PRODUCT_INVALID' using errcode = '23503';
    end if;

    resulting_stock := current_product.stock + target_delta;
    if resulting_stock < 0 then
        raise exception 'STOCK_INSUFFICIENT' using errcode = 'P0001';
    end if;

    insert into public.stock_movements (
        id, business_id, product_id, sale_id, type,
        quantity_delta, notes, created_at
    ) values (
        target_id,
        target_business_id,
        target_product_id,
        null,
        target_type,
        target_delta,
        left(coalesce(payload ->> 'notes', ''), 240),
        coalesce(nullif(payload ->> 'created_at', '')::timestamptz, now())
    );

    update public.products
    set stock = resulting_stock, updated_at = now()
    where id = target_product_id and business_id = target_business_id;

    return jsonb_build_object(
        'movement_id', target_id,
        'stocks', jsonb_build_array(jsonb_build_object('product_id', target_product_id, 'stock', resulting_stock))
    );
end;
$$;

create or replace function public.cancel_sale_bundle(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_sale_id uuid := (payload ->> 'sale_id')::uuid;
    target_business_id uuid := (payload ->> 'business_id')::uuid;
    movement_payload jsonb;
    current_sale public.sales%rowtype;
    result_stocks jsonb;
begin
    if not private.can_write_business(target_business_id) then
        raise exception 'SALE_FORBIDDEN' using errcode = '42501';
    end if;

    select * into current_sale
    from public.sales
    where id = target_sale_id and business_id = target_business_id
    for update;

    if not found then
        raise exception 'SALE_NOT_FOUND' using errcode = 'P0002';
    end if;

    if current_sale.status = 'ANULADO' then
        select coalesce(jsonb_agg(jsonb_build_object('product_id', product.id, 'stock', product.stock) order by product.id), '[]'::jsonb)
        into result_stocks
        from public.products product
        where product.business_id = target_business_id
          and product.id in (
              select distinct item.product_id from public.sale_items item
              where item.business_id = target_business_id and item.sale_id = target_sale_id and item.product_id is not null
          );
        return jsonb_build_object('sale_id', target_sale_id, 'stocks', result_stocks);
    end if;

    if current_sale.status not in ('PENDIENTE', 'CERRADO')
       or jsonb_typeof(coalesce(payload -> 'movements', 'null'::jsonb)) <> 'array' then
        raise exception 'SALE_CANCEL_INVALID' using errcode = '22023';
    end if;

    if exists (
        select 1 from jsonb_array_elements(payload -> 'movements') movement(value)
        where (value ->> 'business_id')::uuid <> target_business_id
           or (value ->> 'sale_id')::uuid <> target_sale_id
           or value ->> 'type' <> 'SALE_CANCEL'
           or (value ->> 'quantity_delta')::integer <= 0
    ) or exists (
        select 1
        from (
            select product_id, sum(quantity) quantity
            from public.sale_items
            where business_id = target_business_id and sale_id = target_sale_id
            group by product_id
        ) lines
        full join (
            select (value ->> 'product_id')::uuid product_id,
                   sum((value ->> 'quantity_delta')::integer) quantity
            from jsonb_array_elements(payload -> 'movements') movement(value)
            group by (value ->> 'product_id')::uuid
        ) cancel_moves using (product_id)
        where lines.quantity is distinct from cancel_moves.quantity
    ) then
        raise exception 'SALE_CANCEL_MOVEMENT_MISMATCH' using errcode = '22023';
    end if;

    perform product.id
    from public.products product
    join (
        select product_id, sum(quantity) quantity
        from public.sale_items
        where business_id = target_business_id and sale_id = target_sale_id
        group by product_id
    ) lines on lines.product_id = product.id
    where product.business_id = target_business_id
    order by product.id
    for update of product;

    for movement_payload in
        select value from jsonb_array_elements(payload -> 'movements') movement(value)
    loop
        if exists (select 1 from public.stock_movements where id = (movement_payload ->> 'id')::uuid) then
            raise exception 'SALE_CANCEL_MOVEMENT_ID_CONFLICT' using errcode = '23505';
        end if;
        insert into public.stock_movements (
            id, business_id, product_id, sale_id, type,
            quantity_delta, notes, created_at
        ) values (
            (movement_payload ->> 'id')::uuid,
            target_business_id,
            (movement_payload ->> 'product_id')::uuid,
            target_sale_id,
            'SALE_CANCEL',
            (movement_payload ->> 'quantity_delta')::integer,
            left(coalesce(movement_payload ->> 'notes', ''), 240),
            coalesce(nullif(movement_payload ->> 'created_at', '')::timestamptz, now())
        );
    end loop;

    update public.products product
    set stock = product.stock + lines.quantity,
        updated_at = now()
    from (
        select product_id, sum(quantity) quantity
        from public.sale_items
        where business_id = target_business_id and sale_id = target_sale_id
        group by product_id
    ) lines
    where product.id = lines.product_id and product.business_id = target_business_id;

    update public.sales
    set status = 'ANULADO',
        cancellation_reason = coalesce(nullif(trim(payload ->> 'reason'), ''), 'Anulada desde SpaceSale'),
        cancelled_at = now(),
        updated_at = now()
    where id = target_sale_id and business_id = target_business_id;

    select coalesce(jsonb_agg(jsonb_build_object('product_id', product.id, 'stock', product.stock) order by product.id), '[]'::jsonb)
    into result_stocks
    from public.products product
    where product.business_id = target_business_id
      and product.id in (
          select distinct item.product_id from public.sale_items item
          where item.business_id = target_business_id and item.sale_id = target_sale_id and item.product_id is not null
      );

    return jsonb_build_object('sale_id', target_sale_id, 'stocks', result_stocks);
end;
$$;

-- Mantiene el cierre de fiados, pero toda anulacion debe restaurar stock por
-- cancel_sale_bundle. Reemplaza la version anterior que solo cambiaba estado.
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
        raise exception 'SALE_FORBIDDEN' using errcode = '42501';
    end if;
    if new_status <> 'CERRADO' then
        raise exception 'SALE_CANCEL_REQUIRES_ATOMIC_RPC' using errcode = '22023';
    end if;

    select status into current_status
    from public.sales
    where id = target_sale_id and business_id = target_business_id
    for update;

    if current_status is null then
        raise exception 'SALE_NOT_FOUND' using errcode = 'P0002';
    end if;
    if current_status = 'CERRADO' then
        return target_sale_id;
    end if;
    if current_status <> 'PENDIENTE' then
        raise exception 'SALE_TRANSITION_INVALID' using errcode = '22023';
    end if;

    update public.sales
    set status = 'CERRADO',
        paid_at = coalesce(target_paid_at, now()),
        updated_at = now()
    where id = target_sale_id and business_id = target_business_id;

    return target_sale_id;
end;
$$;

-- Las proyecciones de stock y el historial financiero solo se escriben por RPC.
revoke insert, update on public.products from authenticated;
revoke insert on public.sales from authenticated;
revoke insert on public.sale_items from authenticated;
revoke insert on public.stock_movements from authenticated;

revoke all on function public.upsert_product_metadata(jsonb) from public;
revoke all on function public.confirm_sale_bundle(jsonb) from public;
revoke all on function public.apply_stock_movement(jsonb) from public;
revoke all on function public.cancel_sale_bundle(jsonb) from public;

grant execute on function public.upsert_product_metadata(jsonb) to authenticated;
grant execute on function public.confirm_sale_bundle(jsonb) to authenticated;
grant execute on function public.apply_stock_movement(jsonb) to authenticated;
grant execute on function public.cancel_sale_bundle(jsonb) to authenticated;

commit;
