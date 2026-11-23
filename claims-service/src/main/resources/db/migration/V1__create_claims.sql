create table claims (
    id                    uuid primary key,
    claim_number          varchar(20)    not null,
    member_id             uuid           not null,
    member_number         varchar(16)    not null,
    member_name           varchar(170)   not null,
    plan_code             varchar(32)    not null,
    coinsurance_percent   integer        not null check (coinsurance_percent between 0 and 100),
    provider_npi          varchar(10)    not null,
    provider_name         varchar(160)   not null,
    service_date          date           not null,
    diagnosis_code        varchar(8)     not null,
    status                varchar(16)    not null,
    total_billed          numeric(12, 2) not null check (total_billed > 0),
    payable_amount        numeric(12, 2),
    member_responsibility numeric(12, 2),
    decision_note         varchar(500),
    submitted_by          varchar(100)   not null,
    assigned_to           varchar(100),
    version               bigint         not null default 0,
    created_at            timestamptz    not null default now(),
    updated_at            timestamptz    not null default now(),
    constraint uq_claims_claim_number unique (claim_number),
    constraint ck_claims_status check (status in ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'DENIED', 'PAID'))
);

create index ix_claims_member_service_date on claims (member_id, service_date desc);
create index ix_claims_status_created on claims (status, created_at desc);
create index ix_claims_duplicate_check on claims (member_id, provider_npi, service_date);

create table claim_lines (
    id             uuid primary key,
    claim_id       uuid           not null references claims (id) on delete cascade,
    line_number    integer        not null,
    procedure_code varchar(5)     not null,
    description    varchar(200)   not null,
    units          integer        not null check (units > 0),
    charge_amount  numeric(12, 2) not null check (charge_amount > 0),
    constraint uq_claim_lines_number unique (claim_id, line_number)
);

create sequence claim_number_seq start with 1;
