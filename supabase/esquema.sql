-- =====================================================================
--  Despertar Equipos (Wake on LAN) — esquema de Supabase
--  Pegar entero en Supabase > SQL Editor > Run.
--
--  Idea: nadie toca las tablas directamente. Todo pasa por funciones
--  (RPC) que comprueban la "clave de red". Así la web, el agente de
--  Windows y la app Android solo necesitan la URL, la anon key y la clave.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------- Tablas ----------------------------------------------------

create table if not exists public.redes (
  id         uuid primary key default gen_random_uuid(),
  nombre     text not null,
  clave_hash text not null,              -- crypt(clave, gen_salt('bf'))
  creada     timestamptz not null default now()
);

create table if not exists public.equipos (
  id           uuid primary key default gen_random_uuid(),
  red_id       uuid not null references public.redes(id) on delete cascade,
  nombre       text not null,
  mac          text not null,            -- formato AA:BB:CC:DD:EE:FF
  ip_local     text,
  mascara      text,
  broadcast    text,
  ip_publica   text,
  so           text,
  usuario      text,
  ultimo_visto timestamptz not null default now(),
  creado       timestamptz not null default now(),
  unique (red_id, mac)
);

create table if not exists public.ordenes (
  id          uuid primary key default gen_random_uuid(),
  red_id      uuid not null references public.redes(id) on delete cascade,
  equipo_id   uuid not null references public.equipos(id) on delete cascade,
  estado      text not null default 'pendiente',   -- pendiente | enviada | despierto | caducada
  pedida_por  text,
  enviada_por text,
  creada      timestamptz not null default now(),
  enviada     timestamptz
);

create index if not exists ordenes_pendientes_idx on public.ordenes (red_id, estado);

-- RLS activado y SIN políticas: la anon key no puede leer ni escribir
-- las tablas; solo las funciones de abajo (security definer).
alter table public.redes   enable row level security;
alter table public.equipos enable row level security;
alter table public.ordenes enable row level security;

-- ---------- Funciones internas ---------------------------------------

create or replace function public._red_por_clave(p_clave text)
returns uuid
language sql stable security definer set search_path = public, extensions
as $$
  select id from public.redes
  where clave_hash = crypt(p_clave, clave_hash)
  limit 1;
$$;

create or replace function public._normaliza_mac(p_mac text)
returns text language sql immutable as $$
  select upper(regexp_replace(
           regexp_replace(coalesce(p_mac,''), '[^0-9A-Fa-f]', '', 'g'),
           '(..)(?!$)', '\1:', 'g'));
$$;

-- ---------- Funciones públicas (RPC) ---------------------------------

-- Crea una red nueva. Solo se usa una vez desde la web.
create or replace function public.crear_red(p_nombre text, p_clave text)
returns uuid
language plpgsql security definer set search_path = public, extensions
as $$
declare v_id uuid;
begin
  if length(coalesce(p_clave,'')) < 8 then
    raise exception 'La clave debe tener al menos 8 caracteres';
  end if;
  if public._red_por_clave(p_clave) is not null then
    raise exception 'Esa clave ya está en uso';
  end if;
  insert into public.redes(nombre, clave_hash)
  values (p_nombre, crypt(p_clave, gen_salt('bf')))
  returning id into v_id;
  return v_id;
end $$;

-- Devuelve el nombre de la red (sirve para comprobar la clave).
create or replace function public.entrar_red(p_clave text)
returns text
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid; v_nombre text;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  select nombre into v_nombre from public.redes where id = v_red;
  return v_nombre;
end $$;

-- El agente de Windows llama a esto cada pocos segundos (latido).
create or replace function public.reportar_equipo(
  p_clave text, p_nombre text, p_mac text, p_ip_local text,
  p_mascara text, p_broadcast text, p_so text default null,
  p_usuario text default null)
returns uuid
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid; v_id uuid; v_mac text; v_ip_pub text;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  v_mac := public._normaliza_mac(p_mac);
  if length(v_mac) <> 17 then raise exception 'MAC no válida: %', p_mac; end if;

  begin
    v_ip_pub := split_part(
      current_setting('request.headers', true)::json->>'x-forwarded-for', ',', 1);
  exception when others then v_ip_pub := null;
  end;

  insert into public.equipos(red_id, nombre, mac, ip_local, mascara, broadcast,
                             ip_publica, so, usuario, ultimo_visto)
  values (v_red, p_nombre, v_mac, p_ip_local, p_mascara, p_broadcast,
          nullif(trim(v_ip_pub),''), p_so, p_usuario, now())
  on conflict (red_id, mac) do update set
    nombre = excluded.nombre, ip_local = excluded.ip_local,
    mascara = excluded.mascara, broadcast = excluded.broadcast,
    ip_publica = coalesce(excluded.ip_publica, equipos.ip_publica),
    so = excluded.so, usuario = excluded.usuario, ultimo_visto = now()
  returning id into v_id;

  -- Si había órdenes para este equipo, ya está despierto.
  update public.ordenes set estado = 'despierto'
  where equipo_id = v_id and estado in ('pendiente','enviada');

  return v_id;
end $$;

-- Lista de equipos para la web y la app.
create or replace function public.listar_equipos(p_clave text)
returns table (id uuid, nombre text, mac text, ip_local text, mascara text,
               broadcast text, ip_publica text, so text, usuario text,
               ultimo_visto timestamptz, encendido boolean, orden_estado text)
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  return query
    select e.id, e.nombre, e.mac, e.ip_local, e.mascara, e.broadcast,
           e.ip_publica, e.so, e.usuario, e.ultimo_visto,
           (e.ultimo_visto > now() - interval '90 seconds') as encendido,
           (select o.estado from public.ordenes o
             where o.equipo_id = e.id and o.creada > now() - interval '10 minutes'
             order by o.creada desc limit 1) as orden_estado
    from public.equipos e
    where e.red_id = v_red
    order by e.nombre;
end $$;

-- Pide despertar un equipo. Cualquier agente encendido de la misma red
-- (o la app Android en la misma Wi-Fi) enviará el paquete mágico.
create or replace function public.pedir_despertar(
  p_clave text, p_equipo uuid, p_quien text default 'web')
returns uuid
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid; v_id uuid;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  if not exists (select 1 from public.equipos where id = p_equipo and red_id = v_red) then
    raise exception 'Equipo no encontrado';
  end if;
  insert into public.ordenes(red_id, equipo_id, pedida_por)
  values (v_red, p_equipo, p_quien)
  returning id into v_id;
  return v_id;
end $$;

-- Los agentes recogen las órdenes pendientes (menos de 10 min).
create or replace function public.ordenes_pendientes(p_clave text)
returns table (orden_id uuid, equipo_id uuid, nombre text, mac text,
               broadcast text, ip_local text)
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;

  update public.ordenes set estado = 'caducada'
  where red_id = v_red and estado in ('pendiente','enviada')
    and creada < now() - interval '10 minutes';

  return query
    select o.id, e.id, e.nombre, e.mac, e.broadcast, e.ip_local
    from public.ordenes o join public.equipos e on e.id = o.equipo_id
    where o.red_id = v_red and o.estado in ('pendiente','enviada');
end $$;

-- El agente avisa de que ya ha enviado el paquete mágico.
create or replace function public.marcar_enviada(
  p_clave text, p_orden uuid, p_quien text)
returns void
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  update public.ordenes
     set estado = case when estado = 'pendiente' then 'enviada' else estado end,
         enviada_por = p_quien, enviada = now()
   where id = p_orden and red_id = v_red;
end $$;

-- Borrar un equipo desde la web.
create or replace function public.borrar_equipo(p_clave text, p_equipo uuid)
returns void
language plpgsql security definer set search_path = public, extensions
as $$
declare v_red uuid;
begin
  v_red := public._red_por_clave(p_clave);
  if v_red is null then raise exception 'Clave incorrecta'; end if;
  delete from public.equipos where id = p_equipo and red_id = v_red;
end $$;

-- Permisos: la anon key solo puede ejecutar las funciones públicas.
revoke all on function public._red_por_clave(text) from public, anon, authenticated;
grant execute on function public.crear_red(text,text)            to anon, authenticated;
grant execute on function public.entrar_red(text)                to anon, authenticated;
grant execute on function public.reportar_equipo(text,text,text,text,text,text,text,text) to anon, authenticated;
grant execute on function public.listar_equipos(text)            to anon, authenticated;
grant execute on function public.pedir_despertar(text,uuid,text) to anon, authenticated;
grant execute on function public.ordenes_pendientes(text)        to anon, authenticated;
grant execute on function public.marcar_enviada(text,uuid,text)  to anon, authenticated;
grant execute on function public.borrar_equipo(text,uuid)        to anon, authenticated;
