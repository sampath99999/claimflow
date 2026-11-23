create table plans (
    id                  uuid primary key,
    code                varchar(32)    not null,
    name                varchar(120)   not null,
    plan_type           varchar(16)    not null,
    annual_deductible   numeric(12, 2) not null check (annual_deductible >= 0),
    coinsurance_percent integer        not null check (coinsurance_percent between 0 and 100),
    out_of_pocket_max   numeric(12, 2) not null check (out_of_pocket_max >= 0),
    active              boolean        not null default true,
    version             bigint         not null default 0,
    created_at          timestamptz    not null default now(),
    updated_at          timestamptz    not null default now(),
    constraint uq_plans_code unique (code)
);

create table members (
    id             uuid primary key,
    member_number  varchar(16)  not null,
    first_name     varchar(80)  not null,
    last_name      varchar(80)  not null,
    date_of_birth  date         not null,
    email          varchar(160),
    plan_id        uuid         not null references plans (id),
    coverage_start date         not null,
    coverage_end   date,
    status         varchar(16)  not null,
    version        bigint       not null default 0,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint uq_members_member_number unique (member_number),
    constraint ck_members_coverage check (coverage_end is null or coverage_end >= coverage_start)
);

create unique index uq_members_email on members (lower(email));
create index ix_members_last_name on members (lower(last_name));
create index ix_members_plan on members (plan_id);

create sequence member_number_seq start with 100001;
