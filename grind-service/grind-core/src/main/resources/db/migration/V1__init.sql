-- GRIND: initial schema (replaces the pre-production V1-V4). Design and reasoning: docs/grind-database.md.
-- Zero-knowledge: the server stores ciphertext, wrapped keys, ids, sizes and dates only. What a record is (a workout,
-- a journal day...) lives inside the ciphertext.
--
-- Row-level security isolates users: every table is visible only for the user id set in `app.user_id`, which the
-- application sets per transaction (UserTransactionTemplate). Migrations run as the owner role; the application
-- connects as ${app_role}, which is not the owner and has no BYPASSRLS, so the policies always apply to it.
set lock_timeout = '5s';

-- Flyway runs with schemas=grind, so this schema exists and is the search path.

-- The signed-in user for this transaction (set by UserTransactionTemplate).
create function current_user_id() returns uuid
    language sql stable
as $$ select nullif(current_setting('app.user_id', true), '')::uuid $$;

-- One number at a time: a user's sequence must grow in commit order (see "Sync order").
create sequence record_seq as bigint cache 1;

-- ── accounts: one row per Supabase user, kept after deletion ─────────────────────────
create table accounts (
    user_id                uuid        primary key,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    deletion_requested_at  timestamptz,
    deletion_completed_at  timestamptz,
    tombstone_horizon_seq  bigint      not null default 0,
    last_active_on         date        not null default current_date,
    status                 text        not null default 'active',
    plan                   text        not null default 'free',
    constraint accounts_status check (status in ('active', 'deleting', 'deleted')),
    constraint accounts_plan   check (plan in ('free', 'plus')),
    constraint accounts_deletion_shape check (
           (status = 'active'   and deletion_requested_at is null     and deletion_completed_at is null)
        or (status = 'deleting' and deletion_requested_at is not null and deletion_completed_at is null)
        or (status = 'deleted'  and deletion_requested_at is not null and deletion_completed_at is not null))
);

create index accounts_deleting_idx on accounts (deletion_requested_at) where status = 'deleting';

-- ── records: latest version of every synced item, 32 parts by user ───────────────────
create table records (
    user_id      uuid        not null references accounts (user_id),
    id           uuid        not null,
    rev          bigint      not null check (rev >= 1),
    seq          bigint      not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    blob_size    integer     check (blob_size between 1 and 8388608),
    deleted      boolean     not null,
    blob_sha256  text        check (length(blob_sha256) = 44),
    blob_key     text        check (length(blob_key) <= 128),
    wrapped_dek  text        check (length(wrapped_dek) <= 256),
    inline_ct    bytea       check (octet_length(inline_ct) <= 4096),
    primary key (user_id, id),
    constraint records_user_seq unique (user_id, seq),
    -- blob columns are all set or all empty
    constraint records_blob_complete check (
        (blob_key is null) = (blob_size is null) and (blob_key is null) = (blob_sha256 is null)),
    -- a deletion keeps no content; a live record has exactly one of inline / blob, plus its wrapped key
    constraint records_content_shape check (
           (deleted and inline_ct is null and blob_key is null and wrapped_dek is null)
        or (not deleted and wrapped_dek is not null and (inline_ct is null) <> (blob_key is null)))
) partition by hash (user_id);

do $$
begin
    for i in 0..31 loop
        execute format(
            'create table records_p%s partition of records for values with (modulus 32, remainder %s)
             with (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.02)',
            lpad(i::text, 2, '0'), i);
    end loop;
end $$;

-- old deletion markers, for the purge job
create index records_tombstones_idx on records (user_id, updated_at) where deleted;

-- ── keyrings: the wrapped master key ──────────────────────────────────────────────────
create table keyrings (
    user_id                  uuid        primary key references accounts (user_id),
    rev                      bigint      not null check (rev >= 1),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    format_version           integer     not null check (format_version between 1 and 100),
    kdf_iterations           integer     not null check (kdf_iterations between 400000 and 2000000),
    kdf_salt                 text        not null check (length(kdf_salt) <= 64),
    wrapped_by_passphrase    text        not null check (length(wrapped_by_passphrase) <= 512),
    wrapped_by_recovery_key  text        not null check (length(wrapped_by_recovery_key) <= 512),
    check_value              text        not null check (length(check_value) <= 256)
);

-- ── storage_usage: hot per-user counter, kept apart from accounts ─────────────────────
create table storage_usage (
    user_id     uuid        primary key references accounts (user_id),
    used_bytes  bigint      not null default 0 check (used_bytes >= 0),
    updated_at  timestamptz not null default now()
) with (fillfactor = 70);

-- ── row-level security: every table, forced, one user at a time ───────────────────────
alter table accounts      enable row level security;  alter table accounts      force row level security;
alter table records       enable row level security;  alter table records       force row level security;
alter table keyrings      enable row level security;  alter table keyrings      force row level security;
alter table storage_usage enable row level security;  alter table storage_usage force row level security;

create policy accounts_owner      on accounts      using (user_id = current_user_id()) with check (user_id = current_user_id());
create policy records_owner       on records       using (user_id = current_user_id()) with check (user_id = current_user_id());
create policy keyrings_owner      on keyrings      using (user_id = current_user_id()) with check (user_id = current_user_id());
create policy storage_usage_owner on storage_usage using (user_id = current_user_id()) with check (user_id = current_user_id());

-- ── the only cross-user questions: ids only, run as the owner ──────────────────────────
create policy accounts_sweep         on accounts      for select to current_user using (true);
create policy storage_usage_reconcile on storage_usage for select to current_user using (true);

create function stuck_account_deletions(requested_before timestamptz, max_rows integer)
    returns setof uuid language sql stable security definer
as $$
    select user_id from accounts
    where status = 'deleting' and deletion_requested_at < requested_before
    order by deletion_requested_at
    limit max_rows
$$;

create function storage_users_after(after_user uuid, max_rows integer)
    returns setof uuid language sql stable security definer
as $$
    select user_id from storage_usage where user_id > after_user order by user_id limit max_rows
$$;

alter function stuck_account_deletions(timestamptz, integer) set search_path from current;
alter function storage_users_after(uuid, integer)          set search_path from current;
revoke all on function stuck_account_deletions(timestamptz, integer), storage_users_after(uuid, integer) from public;

-- ── grants: the parent tables only, never the parts ───────────────────────────────────
grant usage on schema grind to ${app_role}, ${support_role};
grant select, insert, update         on accounts                          to ${app_role};
grant select, insert, update, delete on records, keyrings, storage_usage  to ${app_role};
grant usage on sequence record_seq to ${app_role};
grant execute on function current_user_id(), stuck_account_deletions(timestamptz, integer),
                          storage_users_after(uuid, integer) to ${app_role};
grant select on accounts, records, keyrings, storage_usage to ${support_role};
grant execute on function current_user_id() to ${support_role};
