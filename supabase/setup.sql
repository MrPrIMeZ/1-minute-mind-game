-- =====================================================================
-- 1 Minute Mind Game: online scoreboard (Supabase / PostgreSQL)
-- Run this once in Supabase: SQL Editor → New query → paste → Run.
--
-- Players only give a username and a state. The app talks to the
-- database ONLY through the functions below; the tables themselves are
-- locked (row level security with no policies), so nobody can read
-- secrets or edit other players' scores directly.
-- =====================================================================

create extension if not exists pgcrypto;

create table if not exists public.players (
  id         uuid primary key default gen_random_uuid(),
  secret     uuid not null default gen_random_uuid(),
  name       text not null,
  state      text not null,
  created_at timestamptz not null default now()
);
create unique index if not exists players_name_unique on public.players (lower(name));
-- Premium members get a crown on the scoreboard.
alter table public.players add column if not exists premium boolean not null default false;

create table if not exists public.daily_scores (
  player_id  uuid not null references public.players(id) on delete cascade,
  day        int  not null,                       -- yyyymmdd, e.g. 20260928
  score      int  not null check (score between 0 and 9000),
  created_at timestamptz not null default now(),
  primary key (player_id, day)
);
create index if not exists daily_scores_day on public.daily_scores (day, score desc);

alter table public.players      enable row level security;
alter table public.daily_scores enable row level security;
revoke all on public.players, public.daily_scores from anon, authenticated;

-- ---------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------
create or replace function public.mm_valid_state(p_state text) returns boolean
language sql immutable as $$
  select p_state = any (array[
    'Andhra Pradesh','Arunachal Pradesh','Assam','Bihar','Chhattisgarh','Goa','Gujarat',
    'Haryana','Himachal Pradesh','Jharkhand','Karnataka','Kerala','Madhya Pradesh',
    'Maharashtra','Manipur','Meghalaya','Mizoram','Nagaland','Odisha','Punjab','Rajasthan',
    'Sikkim','Tamil Nadu','Telangana','Tripura','Uttar Pradesh','Uttarakhand','West Bengal',
    'Andaman and Nicobar Islands','Chandigarh','Dadra and Nagar Haveli and Daman and Diu',
    'Delhi','Jammu and Kashmir','Ladakh','Lakshadweep','Puducherry'
  ]);
$$;

create or replace function public.mm_check_profile(p_name text, p_state text) returns text
language plpgsql immutable as $$
declare n text := btrim(p_name);
begin
  if n is null or length(n) < 3 or length(n) > 16 or n !~ '^[A-Za-z0-9_. -]+$' then
    raise exception 'bad_name';
  end if;
  if not public.mm_valid_state(p_state) then
    raise exception 'bad_state';
  end if;
  return n;
end $$;

-- ---------------------------------------------------------------------
-- Sign up: returns the new player's id and secret (kept on the phone).
-- ---------------------------------------------------------------------
create or replace function public.register_player(p_name text, p_state text)
returns json language plpgsql security definer set search_path = public as $$
declare n text; r public.players;
begin
  n := mm_check_profile(p_name, p_state);
  begin
    insert into players(name, state) values (n, p_state) returning * into r;
  exception when unique_violation then
    raise exception 'name_taken';
  end;
  return json_build_object('id', r.id, 'secret', r.secret, 'name', r.name, 'state', r.state);
end $$;

-- Change username or state.
create or replace function public.update_player(p_id uuid, p_secret uuid, p_name text, p_state text)
returns void language plpgsql security definer set search_path = public as $$
declare n text;
begin
  n := mm_check_profile(p_name, p_state);
  begin
    update players set name = n, state = p_state where id = p_id and secret = p_secret;
  exception when unique_violation then
    raise exception 'name_taken';
  end;
  if not found then raise exception 'unknown_player'; end if;
end $$;

-- Save a day's daily-challenge total (only for today/yesterday). If it's sent again
-- (Premium retry), the best total of the day is kept.
create or replace function public.submit_daily(p_id uuid, p_secret uuid, p_day int, p_score int)
returns void language plpgsql security definer set search_path = public as $$
declare
  today int := to_char(now() at time zone 'Asia/Kolkata', 'YYYYMMDD')::int;
  yday  int := to_char((now() at time zone 'Asia/Kolkata') - interval '1 day', 'YYYYMMDD')::int;
  tmrw  int := to_char((now() at time zone 'Asia/Kolkata') + interval '1 day', 'YYYYMMDD')::int;
begin
  if not exists (select 1 from players where id = p_id and secret = p_secret) then
    raise exception 'unknown_player';
  end if;
  if p_day not in (yday, today, tmrw) then raise exception 'bad_day'; end if;
  if p_score < 0 or p_score > 9000 then raise exception 'bad_score'; end if;
  insert into daily_scores(player_id, day, score) values (p_id, p_day, p_score)
  on conflict (player_id, day) do update set score = greatest(daily_scores.score, excluded.score);
end $$;

-- Shows or hides the Premium crown.
create or replace function public.set_premium(p_id uuid, p_secret uuid, p_on boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  update players set premium = p_on where id = p_id and secret = p_secret;
  if not found then raise exception 'unknown_player'; end if;
end $$;

-- Leaderboard. p_period: 'today' (uses p_day) or 'all' (sum of all daily scores).
-- p_state: null for All India, or a state name. Returns the top 100 plus your own row.
drop function if exists public.leaderboard(text, text, int, uuid);
create or replace function public.leaderboard(p_period text, p_state text, p_day int, p_player uuid)
returns table(rank bigint, player_name text, player_state text, score bigint, is_me boolean, is_premium boolean)
language sql stable security definer set search_path = public as $$
  with totals as (
    select p.id, p.name, p.state, p.premium, sum(d.score)::bigint as total
    from daily_scores d join players p on p.id = d.player_id
    where (p_period = 'all' or d.day = p_day)
      and (p_state is null or p.state = p_state)
    group by p.id, p.name, p.state, p.premium
  ), ranked as (
    select t.*, rank() over (order by t.total desc) as r from totals t
  )
  select r, name, state, total, coalesce(id = p_player, false), premium
  from ranked
  where r <= 100 or id = p_player
  order by r, name
  limit 101;
$$;

revoke all on function public.register_player(text, text) from public;
revoke all on function public.update_player(uuid, uuid, text, text) from public;
revoke all on function public.submit_daily(uuid, uuid, int, int) from public;
revoke all on function public.leaderboard(text, text, int, uuid) from public;
revoke all on function public.set_premium(uuid, uuid, boolean) from public;
grant execute on function public.set_premium(uuid, uuid, boolean) to anon, authenticated;
grant execute on function public.register_player(text, text) to anon, authenticated;
grant execute on function public.update_player(uuid, uuid, text, text) to anon, authenticated;
grant execute on function public.submit_daily(uuid, uuid, int, int) to anon, authenticated;
grant execute on function public.leaderboard(text, text, int, uuid) to anon, authenticated;
