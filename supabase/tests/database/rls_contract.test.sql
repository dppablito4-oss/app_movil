begin;

create extension if not exists pgtap with schema extensions;
select plan(12);

select ok((select relrowsecurity from pg_class where oid = 'public.businesses'::regclass), 'businesses has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.products'::regclass), 'products has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.customers'::regclass), 'customers has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.sales'::regclass), 'sales has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.sale_items'::regclass), 'sale_items has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.credit_payments'::regclass), 'credit_payments has RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.stock_movements'::regclass), 'stock_movements has RLS');
select ok((select relrowsecurity from pg_class where oid = 'storage.objects'::regclass), 'storage objects has RLS');

select ok(
    pg_get_functiondef('private.can_write_business(uuid)'::regprocedure) like '%owner%admin%staff%',
    'owner, admin and staff can write'
);
select ok(
    pg_get_functiondef('private.can_write_business(uuid)'::regprocedure) not like '%viewer%',
    'viewer is excluded from writes'
);
select ok(
    (select count(*) >= 4 from pg_policies where schemaname = 'public' and policyname like '%_writer'),
    'catalog and financial writer policies exist'
);
select ok(
    (select count(*) >= 3 from pg_policies where schemaname = 'storage' and policyname like 'product_images_%_writer'),
    'private product image writer policies exist'
);

select * from finish();
rollback;
