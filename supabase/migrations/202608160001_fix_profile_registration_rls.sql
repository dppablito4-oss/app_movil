-- Permite que clientes autenticados antiguos que todavía usan UPSERT creen
-- únicamente su propio perfil. Las versiones nuevas actualizan la fila que
-- handle_new_user() crea al registrar auth.users.
begin;

grant insert on table public.profiles to authenticated;

drop policy if exists profiles_insert_own on public.profiles;

create policy profiles_insert_own
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = id);

commit;
