-- FleetDocket core schema: tenancy, subjects, requirement catalog, compliance
-- items, documents, driver upload links, alerting, and billing mirror.
-- Every tenant table carries org_id and is protected by RLS; the service-role
-- key (webhooks, cron, token-upload route) bypasses RLS by design.

create schema if not exists private;

-- ---------------------------------------------------------------------------
-- Tenancy
-- ---------------------------------------------------------------------------

create table public.orgs (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  dot_number text,
  mc_number text,
  timezone text not null default 'America/Chicago',
  stripe_customer_id text unique,
  plan text,
  subscription_status text not null default 'trialing',
  trial_ends_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.memberships (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'admin' check (role in ('admin', 'member')),
  created_at timestamptz not null default now(),
  unique (org_id, user_id)
);

-- security definer so RLS policies can consult memberships without recursing
-- into the memberships policies themselves.
create or replace function private.user_org_ids()
returns setof uuid
language sql
security definer
set search_path = ''
stable
as $$
  select org_id from public.memberships where user_id = (select auth.uid());
$$;

-- ---------------------------------------------------------------------------
-- Subjects
-- ---------------------------------------------------------------------------

create table public.drivers (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  first_name text not null,
  last_name text not null,
  email text,
  phone text,
  hire_date date,
  termination_date date,
  cdl_number text,
  cdl_state text,
  status text not null default 'active' check (status in ('active', 'terminated')),
  created_at timestamptz not null default now()
);

create table public.vehicles (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  unit_number text not null,
  vin text,
  plate text,
  plate_state text,
  year int,
  make text,
  status text not null default 'active' check (status in ('active', 'retired')),
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Requirement catalog (data-driven; org_id NULL = global seeded row)
-- ---------------------------------------------------------------------------

create table public.requirement_catalog (
  id uuid primary key default gen_random_uuid(),
  org_id uuid references public.orgs (id) on delete cascade,
  code text not null,
  name text not null,
  description text,
  regulation_ref text,
  applies_to text not null check (applies_to in ('driver', 'vehicle', 'company')),
  recurrence_kind text not null check (
    recurrence_kind in ('one_time', 'document_expiry', 'fixed_interval', 'calendar_rule')
  ),
  interval_months int,
  calendar_rule jsonb,
  default_lead_days int[] not null default '{60,30,14,7,0}',
  required boolean not null default true,
  sort_order int not null default 0,
  check (recurrence_kind <> 'fixed_interval' or interval_months is not null),
  check (recurrence_kind <> 'calendar_rule' or calendar_rule is not null)
);

-- one global namespace for seeded codes, per-org namespace for custom rows
create unique index requirement_catalog_global_code_key
  on public.requirement_catalog (code) where org_id is null;
create unique index requirement_catalog_org_code_key
  on public.requirement_catalog (org_id, code) where org_id is not null;

create table public.org_requirement_settings (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  requirement_id uuid not null references public.requirement_catalog (id) on delete cascade,
  enabled boolean not null default true,
  lead_days_override int[],
  unique (org_id, requirement_id)
);

-- ---------------------------------------------------------------------------
-- Compliance items (one row per subject per applicable requirement)
-- ---------------------------------------------------------------------------

create table public.compliance_items (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  requirement_id uuid not null references public.requirement_catalog (id) on delete cascade,
  driver_id uuid references public.drivers (id) on delete cascade,
  vehicle_id uuid references public.vehicles (id) on delete cascade,
  status text not null default 'missing' check (
    status in ('missing', 'current', 'expiring', 'expired', 'not_applicable')
  ),
  completed_at date,
  expires_at date,
  current_document_id uuid,
  notes text,
  updated_at timestamptz not null default now(),
  check (driver_id is null or vehicle_id is null),
  unique nulls not distinct (org_id, requirement_id, driver_id, vehicle_id)
);

create index compliance_items_expiry_idx
  on public.compliance_items (org_id, expires_at) where expires_at is not null;

-- ---------------------------------------------------------------------------
-- Documents
-- ---------------------------------------------------------------------------

create table public.documents (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  compliance_item_id uuid references public.compliance_items (id) on delete set null,
  driver_id uuid references public.drivers (id) on delete set null,
  vehicle_id uuid references public.vehicles (id) on delete set null,
  storage_path text not null,
  file_name text not null,
  mime_type text not null,
  size_bytes bigint not null,
  uploaded_by uuid references auth.users (id) on delete set null,
  upload_request_id uuid,
  review_status text not null default 'approved' check (
    review_status in ('pending', 'approved', 'rejected')
  ),
  expires_at date,
  created_at timestamptz not null default now()
);

alter table public.compliance_items
  add constraint compliance_items_current_document_fk
  foreign key (current_document_id) references public.documents (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Driver self-upload links (tokenized, no login; raw token never stored)
-- ---------------------------------------------------------------------------

create table public.upload_requests (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  driver_id uuid not null references public.drivers (id) on delete cascade,
  compliance_item_id uuid not null references public.compliance_items (id) on delete cascade,
  token_hash text not null unique,
  message text,
  channel text not null check (channel in ('sms', 'email')),
  expires_at timestamptz not null,
  used_at timestamptz,
  created_by uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.documents
  add constraint documents_upload_request_fk
  foreign key (upload_request_id) references public.upload_requests (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Alerting (dedupe key makes cron re-runs idempotent)
-- ---------------------------------------------------------------------------

create table public.alert_log (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.orgs (id) on delete cascade,
  compliance_item_id uuid not null references public.compliance_items (id) on delete cascade,
  tier int not null,
  channel text not null check (channel in ('email', 'sms')),
  recipient text not null,
  sent_at timestamptz not null default now(),
  unique (compliance_item_id, tier, channel, recipient)
);

-- ---------------------------------------------------------------------------
-- Stripe webhook idempotency
-- ---------------------------------------------------------------------------

create table public.stripe_events (
  id text primary key,
  processed_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------

alter table public.orgs enable row level security;
alter table public.memberships enable row level security;
alter table public.drivers enable row level security;
alter table public.vehicles enable row level security;
alter table public.requirement_catalog enable row level security;
alter table public.org_requirement_settings enable row level security;
alter table public.compliance_items enable row level security;
alter table public.documents enable row level security;
alter table public.upload_requests enable row level security;
alter table public.alert_log enable row level security;
alter table public.stripe_events enable row level security;

create policy orgs_member_select on public.orgs
  for select using (id in (select private.user_org_ids()));
create policy orgs_member_update on public.orgs
  for update using (id in (select private.user_org_ids()));

create policy memberships_member_select on public.memberships
  for select using (org_id in (select private.user_org_ids()));

-- global catalog rows are readable by any signed-in user; org rows by members
create policy catalog_select on public.requirement_catalog
  for select using (
    org_id is null or org_id in (select private.user_org_ids())
  );
create policy catalog_org_write on public.requirement_catalog
  for all using (org_id in (select private.user_org_ids()))
  with check (org_id in (select private.user_org_ids()));

-- uniform member policies for the remaining tenant tables
do $$
declare
  t text;
begin
  foreach t in array array[
    'drivers', 'vehicles', 'org_requirement_settings',
    'compliance_items', 'documents', 'upload_requests', 'alert_log'
  ]
  loop
    execute format(
      'create policy %I_member_all on public.%I
         for all using (org_id in (select private.user_org_ids()))
         with check (org_id in (select private.user_org_ids()))',
      t, t
    );
  end loop;
end
$$;

-- stripe_events: service-role only (no policies; RLS enabled blocks anon/auth)

-- Org creation happens through a security-definer function so a fresh signup
-- (who is not yet a member of anything) can create their org + membership
-- atomically without needing permissive insert policies.
create or replace function public.create_org(org_name text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  new_org_id uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'not authenticated';
  end if;
  insert into public.orgs (name) values (org_name) returning id into new_org_id;
  insert into public.memberships (org_id, user_id, role)
    values (new_org_id, (select auth.uid()), 'admin');
  return new_org_id;
end;
$$;
