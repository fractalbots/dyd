-- =====================================================================
-- DYD Contable · Esquema de base de datos para Supabase (Postgres 15+)
-- Ejecutar completo en: Supabase > SQL Editor > New query > Run
-- Montos en centavos (bigint). Cantidades de inventario en numeric.
-- =====================================================================

-- ---------- Tipos ----------
create type public.app_role as enum ('admin', 'contador', 'vendedor', 'bodega', 'produccion', 'calidad', 'id', 'auditor', 'compras');
create type public.entry_type as enum ('INCOME', 'EXPENSE', 'TRANSFER');
create type public.account_type as enum ('CASH', 'BANK', 'CARD', 'OTHER');
create type public.contact_type as enum ('CLIENT', 'SUPPLIER', 'BOTH');
create type public.item_kind as enum ('MATERIAL', 'PRODUCT');
create type public.stock_move_type as enum ('PURCHASE', 'SALE', 'PRODUCTION_IN', 'PRODUCTION_OUT', 'ADJUSTMENT');
create type public.interaction_kind as enum ('CALL', 'VISIT', 'QUOTE', 'MESSAGE', 'OTHER');

-- ---------- Usuarios y roles ----------
-- Cada usuario de Supabase Auth tiene un perfil con su rol.
create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    full_name text not null default '',
    role public.app_role not null default 'vendedor',
    active boolean not null default true,
    created_at timestamptz not null default now()
);

-- Rol del usuario actual (null si no tiene perfil activo).
create or replace function public.current_role_name()
returns public.app_role
language sql stable security definer set search_path = public
as $$
    select role from public.profiles where id = auth.uid() and active
$$;

create or replace function public.has_role(variadic roles public.app_role[])
returns boolean
language sql stable security definer set search_path = public
as $$
    select coalesce(public.current_role_name() = any (roles), false)
$$;

-- Al registrarse un usuario se crea su perfil. El primero en registrarse es administrador.
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
    insert into public.profiles (id, full_name, role)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'full_name', split_part(new.email, '@', 1)),
        case when exists (select 1 from public.profiles) then 'vendedor'::public.app_role else 'admin'::public.app_role end
    );
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------- Contabilidad ----------
create table public.accounts (
    id bigint generated always as identity primary key,
    name text not null,
    type public.account_type not null default 'CASH',
    initial_balance bigint not null default 0,
    color bigint not null default 4278934859,
    archived boolean not null default false,
    created_at timestamptz not null default now()
);

create table public.categories (
    id bigint generated always as identity primary key,
    name text not null,
    type public.entry_type not null check (type in ('INCOME', 'EXPENSE')),
    icon text not null default 'label',
    color bigint not null default 4284513675
);

create table public.contacts (
    id bigint generated always as identity primary key,
    name text not null,
    type public.contact_type not null default 'CLIENT',
    tax_id text not null default '',
    phone text not null default '',
    email text not null default '',
    notes text not null default '',
    owner_id uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now()
);

create table public.entries (
    id bigint generated always as identity primary key,
    type public.entry_type not null,
    amount bigint not null check (amount > 0),
    date date not null default current_date,
    account_id bigint not null references public.accounts (id),
    to_account_id bigint references public.accounts (id),
    category_id bigint references public.categories (id) on delete set null,
    contact_id bigint references public.contacts (id) on delete set null,
    description text not null default '',
    reference text not null default '',
    is_paid boolean not null default true,
    due_date date,
    created_by uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now(),
    check (type <> 'TRANSFER' or (to_account_id is not null and to_account_id <> account_id and is_paid))
);
create index on public.entries (date);
create index on public.entries (account_id);
create index on public.entries (to_account_id);
create index on public.entries (category_id);
create index on public.entries (contact_id);

-- ---------- Inventario y fabricación ----------
create table public.items (
    id bigint generated always as identity primary key,
    name text not null,
    kind public.item_kind not null,
    unit text not null default 'und',
    sku text not null default '',
    unit_cost bigint not null default 0,          -- costo promedio ponderado
    sale_price bigint not null default 0,
    min_stock numeric(14, 3) not null default 0,
    labor_cost bigint not null default 0,         -- mano de obra por unidad
    overhead_cost bigint not null default 0,      -- costos indirectos por unidad
    archived boolean not null default false,
    -- Catálogo web
    is_public boolean not null default false,
    description text not null default '',
    dimensions text not null default '',
    image_url text not null default '',
    created_at timestamptz not null default now()
);

-- Lista de materiales (BOM): material por unidad de producto.
create table public.bom_lines (
    id bigint generated always as identity primary key,
    product_id bigint not null references public.items (id) on delete cascade,
    material_id bigint not null references public.items (id) on delete restrict,
    quantity numeric(14, 4) not null check (quantity > 0),
    waste_percent numeric(6, 2) not null default 0 check (waste_percent >= 0),
    unique (product_id, material_id),
    check (product_id <> material_id)
);

create table public.production_orders (
    id bigint generated always as identity primary key,
    product_id bigint not null references public.items (id),
    quantity numeric(14, 3) not null check (quantity > 0),
    date date not null default current_date,
    material_cost bigint not null,
    labor_cost bigint not null,
    overhead_cost bigint not null,
    note text not null default '',
    -- Control de calidad: PENDIENTE, APROBADO o RECHAZADO
    quality_status text not null default 'PENDIENTE' check (quality_status in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    quality_note text not null default '',
    inspected_by uuid references auth.users (id),
    inspected_at timestamptz,
    created_by uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now()
);

-- Kardex: cantidad positiva entra, negativa sale.
create table public.stock_moves (
    id bigint generated always as identity primary key,
    item_id bigint not null references public.items (id),
    type public.stock_move_type not null,
    quantity numeric(14, 3) not null check (quantity <> 0),
    unit_cost bigint not null default 0,
    unit_price bigint not null default 0,
    date date not null default current_date,
    entry_id bigint references public.entries (id) on delete cascade,
    production_id bigint references public.production_orders (id) on delete cascade,
    contact_id bigint references public.contacts (id) on delete set null,
    note text not null default '',
    created_by uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now()
);
create index on public.stock_moves (item_id);
create index on public.stock_moves (entry_id);
create index on public.stock_moves (production_id);
create index on public.stock_moves (contact_id);

-- ---------- CRM ----------
create table public.interactions (
    id bigint generated always as identity primary key,
    contact_id bigint not null references public.contacts (id) on delete cascade,
    kind public.interaction_kind not null default 'CALL',
    date date not null default current_date,
    note text not null default '',
    follow_up date,
    done boolean not null default false,
    created_by uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now()
);
create index on public.interactions (contact_id);

-- ---------- Vistas (respetan la seguridad del usuario que consulta) ----------
create view public.account_balances with (security_invoker = true) as
select a.*,
    a.initial_balance
    + coalesce((select sum(amount) from public.entries e where e.account_id = a.id and e.type = 'INCOME' and e.is_paid), 0)
    - coalesce((select sum(amount) from public.entries e where e.account_id = a.id and e.type in ('EXPENSE', 'TRANSFER') and e.is_paid), 0)
    + coalesce((select sum(amount) from public.entries e where e.to_account_id = a.id and e.type = 'TRANSFER'), 0)
    as balance
from public.accounts a;

create view public.item_stock with (security_invoker = true) as
select i.*,
    coalesce((select sum(quantity) from public.stock_moves s where s.item_id = i.id), 0) as stock
from public.items i;

create view public.contact_stats with (security_invoker = true) as
select c.id as contact_id,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'INCOME'), 0) as sales,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'EXPENSE'), 0) as expenses,
    coalesce((select round(sum(-s.quantity * s.unit_cost)) from public.stock_moves s where s.contact_id = c.id and s.type = 'SALE'), 0)::bigint as cogs,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'INCOME' and not e.is_paid), 0) as receivable,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'EXPENSE' and not e.is_paid), 0) as payable
from public.contacts c;

-- Catálogo público: solo productos marcados como visibles y solo columnas de vitrina.
-- Vista sin security_invoker a propósito: el público no tiene acceso a la tabla items.
create view public.catalog as
select i.id, i.name, i.sku, i.unit, i.sale_price, i.description, i.dimensions, i.image_url,
    greatest(coalesce((select sum(quantity) from public.stock_moves s where s.item_id = i.id), 0), 0) > 0 as in_stock
from public.items i
where i.is_public and not i.archived and i.kind = 'PRODUCT';

-- ---------- Lógica de negocio (atómica) ----------

-- Costo promedio ponderado tras una entrada de inventario.
create or replace function public.weighted_cost(stock_before numeric, cost_before bigint, qty_in numeric, cost_in bigint)
returns bigint
language sql immutable
as $$
    select case
        when greatest(stock_before, 0) + qty_in <= 0 then cost_in
        else round((greatest(stock_before, 0) * cost_before + qty_in * cost_in) / (greatest(stock_before, 0) + qty_in))::bigint
    end
$$;

-- Explosión de materiales: qué se necesita para fabricar p_quantity unidades.
create or replace function public.explode_bom(p_product_id bigint, p_quantity numeric)
returns table (
    material_id bigint, material_name text, unit text,
    required numeric, available numeric, shortage numeric, unit_cost bigint, cost bigint
)
language sql stable security invoker set search_path = public
as $$
    select m.id, m.name, m.unit,
        round(b.quantity * (1 + b.waste_percent / 100) * p_quantity, 3) as required,
        coalesce(st.stock, 0) as available,
        greatest(round(b.quantity * (1 + b.waste_percent / 100) * p_quantity, 3) - coalesce(st.stock, 0), 0) as shortage,
        m.unit_cost,
        round(b.quantity * (1 + b.waste_percent / 100) * p_quantity * m.unit_cost)::bigint as cost
    from public.bom_lines b
    join public.items m on m.id = b.material_id
    left join (select item_id, sum(quantity) as stock from public.stock_moves group by item_id) st on st.item_id = m.id
    where b.product_id = p_product_id
    order by m.name
$$;

-- >>> produccion
-- Fabricar: consume materiales, suma producto terminado y recalcula su costo. Todo o nada.
-- Corre con permisos del sistema para que el rol producción no necesite editar costos ni kardex directamente.
create or replace function public.produce(p_product_id bigint, p_quantity numeric, p_date date default current_date, p_note text default '')
returns bigint
language plpgsql security definer set search_path = public
as $$
declare
    v_product public.items;
    v_row record;
    v_material_cost bigint := 0;
    v_labor bigint;
    v_overhead bigint;
    v_unit_cost bigint;
    v_stock_before numeric;
    v_order_id bigint;
begin
    if not public.perm_produce() then
        raise exception 'No tienes permiso para registrar producción';
    end if;
    if p_quantity is null or p_quantity <= 0 then
        raise exception 'La cantidad debe ser mayor que cero';
    end if;
    select * into v_product from public.items where id = p_product_id and kind = 'PRODUCT' for update;
    if not found then
        raise exception 'Producto no encontrado';
    end if;
    if not exists (select 1 from public.bom_lines where product_id = p_product_id) then
        raise exception 'El producto no tiene lista de materiales';
    end if;
    if exists (select 1 from public.explode_bom(p_product_id, p_quantity) where shortage > 0) then
        raise exception 'Material insuficiente para fabricar % unidades', p_quantity;
    end if;

    select coalesce(sum(cost), 0) into v_material_cost from public.explode_bom(p_product_id, p_quantity);
    v_labor := round(v_product.labor_cost * p_quantity);
    v_overhead := round(v_product.overhead_cost * p_quantity);
    v_unit_cost := round((v_material_cost + v_labor + v_overhead) / p_quantity);

    insert into public.production_orders (product_id, quantity, date, material_cost, labor_cost, overhead_cost, note)
    values (p_product_id, p_quantity, p_date, v_material_cost, v_labor, v_overhead, coalesce(p_note, ''))
    returning id into v_order_id;

    for v_row in select * from public.explode_bom(p_product_id, p_quantity) loop
        insert into public.stock_moves (item_id, type, quantity, unit_cost, date, production_id, note)
        values (v_row.material_id, 'PRODUCTION_OUT', -v_row.required, v_row.unit_cost, p_date, v_order_id,
                'Consumo para ' || v_product.name);
    end loop;

    select coalesce(sum(quantity), 0) into v_stock_before from public.stock_moves where item_id = p_product_id;
    insert into public.stock_moves (item_id, type, quantity, unit_cost, date, production_id, note)
    values (p_product_id, 'PRODUCTION_IN', p_quantity, v_unit_cost, p_date, v_order_id, 'Producción');
    update public.items
    set unit_cost = public.weighted_cost(v_stock_before, v_product.unit_cost, p_quantity, v_unit_cost)
    where id = p_product_id;

    return v_order_id;
end;
$$;

-- Control de calidad de una orden de producción.
create or replace function public.inspect_production(p_order_id bigint, p_status text, p_note text default '')
returns void
language plpgsql security definer set search_path = public
as $$
begin
    if not public.perm_quality() then
        raise exception 'No tienes permiso para registrar control de calidad';
    end if;
    if p_status not in ('PENDIENTE', 'APROBADO', 'RECHAZADO') then
        raise exception 'Estado de calidad no válido';
    end if;
    update public.production_orders
    set quality_status = p_status, quality_note = coalesce(p_note, ''),
        inspected_by = case when p_status = 'PENDIENTE' then null else auth.uid() end,
        inspected_at = case when p_status = 'PENDIENTE' then null else now() end
    where id = p_order_id;
    if not found then
        raise exception 'Orden de producción no encontrada';
    end if;
end;
$$;
-- <<< produccion

-- Actualiza el costo promedio cuando entra una compra.
create or replace function public.on_purchase_move()
returns trigger
language plpgsql security definer set search_path = public
as $$
declare
    v_stock_before numeric;
    v_cost bigint;
begin
    if new.type = 'PURCHASE' and new.quantity > 0 then
        select coalesce(sum(quantity), 0) into v_stock_before from public.stock_moves where item_id = new.item_id and id <> new.id;
        select unit_cost into v_cost from public.items where id = new.item_id;
        update public.items set unit_cost = public.weighted_cost(v_stock_before, v_cost, new.quantity, new.unit_cost)
        where id = new.item_id;
    end if;
    return new;
end;
$$;

create trigger stock_moves_purchase_cost
    after insert on public.stock_moves
    for each row execute function public.on_purchase_move();

-- >>> entrada
-- Guarda un movimiento contable y, si aplica, su salida (venta) o entrada (compra) de inventario.
-- p_entry usa los nombres de columna de entries; sin "id" (o id 0) crea, con "id" actualiza.
create or replace function public.save_entry(p_entry jsonb, p_item_id bigint default null, p_quantity numeric default null)
returns bigint
language plpgsql security invoker set search_path = public
as $$
declare
    v_id bigint := nullif(nullif(p_entry ->> 'id', ''), '0')::bigint;
    v_type public.entry_type := (p_entry ->> 'type')::public.entry_type;
    v_amount bigint := (p_entry ->> 'amount')::bigint;
    v_tax bigint := coalesce(nullif(p_entry ->> 'tax_amount', '')::bigint, 0);  -- IVA incluido en el monto
    v_date date := coalesce((p_entry ->> 'date')::date, current_date);
    v_contact bigint := nullif(p_entry ->> 'contact_id', '')::bigint;
    v_cost bigint;
begin
    if v_id is null then
        insert into public.entries (type, amount, tax_amount, date, account_id, to_account_id, category_id, contact_id,
                                    description, reference, is_paid, due_date)
        values (v_type, v_amount, v_tax, v_date, (p_entry ->> 'account_id')::bigint,
                nullif(p_entry ->> 'to_account_id', '')::bigint, nullif(p_entry ->> 'category_id', '')::bigint, v_contact,
                coalesce(p_entry ->> 'description', ''), coalesce(p_entry ->> 'reference', ''),
                coalesce((p_entry ->> 'is_paid')::boolean, true), nullif(p_entry ->> 'due_date', '')::date)
        returning id into v_id;
    else
        update public.entries set
            type = v_type, amount = v_amount, tax_amount = v_tax, date = v_date,
            account_id = (p_entry ->> 'account_id')::bigint,
            to_account_id = nullif(p_entry ->> 'to_account_id', '')::bigint,
            category_id = nullif(p_entry ->> 'category_id', '')::bigint,
            contact_id = v_contact,
            description = coalesce(p_entry ->> 'description', ''),
            reference = coalesce(p_entry ->> 'reference', ''),
            is_paid = coalesce((p_entry ->> 'is_paid')::boolean, true),
            due_date = nullif(p_entry ->> 'due_date', '')::date
        where id = v_id;
        if not found then
            raise exception 'Movimiento no encontrado o sin permiso para editarlo';
        end if;
        delete from public.stock_moves where entry_id = v_id;
    end if;

    if p_item_id is not null and coalesce(p_quantity, 0) > 0 and v_type <> 'TRANSFER' then
        if v_type = 'INCOME' then
            select unit_cost into v_cost from public.items where id = p_item_id;
            insert into public.stock_moves (item_id, type, quantity, unit_cost, unit_price, date, entry_id, contact_id, note)
            values (p_item_id, 'SALE', -p_quantity, coalesce(v_cost, 0), round((v_amount - v_tax) / p_quantity), v_date, v_id, v_contact, 'Venta');
        else
            insert into public.stock_moves (item_id, type, quantity, unit_cost, date, entry_id, contact_id, note)
            -- El costo no incluye el IVA: el IVA pagado es crédito tributario.
            values (p_item_id, 'PURCHASE', p_quantity, round((v_amount - v_tax) / p_quantity), v_date, v_id, v_contact, 'Compra');
        end if;
    end if;
    return v_id;
end;
$$;
-- <<< entrada

-- ---------- Seguridad por filas (RLS) ----------
alter table public.profiles enable row level security;
alter table public.accounts enable row level security;
alter table public.categories enable row level security;
alter table public.contacts enable row level security;
alter table public.entries enable row level security;
alter table public.items enable row level security;
alter table public.bom_lines enable row level security;
alter table public.production_orders enable row level security;
alter table public.stock_moves enable row level security;
alter table public.interactions enable row level security;

-- >>> permisos
-- Qué puede hacer cada rol. Cambia las listas aquí para ajustar permisos.
-- Se comparan como texto para que este bloque funcione también al actualizar una base existente.
create or replace function public.role_in(variadic roles text[])
returns boolean
language sql stable security definer set search_path = public
as $$
    select coalesce(public.current_role_name()::text = any (roles), false)
$$;

-- Cualquier usuario activo: ver inventario, materiales, producción y kardex.
create or replace function public.perm_team() returns boolean language sql stable as $$
    select public.current_role_name() is not null $$;
-- Ver contabilidad (movimientos de todos, saldos, reportes).
create or replace function public.perm_accounting_read() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'auditor') $$;
-- Registrar y editar contabilidad.
create or replace function public.perm_accounting_write() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador') $$;
-- Ver cuentas y categorías (para elegirlas al registrar una venta o compra).
create or replace function public.perm_accounts_read() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'vendedor', 'auditor', 'compras') $$;
-- Ver clientes y proveedores.
create or replace function public.perm_crm_read() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'vendedor', 'auditor', 'compras') $$;
-- Crear y editar clientes (con su RUC o cédula): contabilidad. Compras solo crea proveedores.
create or replace function public.perm_contacts_write() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador') $$;
-- Registrar seguimientos (llamadas, visitas, cotizaciones).
create or replace function public.perm_crm_write() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'vendedor', 'compras') $$;
-- Registrar compras a proveedores.
create or replace function public.perm_purchases() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'compras') $$;
-- Emitir facturas electrónicas.
create or replace function public.perm_invoice() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'vendedor') $$;
-- Crear y editar productos, materiales, lista de materiales y fotos del catálogo.
create or replace function public.perm_items_write() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'id') $$;
-- Registrar órdenes de producción.
create or replace function public.perm_produce() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'produccion') $$;
-- Entradas y salidas manuales de bodega (ajustes de inventario).
create or replace function public.perm_stock_adjust() returns boolean language sql stable as $$
    select public.role_in('admin', 'contador', 'bodega') $$;
-- Aprobar o rechazar producción.
create or replace function public.perm_quality() returns boolean language sql stable as $$
    select public.role_in('admin', 'calidad') $$;
-- Ver la lista de usuarios.
create or replace function public.perm_users_read() returns boolean language sql stable as $$
    select public.role_in('admin', 'auditor') $$;

-- Quita las reglas anteriores para poder volver a ejecutar este bloque.
do $$
declare r record;
begin
    for r in select policyname, tablename from pg_policies
             where schemaname = 'public' and tablename in
               ('profiles', 'accounts', 'categories', 'contacts', 'entries', 'items', 'bom_lines',
                'production_orders', 'stock_moves', 'interactions')
    loop
        execute format('drop policy %I on public.%I', r.policyname, r.tablename);
    end loop;
    for r in select policyname from pg_policies
             where schemaname = 'storage' and tablename = 'objects' and policyname like '%fotos catalogo%'
    loop
        execute format('drop policy %I on storage.objects', r.policyname);
    end loop;
end $$;

-- Perfiles: cada uno ve el suyo; admin y auditor ven todos; solo el admin los edita.
create policy "ver perfiles" on public.profiles for select to authenticated
    using (id = auth.uid() or public.perm_users_read());
create policy "admin gestiona perfiles" on public.profiles for update to authenticated
    using (public.role_in('admin')) with check (public.role_in('admin'));

-- Cuentas y categorías.
create policy "leer cuentas" on public.accounts for select to authenticated using (public.perm_accounts_read());
create policy "gestionar cuentas" on public.accounts for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
create policy "leer categorias" on public.categories for select to authenticated using (public.perm_accounts_read());
create policy "gestionar categorias" on public.categories for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());

-- Movimientos contables. El vendedor solo registra y ve sus propias ventas.
create policy "leer contabilidad" on public.entries for select to authenticated using (public.perm_accounting_read());
create policy "gestionar contabilidad" on public.entries for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
create policy "vendedor ve sus ventas" on public.entries for select to authenticated
    using (public.role_in('vendedor') and created_by = auth.uid());
create policy "vendedor registra ventas" on public.entries for insert to authenticated
    with check (public.role_in('vendedor') and type = 'INCOME' and created_by = auth.uid());
create policy "compras ve sus compras" on public.entries for select to authenticated
    using (public.role_in('compras') and created_by = auth.uid());
create policy "compras registra compras" on public.entries for insert to authenticated
    with check (public.role_in('compras') and type = 'EXPENSE' and created_by = auth.uid());

-- Clientes y seguimientos.
create policy "leer contactos" on public.contacts for select to authenticated using (public.perm_crm_read());
create policy "gestionar contactos" on public.contacts for all to authenticated
    using (public.perm_contacts_write()) with check (public.perm_contacts_write());
create policy "compras gestiona proveedores" on public.contacts for all to authenticated
    using (public.role_in('compras') and type in ('SUPPLIER', 'BOTH'))
    with check (public.role_in('compras') and type in ('SUPPLIER', 'BOTH'));
create policy "leer seguimientos" on public.interactions for select to authenticated using (public.perm_crm_read());
create policy "gestionar seguimientos" on public.interactions for all to authenticated
    using (public.perm_crm_write()) with check (public.perm_crm_write());

-- Inventario: todo el equipo lo ve.
create policy "leer articulos" on public.items for select to authenticated using (public.perm_team());
create policy "gestionar articulos" on public.items for all to authenticated
    using (public.perm_items_write()) with check (public.perm_items_write());
create policy "leer bom" on public.bom_lines for select to authenticated using (public.perm_team());
create policy "gestionar bom" on public.bom_lines for all to authenticated
    using (public.perm_items_write()) with check (public.perm_items_write());
-- La producción se registra con produce() y la calidad con inspect_production().
create policy "leer produccion" on public.production_orders for select to authenticated using (public.perm_team());
create policy "gestionar produccion" on public.production_orders for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
create policy "leer kardex" on public.stock_moves for select to authenticated using (public.perm_team());
create policy "gestionar kardex" on public.stock_moves for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
create policy "bodega registra ajustes" on public.stock_moves for insert to authenticated
    with check (public.perm_stock_adjust() and type = 'ADJUSTMENT' and entry_id is null and production_id is null);
create policy "vendedor registra salidas" on public.stock_moves for insert to authenticated
    with check (public.role_in('vendedor') and type = 'SALE' and quantity < 0);
create policy "compras registra entradas" on public.stock_moves for insert to authenticated
    with check (public.role_in('compras') and type = 'PURCHASE' and quantity > 0);

-- El público (sin login) solo puede leer el catálogo.
revoke all on public.catalog from anon;
grant select on public.catalog to anon, authenticated;

-- Fotos del catálogo.
insert into storage.buckets (id, name, public) values ('catalogo', 'catalogo', true)
on conflict (id) do nothing;
create policy "subir fotos catalogo" on storage.objects for insert to authenticated
    with check (bucket_id = 'catalogo' and public.perm_items_write());
create policy "cambiar fotos catalogo" on storage.objects for update to authenticated
    using (bucket_id = 'catalogo' and public.perm_items_write());
create policy "borrar fotos catalogo" on storage.objects for delete to authenticated
    using (bucket_id = 'catalogo' and public.perm_items_write());
-- <<< permisos

-- ---------- Datos iniciales para un negocio de pallets ----------
insert into public.accounts (name, type, color) values
    ('Caja', 'CASH', 4278934859), ('Banco', 'BANK', 4279592384);

insert into public.categories (name, type, icon, color) values
    ('Venta de pallets', 'INCOME', 'pallet', 4281236786),
    ('Reparación de pallets', 'INCOME', 'build', 4278225275),
    ('Servicio de transporte', 'INCOME', 'truck', 4278351805),
    ('Otros ingresos', 'INCOME', 'savings', 4286362434),
    ('Madera', 'EXPENSE', 'forest', 4287458915),
    ('Clavos e insumos', 'EXPENSE', 'hardware', 4283723386),
    ('Nómina', 'EXPENSE', 'people', 4284364209),
    ('Transporte y combustible', 'EXPENSE', 'truck', 4293880832),
    ('Arriendo', 'EXPENSE', 'home', 4291176488),
    ('Servicios públicos', 'EXPENSE', 'bolt', 4294551589),
    ('Mantenimiento', 'EXPENSE', 'build', 4285353025),
    ('Impuestos', 'EXPENSE', 'gavel', 4281812815),
    ('Otros gastos', 'EXPENSE', 'label', 4286091420);

with m as (
    insert into public.items (name, kind, unit) values
        ('Tabla de pino 1,20 m', 'MATERIAL', 'und'),
        ('Larguero 1,00 m', 'MATERIAL', 'und'),
        ('Taco de madera', 'MATERIAL', 'und'),
        ('Clavos', 'MATERIAL', 'kg')
    returning id, name
), p as (
    insert into public.items (name, kind, unit, is_public, description, dimensions)
    values ('Pallet estándar', 'PRODUCT', 'und', true, 'Pallet de madera de pino para carga general.', '1,20 × 1,00 m')
    returning id
)
insert into public.bom_lines (product_id, material_id, quantity, waste_percent)
select p.id, m.id,
    case m.name when 'Tabla de pino 1,20 m' then 7 when 'Larguero 1,00 m' then 3 when 'Taco de madera' then 9 else 0.25 end,
    case m.name when 'Clavos' then 5 else 2 end
from m, p;

-- >>> ecuador
-- ---------- Ecuador: identificación, IVA, facturación electrónica SRI e intereses ----------
-- Bloque idempotente: sirve en una instalación nueva y para actualizar una base existente.

-- Validación de cédula (módulo 10) y RUC.
create or replace function public.ec_valid_cedula(p text) returns boolean
language plpgsql immutable as $$
declare s int := 0; d int; prov int;
begin
    if p is null or p !~ '^\d{10}$' then return false; end if;
    prov := substr(p, 1, 2)::int;
    if not (prov between 1 and 24 or prov = 30) then return false; end if;
    if substr(p, 3, 1)::int >= 6 then return false; end if;
    for i in 1..9 loop
        d := substr(p, i, 1)::int * case when i % 2 = 1 then 2 else 1 end;
        if d > 9 then d := d - 9; end if;
        s := s + d;
    end loop;
    return (10 - s % 10) % 10 = substr(p, 10, 1)::int;
end $$;

-- Persona natural: cédula válida + establecimiento. Sociedades (9) y entidades públicas (6):
-- se valida la estructura, porque el SRI ya no garantiza el dígito verificador en los RUC nuevos.
create or replace function public.ec_valid_ruc(p text) returns boolean
language plpgsql immutable as $$
declare t int; prov int;
begin
    if p is null or p !~ '^\d{13}$' then return false; end if;
    prov := substr(p, 1, 2)::int;
    if not (prov between 1 and 24 or prov = 30) then return false; end if;
    t := substr(p, 3, 1)::int;
    if t < 6 then return public.ec_valid_cedula(substr(p, 1, 10)) and substr(p, 11, 3) <> '000'; end if;
    if t = 6 then return substr(p, 10, 4) <> '0000'; end if;
    if t = 9 then return substr(p, 11, 3) <> '000'; end if;
    return false;
end $$;

-- Tipos de identificación del SRI: 04 RUC, 05 cédula, 06 pasaporte, 07 consumidor final, 08 exterior.
create or replace function public.ec_valid_id(p_type text, p_id text) returns boolean
language sql immutable as $$
    select case p_type
        when '04' then public.ec_valid_ruc(p_id)
        when '05' then public.ec_valid_cedula(p_id)
        when '06' then length(coalesce(p_id, '')) between 3 and 20
        when '07' then p_id = '9999999999999'
        when '08' then length(coalesce(p_id, '')) between 3 and 20
        else false end
$$;

-- Porcentaje de IVA según el código del SRI (tabla 17 de la ficha técnica).
create or replace function public.iva_rate(p_code text) returns numeric
language sql immutable as $$
    select case p_code when '4' then 15 when '5' then 5 else 0 end::numeric
$$;

-- Clientes y proveedores con identificación del SRI.
alter table public.contacts add column if not exists id_type text not null default '';
alter table public.contacts add column if not exists address text not null default '';
do $$ begin
    if not exists (select 1 from pg_constraint where conname = 'contacts_id_valida') then
        alter table public.contacts add constraint contacts_id_valida
            check (id_type = '' or public.ec_valid_id(id_type, tax_id));
    end if;
end $$;
create unique index if not exists contacts_tax_id_unico on public.contacts (tax_id)
    where tax_id <> '' and id_type <> '07';

-- IVA de cada artículo (4 = 15 %, 0 = 0 %, 5 = 5 %, 6 = no objeto, 7 = exento).
alter table public.items add column if not exists iva_code text not null default '4';
do $$ begin
    if not exists (select 1 from pg_constraint where conname = 'items_iva_code') then
        alter table public.items add constraint items_iva_code check (iva_code in ('0', '4', '5', '6', '7'));
    end if;
end $$;

-- Datos del emisor (una sola fila).
create table if not exists public.company (
    id int primary key default 1 check (id = 1),
    ruc text not null default '',
    razon_social text not null default '',
    nombre_comercial text not null default '',
    dir_matriz text not null default '',
    dir_establecimiento text not null default '',
    estab text not null default '001' check (estab ~ '^\d{3}$'),
    pto_emi text not null default '001' check (pto_emi ~ '^\d{3}$'),
    next_secuencial bigint not null default 1 check (next_secuencial between 1 and 999999999),
    ambiente smallint not null default 1 check (ambiente in (1, 2)),       -- 1 pruebas, 2 producción
    obligado_contabilidad boolean not null default false,
    contribuyente_especial text not null default '',
    agente_retencion text not null default '',
    regimen text not null default 'GENERAL' check (regimen in ('GENERAL', 'RIMPE_EMPRENDEDOR', 'RIMPE_NEGOCIO_POPULAR')),
    email text not null default '',
    phone text not null default '',
    late_interest_rate numeric(6, 3) not null default 0 check (late_interest_rate >= 0),  -- % anual por mora
    updated_at timestamptz not null default now()
);
insert into public.company (id) values (1) on conflict (id) do nothing;

-- Facturas electrónicas.
create table if not exists public.invoices (
    id bigint generated always as identity primary key,
    contact_id bigint not null references public.contacts (id),
    estab text not null,
    pto_emi text not null,
    secuencial bigint not null,
    fecha_emision date not null default current_date,
    ambiente smallint not null,
    clave_acceso text not null unique check (clave_acceso ~ '^\d{49}$'),
    -- PENDIENTE: creada, sin enviar · RECIBIDA/DEVUELTA: respuesta de recepción · AUTORIZADA/NO_AUTORIZADA · ANULADA
    status text not null default 'PENDIENTE'
        check (status in ('PENDIENTE', 'RECIBIDA', 'DEVUELTA', 'AUTORIZADA', 'NO_AUTORIZADA', 'ANULADA')),
    buyer_id_type text not null,
    buyer_id text not null,
    buyer_name text not null,
    buyer_address text not null default '',
    buyer_email text not null default '',
    subtotal bigint not null,          -- sin impuestos, después de descuentos
    discount bigint not null default 0,
    tax bigint not null,
    total bigint not null,
    payment_method text not null default '01',
    term_days int not null default 0 check (term_days >= 0),
    installments int not null default 1 check (installments between 1 and 60),
    finance_rate numeric(6, 3) not null default 0 check (finance_rate >= 0),  -- % anual por financiamiento
    account_id bigint references public.accounts (id),
    note text not null default '',
    xml text,
    authorization_number text,
    authorized_at timestamptz,
    sri_messages jsonb not null default '[]',
    created_by uuid references auth.users (id) default auth.uid(),
    created_at timestamptz not null default now(),
    unique (estab, pto_emi, secuencial, ambiente)
);
create index if not exists invoices_fecha on public.invoices (fecha_emision);
create index if not exists invoices_contact on public.invoices (contact_id);

create table if not exists public.invoice_lines (
    id bigint generated always as identity primary key,
    invoice_id bigint not null references public.invoices (id) on delete cascade,
    item_id bigint references public.items (id),
    code text not null default '',
    description text not null,
    quantity numeric(14, 3) not null check (quantity > 0),
    unit_price bigint not null check (unit_price >= 0),
    discount bigint not null default 0 check (discount >= 0),
    iva_code text not null,
    iva_rate numeric(5, 2) not null,
    subtotal bigint not null,
    tax bigint not null
);
create index if not exists invoice_lines_invoice on public.invoice_lines (invoice_id);

-- Cuotas de una venta a crédito (sistema francés).
create table if not exists public.installments (
    id bigint generated always as identity primary key,
    invoice_id bigint not null references public.invoices (id) on delete cascade,
    number int not null,
    due_date date not null,
    capital bigint not null,
    interest bigint not null,
    total bigint not null,
    unique (invoice_id, number)
);

-- Enlaces de contabilidad e inventario con la factura; intereses.
alter table public.entries add column if not exists tax_amount bigint not null default 0;      -- IVA incluido en amount
alter table public.entries add column if not exists invoice_id bigint references public.invoices (id) on delete set null;
alter table public.entries add column if not exists is_interest boolean not null default false;
alter table public.entries add column if not exists interest_until date;                        -- mora cobrada hasta
alter table public.stock_moves add column if not exists invoice_id bigint references public.invoices (id) on delete cascade;
do $$ begin
    if not exists (select 1 from pg_constraint where conname = 'entries_tax_amount') then
        alter table public.entries add constraint entries_tax_amount check (tax_amount >= 0 and tax_amount <= amount);
    end if;
end $$;
create index if not exists entries_invoice on public.entries (invoice_id);

-- Tasas de interés por mora tributaria que publica el SRI cada trimestre (% mensual).
create table if not exists public.sri_interest_rates (
    quarter_start date primary key
        check (extract(day from quarter_start) = 1 and extract(month from quarter_start) in (1, 4, 7, 10)),
    monthly_rate numeric(7, 4) not null check (monthly_rate >= 0)
);

-- Categorías que usan la facturación y los intereses.
insert into public.categories (name, type, icon, color)
select v.name, v.type::public.entry_type, v.icon, v.color
from (values ('Intereses ganados', 'INCOME', 'savings', 4281236786),
             ('Intereses y multas SRI', 'EXPENSE', 'gavel', 4291176488)) v(name, type, icon, color)
where not exists (select 1 from public.categories c where c.name = v.name);

-- Cliente genérico para ventas a consumidor final.
insert into public.contacts (name, type, tax_id, id_type)
select 'CONSUMIDOR FINAL', 'CLIENT', '9999999999999', '07'
where not exists (select 1 from public.contacts where id_type = '07');

-- ---------- Clave de acceso (49 dígitos, módulo 11) ----------
create or replace function public.sri_mod11(p text) returns int
language plpgsql immutable as $$
declare s int := 0; w int := 2; r int;
begin
    for i in reverse length(p)..1 loop
        s := s + substr(p, i, 1)::int * w;
        w := case when w = 7 then 2 else w + 1 end;
    end loop;
    r := 11 - s % 11;
    return case r when 11 then 0 when 10 then 1 else r end;
end $$;

create or replace function public.sri_clave_acceso(
    p_fecha date, p_tipo text, p_ruc text, p_ambiente int, p_serie text, p_secuencial bigint, p_codigo text
) returns text
language plpgsql immutable as $$
declare v text;
begin
    v := to_char(p_fecha, 'DDMMYYYY') || p_tipo || p_ruc || p_ambiente::text || p_serie
         || lpad(p_secuencial::text, 9, '0') || lpad(p_codigo, 8, '0') || '1';
    return v || public.sri_mod11(v)::text;
end $$;

-- ---------- Emitir factura ----------
-- p: contact_id, date, account_id, category_id, payment_method, term_days, installments, finance_rate, note,
--    lines: [{item_id, code, description, quantity, unit_price, discount, iva_code}]
create or replace function public.create_invoice(p jsonb) returns bigint
language plpgsql security definer set search_path = public
as $$
declare
    c public.company;
    k public.contacts;
    v_date date := coalesce(nullif(p ->> 'date', '')::date, current_date);
    v_inst int := greatest(coalesce(nullif(p ->> 'installments', '')::int, 1), 1);
    v_rate numeric := coalesce(nullif(p ->> 'finance_rate', '')::numeric, 0);
    v_term int := coalesce(nullif(p ->> 'term_days', '')::int, 0);
    v_account bigint := nullif(p ->> 'account_id', '')::bigint;
    v_category bigint := nullif(p ->> 'category_id', '')::bigint;
    v_interest_cat bigint;
    v_id bigint;
    v_sec bigint;
    l jsonb;
    v_item public.items;
    v_qty numeric; v_price bigint; v_disc bigint; v_code text; v_line_sub bigint;
    v_subtotal bigint := 0; v_discount bigint := 0; v_tax bigint := 0; v_total bigint;
    v_number text;
    -- cuotas
    r numeric; v_quota bigint; v_balance bigint; v_int bigint; v_cap bigint; v_tax_left bigint; v_tax_k bigint;
    v_entry bigint; v_due date;
begin
    if not public.perm_invoice() then
        raise exception 'No tienes permiso para emitir facturas';
    end if;
    select * into c from public.company where id = 1 for update;
    if not public.ec_valid_ruc(c.ruc) or c.razon_social = '' or c.dir_matriz = '' then
        raise exception 'Completa los datos de facturación de la empresa (RUC, razón social y dirección)';
    end if;
    select * into k from public.contacts where id = (p ->> 'contact_id')::bigint;
    if not found then raise exception 'Cliente no encontrado'; end if;
    if k.id_type = '' or not public.ec_valid_id(k.id_type, k.tax_id) then
        raise exception 'El cliente % no tiene un RUC, cédula o pasaporte válido', k.name;
    end if;
    if jsonb_array_length(coalesce(p -> 'lines', '[]')) = 0 then
        raise exception 'La factura no tiene productos';
    end if;
    if v_account is null then
        raise exception 'Elige la cuenta donde entra el dinero';
    end if;
    if v_inst > 1 and v_term = 0 then v_term := v_inst * 30; end if;

    v_sec := c.next_secuencial;
    update public.company set next_secuencial = next_secuencial + 1, updated_at = now() where id = 1;
    v_number := c.estab || '-' || c.pto_emi || '-' || lpad(v_sec::text, 9, '0');

    insert into public.invoices (contact_id, estab, pto_emi, secuencial, fecha_emision, ambiente, clave_acceso,
        buyer_id_type, buyer_id, buyer_name, buyer_address, buyer_email, subtotal, discount, tax, total,
        payment_method, term_days, installments, finance_rate, account_id, note)
    values (k.id, c.estab, c.pto_emi, v_sec, v_date, c.ambiente,
        public.sri_clave_acceso(v_date, '01', c.ruc, c.ambiente, c.estab || c.pto_emi, v_sec,
                                lpad((floor(random() * 100000000))::bigint::text, 8, '0')),
        k.id_type, k.tax_id, k.name, k.address, k.email, 0, 0, 0, 0,
        coalesce(nullif(p ->> 'payment_method', ''), '01'), v_term, v_inst, v_rate, v_account, coalesce(p ->> 'note', ''))
    returning id into v_id;

    for l in select * from jsonb_array_elements(p -> 'lines') loop
        v_item := null;
        if nullif(l ->> 'item_id', '') is not null then
            select * into v_item from public.items where id = (l ->> 'item_id')::bigint;
        end if;
        v_qty := (l ->> 'quantity')::numeric;
        v_price := (l ->> 'unit_price')::bigint;
        v_disc := coalesce(nullif(l ->> 'discount', '')::bigint, 0);
        v_code := coalesce(nullif(l ->> 'iva_code', ''), v_item.iva_code, '4');
        if v_qty is null or v_qty <= 0 then raise exception 'Cantidad no válida'; end if;
        v_line_sub := round(v_qty * v_price) - v_disc;
        if v_line_sub < 0 then raise exception 'El descuento supera el valor de la línea'; end if;
        insert into public.invoice_lines (invoice_id, item_id, code, description, quantity, unit_price, discount,
                                          iva_code, iva_rate, subtotal, tax)
        values (v_id, v_item.id, coalesce(nullif(l ->> 'code', ''), v_item.sku, ''),
                coalesce(nullif(l ->> 'description', ''), v_item.name, 'Producto'),
                v_qty, v_price, v_disc, v_code, public.iva_rate(v_code), v_line_sub,
                round(v_line_sub * public.iva_rate(v_code) / 100));
        v_subtotal := v_subtotal + v_line_sub;
        v_discount := v_discount + v_disc;
        if v_item.id is not null then
            insert into public.stock_moves (item_id, type, quantity, unit_cost, unit_price, date, contact_id, invoice_id, note)
            values (v_item.id, 'SALE', -v_qty, v_item.unit_cost, round(v_line_sub / v_qty), v_date, k.id, v_id,
                    'Factura ' || v_number);
        end if;
    end loop;

    -- IVA por tarifa, como lo valida el SRI en totalConImpuestos.
    select coalesce(sum(round(base * public.iva_rate(iva_code) / 100)), 0) into v_tax
    from (select iva_code, sum(subtotal) as base from public.invoice_lines where invoice_id = v_id group by iva_code) t;
    v_total := v_subtotal + v_tax;
    if k.id_type = '07' and v_total > 5000 then
        raise exception 'Una factura a consumidor final no puede superar USD 50,00. Registra los datos del cliente.';
    end if;
    update public.invoices set subtotal = v_subtotal, discount = v_discount, tax = v_tax, total = v_total where id = v_id;

    if v_category is null then
        select id into v_category from public.categories where type = 'INCOME' order by id limit 1;
    end if;

    if v_inst = 1 then
        insert into public.entries (type, amount, tax_amount, date, account_id, category_id, contact_id, description,
                                    reference, is_paid, due_date, invoice_id)
        values ('INCOME', v_total, v_tax, v_date, v_account, v_category, k.id, 'Factura ' || v_number,
                v_number, v_term = 0, case when v_term > 0 then v_date + v_term end, v_id);
    else
        -- Venta a crédito: cuotas con interés de financiamiento (sistema francés, tasa anual / 12).
        select id into v_interest_cat from public.categories where name = 'Intereses ganados' limit 1;
        r := v_rate / 1200;
        v_quota := case when r = 0 then ceil(v_total::numeric / v_inst)
                        else round(v_total * r / (1 - power(1 + r, -v_inst))) end;
        v_balance := v_total;
        v_tax_left := v_tax;
        for i in 1..v_inst loop
            v_int := round(v_balance * r);
            v_cap := case when i = v_inst then v_balance else least(v_quota - v_int, v_balance) end;
            v_tax_k := case when i = v_inst then v_tax_left else round(v_tax::numeric * v_cap / v_total) end;
            v_due := (v_date + make_interval(months => i))::date;
            insert into public.installments (invoice_id, number, due_date, capital, interest, total)
            values (v_id, i, v_due, v_cap, v_int, v_cap + v_int);
            if v_cap > 0 then
                insert into public.entries (type, amount, tax_amount, date, account_id, category_id, contact_id,
                                            description, reference, is_paid, due_date, invoice_id)
                values ('INCOME', v_cap, v_tax_k, v_date, v_account, v_category, k.id,
                        'Factura ' || v_number || ' · cuota ' || i || '/' || v_inst, v_number, false, v_due, v_id);
            end if;
            if v_int > 0 then
                insert into public.entries (type, amount, date, account_id, category_id, contact_id, description,
                                            reference, is_paid, due_date, invoice_id, is_interest)
                values ('INCOME', v_int, v_date, v_account, v_interest_cat, k.id,
                        'Interés de financiamiento · cuota ' || i || '/' || v_inst, v_number, false, v_due, v_id, true);
            end if;
            v_balance := v_balance - v_cap;
            v_tax_left := v_tax_left - v_tax_k;
        end loop;
    end if;
    return v_id;
end;
$$;

-- Anular: revierte contabilidad e inventario. (En el SRI la anulación se solicita en su portal.)
create or replace function public.void_invoice(p_id bigint) returns void
language plpgsql security definer set search_path = public
as $$
begin
    if not public.perm_accounting_write() then
        raise exception 'No tienes permiso para anular facturas';
    end if;
    update public.invoices set status = 'ANULADA' where id = p_id and status <> 'ANULADA';
    if not found then raise exception 'Factura no encontrada o ya anulada'; end if;
    delete from public.entries where invoice_id = p_id;
    delete from public.stock_moves where invoice_id = p_id;
end;
$$;

-- ---------- Interés por mora de clientes ----------
-- Cuentas por cobrar vencidas y el interés acumulado a la tasa anual de la empresa (días / 365).
create or replace view public.late_receivables with (security_invoker = true) as
select e.id as entry_id, e.contact_id, e.description, e.amount, e.due_date,
    greatest(e.due_date, coalesce(e.interest_until, e.due_date)) as interest_from,
    (current_date - greatest(e.due_date, coalesce(e.interest_until, e.due_date))) as days,
    (current_date - e.due_date) as days_overdue,
    round(e.amount * (select late_interest_rate from public.company where id = 1) / 100
          * (current_date - greatest(e.due_date, coalesce(e.interest_until, e.due_date))) / 365)::bigint as interest
from public.entries e
where e.type = 'INCOME' and not e.is_paid and not e.is_interest and e.due_date < current_date;

-- Registra el interés de mora como una cuenta por cobrar nueva y marca hasta cuándo se cobró.
create or replace function public.charge_late_interest(p_entry_id bigint) returns bigint
language plpgsql security invoker set search_path = public
as $$
declare r record; v_cat bigint; v_id bigint;
begin
    if not public.perm_accounting_write() then
        raise exception 'No tienes permiso para cobrar intereses';
    end if;
    select l.*, e.account_id into r from public.late_receivables l join public.entries e on e.id = l.entry_id
    where l.entry_id = p_entry_id;
    if not found or r.interest <= 0 then
        raise exception 'No hay interés por cobrar (revisa la tasa de mora en los datos de la empresa)';
    end if;
    select id into v_cat from public.categories where name = 'Intereses ganados' limit 1;
    insert into public.entries (type, amount, date, account_id, category_id, contact_id, description, is_paid,
                                due_date, is_interest)
    values ('INCOME', r.interest, current_date, r.account_id, v_cat, r.contact_id,
            'Interés por mora (' || r.days || ' días) · ' || r.description, false, current_date, true)
    returning id into v_id;
    update public.entries set interest_until = current_date where id = p_entry_id;
    return v_id;
end;
$$;

-- ---------- Impuestos: IVA mensual e intereses del SRI ----------
-- Fecha límite de declaración mensual según el noveno dígito del RUC (se corre al lunes si cae en fin de semana).
create or replace function public.sri_due_date(p_month date) returns date
language plpgsql stable set search_path = public as $$
declare d int; v date;
begin
    d := coalesce(nullif(substr((select ruc from public.company where id = 1), 9, 1), '')::int, 0);
    v := (date_trunc('month', p_month) + interval '1 month')::date
         + (case d when 1 then 10 when 2 then 12 when 3 then 14 when 4 then 16 when 5 then 18
                   when 6 then 20 when 7 then 22 when 8 then 24 when 9 then 26 else 28 end) - 1;
    while extract(isodow from v) > 5 loop v := v + 1; end loop;
    return v;
end $$;

-- Resumen mensual de IVA (base para el formulario 104). tax_due negativo = crédito tributario del mes.
create or replace view public.iva_monthly with (security_invoker = true) as
with s as (
    select date_trunc('month', fecha_emision)::date as m, sum(subtotal) as base, sum(tax) as iva
    from public.invoices where status <> 'ANULADA' group by 1
), p as (
    select date_trunc('month', date)::date as m, sum(amount - tax_amount) as base, sum(tax_amount) as iva
    from public.entries where type = 'EXPENSE' and tax_amount > 0 group by 1
)
select coalesce(s.m, p.m) as month,
    coalesce(s.base, 0)::bigint as sales_base, coalesce(s.iva, 0)::bigint as sales_tax,
    coalesce(p.base, 0)::bigint as purchases_base, coalesce(p.iva, 0)::bigint as purchases_tax,
    (coalesce(s.iva, 0) - coalesce(p.iva, 0))::bigint as tax_due,
    public.sri_due_date(coalesce(s.m, p.m)) as due_date
from s full join p on s.m = p.m;

-- Intereses y multa por pagar/declarar tarde (art. 21 Código Tributario y art. 100 LRTI):
-- cada mes o fracción cuenta completo; interés con la tasa del trimestre de ese mes;
-- multa del 3 % mensual del impuesto, sin superar el 100 % del impuesto.
create or replace function public.sri_late_charges(p_tax bigint, p_due date, p_pay date default current_date)
returns table (months int, interest bigint, fine bigint, total bigint, missing_rates boolean)
language plpgsql stable set search_path = public as $$
declare m int := 0; v_rate numeric; v_int numeric := 0; v_missing boolean := false; v_start date;
begin
    if p_pay > p_due then
        while (p_due + make_interval(months => m))::date < p_pay loop
            v_start := (p_due + make_interval(months => m))::date;
            select monthly_rate into v_rate from public.sri_interest_rates
            where quarter_start = date_trunc('quarter', v_start)::date;
            if v_rate is null then v_missing := true; v_rate := 0; end if;
            v_int := v_int + p_tax * v_rate / 100;
            m := m + 1;
        end loop;
    end if;
    months := m;
    interest := round(v_int);
    fine := least(round(p_tax * 0.03 * m), p_tax);
    total := p_tax + interest + fine;
    missing_rates := v_missing;
    return next;
end $$;

-- Clientes: ventas netas de IVA.
create or replace view public.contact_stats with (security_invoker = true) as
select c.id as contact_id,
    coalesce((select sum(amount - tax_amount) from public.entries e where e.contact_id = c.id and e.type = 'INCOME'), 0) as sales,
    coalesce((select sum(amount - tax_amount) from public.entries e where e.contact_id = c.id and e.type = 'EXPENSE'), 0) as expenses,
    coalesce((select round(sum(-s.quantity * s.unit_cost)) from public.stock_moves s where s.contact_id = c.id and s.type = 'SALE'), 0)::bigint as cogs,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'INCOME' and not e.is_paid), 0) as receivable,
    coalesce((select sum(amount) from public.entries e where e.contact_id = c.id and e.type = 'EXPENSE' and not e.is_paid), 0) as payable
from public.contacts c;

-- ---------- Seguridad de las tablas de Ecuador ----------
alter table public.company enable row level security;
alter table public.invoices enable row level security;
alter table public.invoice_lines enable row level security;
alter table public.installments enable row level security;
alter table public.sri_interest_rates enable row level security;

drop policy if exists "leer empresa" on public.company;
drop policy if exists "editar empresa" on public.company;
drop policy if exists "leer facturas" on public.invoices;
drop policy if exists "leer lineas" on public.invoice_lines;
drop policy if exists "leer cuotas" on public.installments;
drop policy if exists "leer tasas sri" on public.sri_interest_rates;
drop policy if exists "editar tasas sri" on public.sri_interest_rates;

create policy "leer empresa" on public.company for select to authenticated using (public.perm_team());
create policy "editar empresa" on public.company for update to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
-- Facturas: se crean con create_invoice() y el SRI las actualiza desde el servidor.
create policy "leer facturas" on public.invoices for select to authenticated
    using (public.perm_accounting_read() or (public.role_in('vendedor') and created_by = auth.uid()));
create policy "leer lineas" on public.invoice_lines for select to authenticated
    using (exists (select 1 from public.invoices i where i.id = invoice_id));
create policy "leer cuotas" on public.installments for select to authenticated
    using (exists (select 1 from public.invoices i where i.id = invoice_id));
create policy "leer tasas sri" on public.sri_interest_rates for select to authenticated using (public.perm_team());
create policy "editar tasas sri" on public.sri_interest_rates for all to authenticated
    using (public.perm_accounting_write()) with check (public.perm_accounting_write());
-- <<< ecuador

-- >>> endurecer
-- Seguridad extra recomendada por el asesor de Supabase.
-- search_path fijo en las funciones auxiliares.
do $$
declare f record;
begin
    for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.proname in (
                 'perm_team', 'perm_accounting_read', 'perm_accounting_write', 'perm_accounts_read', 'perm_crm_read',
                 'perm_contacts_write', 'perm_crm_write', 'perm_purchases', 'perm_invoice', 'perm_items_write',
                 'perm_produce', 'perm_stock_adjust', 'perm_quality', 'perm_users_read', 'weighted_cost',
                 'ec_valid_cedula', 'ec_valid_ruc', 'ec_valid_id', 'iva_rate', 'sri_mod11', 'sri_clave_acceso')
    loop
        execute format('alter function %s set search_path = public', f.sig);
    end loop;
end $$;
-- Nadie sin sesión ejecuta funciones con privilegios; las de trigger no se llaman por la API.
revoke execute on function public.create_invoice(jsonb), public.void_invoice(bigint),
    public.produce(bigint, numeric, date, text), public.inspect_production(bigint, text, text),
    public.current_role_name(), public.has_role(public.app_role[]), public.role_in(text[]),
    public.handle_new_user(), public.on_purchase_move() from public, anon;
revoke execute on function public.handle_new_user(), public.on_purchase_move() from authenticated;
grant execute on function public.create_invoice(jsonb), public.void_invoice(bigint),
    public.produce(bigint, numeric, date, text), public.inspect_production(bigint, text, text),
    public.current_role_name(), public.has_role(public.app_role[]), public.role_in(text[]) to authenticated;
-- <<< endurecer

-- >>> precios
-- El precio de venta del producto terminado lo asigna contabilidad; I+D asigna costos y lista de materiales.
create or replace function public.perm_set_prices() returns boolean language sql stable set search_path = public as $$
    select public.role_in('admin', 'contador') $$;

create or replace function public.guard_sale_price()
returns trigger
language plpgsql set search_path = public
as $$
begin
    -- Sin sesión (tareas internas del sistema) no se restringe.
    if auth.uid() is null or public.perm_set_prices() then
        return new;
    end if;
    if tg_op = 'INSERT' then
        new.sale_price := 0;  -- contabilidad lo pone después
    elsif new.sale_price is distinct from old.sale_price then
        raise exception 'El precio de venta lo asigna contabilidad';
    end if;
    return new;
end;
$$;

drop trigger if exists items_guard_sale_price on public.items;
create trigger items_guard_sale_price
    before insert or update on public.items
    for each row execute function public.guard_sale_price();
revoke execute on function public.guard_sale_price() from public, anon, authenticated;
-- <<< precios

-- >>> sri_ruc
-- Datos del catastro del SRI para cada cliente: se llenan con «Consultar SRI» al registrar el RUC.
alter table public.contacts add column if not exists actividad_economica text not null default '';
alter table public.contacts add column if not exists sri_estado text not null default '';       -- ACTIVO, SUSPENDIDO, PASIVO...
alter table public.contacts add column if not exists sri_tipo text not null default '';         -- PERSONA NATURAL, SOCIEDAD...
alter table public.contacts add column if not exists sri_regimen text not null default '';      -- GENERAL, RIMPE...
alter table public.contacts add column if not exists sri_obligado_contabilidad text not null default '';  -- SI / NO
alter table public.contacts add column if not exists sri_data jsonb;                             -- respuesta completa del SRI
alter table public.contacts add column if not exists sri_checked_at timestamptz;
-- <<< sri_ruc

-- >>> borrar_usuario
-- Al eliminar un usuario (Google Play exige poder borrar cuentas) sus registros contables se conservan
-- y solo pierden la referencia a quién los creó.
do $$
declare r record;
begin
    for r in
        select c.conrelid::regclass as tbl, c.conname, a.attname
        from pg_constraint c
        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
        where c.confrelid = 'auth.users'::regclass and c.connamespace = 'public'::regnamespace
          and c.contype = 'f' and c.confdeltype = 'a'
    loop
        execute format('alter table %s drop constraint %I', r.tbl, r.conname);
        execute format('alter table %s add constraint %I foreign key (%I) references auth.users (id) on delete set null',
                       r.tbl, r.conname, r.attname);
    end loop;
end $$;
-- <<< borrar_usuario
