create table if not exists ranking_event_ledger (
    event_id varchar(64) primary key,
    event_type varchar(128),
    post_id bigint not null,
    actor_id bigint,
    author_id bigint,
    metric_type varchar(32) not null,
    delta int not null,
    occurred_at timestamp not null,
    published_at timestamp,
    partition_key varchar(64) not null,
    source_service varchar(64),
    source_op_id varchar(64),
    created_at timestamp not null
);

create index if not exists idx_ranking_ledger_post_occurred
    on ranking_event_ledger(post_id, occurred_at, event_id);

create index if not exists idx_ranking_ledger_occurred_event
    on ranking_event_ledger(occurred_at, event_id);

create table if not exists ranking_delta_bucket (
    bucket_start timestamp not null,
    post_id bigint not null,
    view_delta bigint not null default 0,
    like_delta int not null default 0,
    favorite_delta int not null default 0,
    comment_delta int not null default 0,
    applied_view_delta bigint not null default 0,
    applied_like_delta int not null default 0,
    applied_favorite_delta int not null default 0,
    applied_comment_delta int not null default 0,
    flush_owner varchar(128),
    flush_started_at timestamp,
    flushed boolean not null default false,
    flushed_at timestamp,
    updated_at timestamp not null,
    primary key (bucket_start, post_id)
);

create index if not exists idx_ranking_bucket_flush
    on ranking_delta_bucket(flushed, bucket_start, updated_at);

alter table if exists ranking_delta_bucket
    add column if not exists applied_view_delta bigint not null default 0;

alter table if exists ranking_delta_bucket
    add column if not exists applied_like_delta int not null default 0;

alter table if exists ranking_delta_bucket
    add column if not exists applied_favorite_delta int not null default 0;

alter table if exists ranking_delta_bucket
    add column if not exists applied_comment_delta int not null default 0;

create table if not exists ranking_post_state (
    post_id bigint primary key,
    author_id bigint,
    published_at timestamp,
    topic_ids varchar(2048),
    view_count bigint not null default 0,
    like_count int not null default 0,
    favorite_count int not null default 0,
    comment_count int not null default 0,
    raw_score double precision not null default 0,
    hot_score double precision not null default 0,
    version bigint not null default 0,
    last_bucket_start timestamp,
    updated_at timestamp not null
);

create index if not exists idx_ranking_post_state_hot_score
    on ranking_post_state(hot_score);

create table if not exists ranking_period_score (
    period_type varchar(16) not null,
    period_key varchar(32) not null,
    post_id bigint not null,
    delta_score double precision not null default 0,
    updated_at timestamp not null,
    primary key (period_type, period_key, post_id)
);

create index if not exists idx_ranking_period_score_lookup
    on ranking_period_score(period_type, period_key, delta_score);
