alter table if exists users
    add column if not exists profile_version bigint not null default 0;
