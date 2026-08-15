-- SpaceSale Auth 2.0: perfil y recursos visuales del negocio.
alter table public.businesses
    add column if not exists logo_path text;

create or replace function public.set_business_logo(
    target_business_id uuid,
    target_logo_path text
)
returns void
language plpgsql
security definer
set search_path = public, private
as $$
begin
    if not private.can_manage_business(target_business_id) then
        raise exception 'No autorizado para modificar este negocio';
    end if;
    update public.businesses
    set logo_path = nullif(trim(target_logo_path), '')
    where id = target_business_id;
end;
$$;

revoke all on function public.set_business_logo(uuid, text) from public;
grant execute on function public.set_business_logo(uuid, text) to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'business-assets',
    'business-assets',
    false,
    2097152,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "business_assets_read" on storage.objects;
create policy "business_assets_read"
on storage.objects for select to authenticated
using (
    bucket_id = 'business-assets'
    and private.is_business_member((storage.foldername(name))[1]::uuid)
);

drop policy if exists "business_assets_insert" on storage.objects;
create policy "business_assets_insert"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'business-assets'
    and private.can_manage_business((storage.foldername(name))[1]::uuid)
);

drop policy if exists "business_assets_update" on storage.objects;
create policy "business_assets_update"
on storage.objects for update to authenticated
using (
    bucket_id = 'business-assets'
    and private.can_manage_business((storage.foldername(name))[1]::uuid)
)
with check (
    bucket_id = 'business-assets'
    and private.can_manage_business((storage.foldername(name))[1]::uuid)
);

drop policy if exists "business_assets_delete" on storage.objects;
create policy "business_assets_delete"
on storage.objects for delete to authenticated
using (
    bucket_id = 'business-assets'
    and private.can_manage_business((storage.foldername(name))[1]::uuid)
);
