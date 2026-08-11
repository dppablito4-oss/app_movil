-- Permite que el propietario lea el negocio que acaba de crear incluso antes
-- de que la política basada en business_members pueda observar el trigger
-- AFTER INSERT. Conserva el acceso de los demás miembros.

begin;

drop policy if exists businesses_select_member on public.businesses;
drop policy if exists businesses_select_member_or_owner on public.businesses;

create policy businesses_select_member_or_owner
on public.businesses for select to authenticated
using (
    owner_id = (select auth.uid())
    or private.is_business_member(id)
);

commit;
