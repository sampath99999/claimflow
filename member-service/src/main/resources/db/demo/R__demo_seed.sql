-- Demo data, loaded only with the "demo" profile. Ids are fixed so the gateway demo logins can point at them.

insert into plans (id, code, name, plan_type, annual_deductible, coinsurance_percent, out_of_pocket_max, active)
values ('b5162f83-88f0-4235-8d29-52924e844924', 'SILVER-HMO', 'Silver HMO', 'HMO', 2500.00, 30, 8000.00, true),
       ('d12663fe-bfd0-46fc-8900-fa6dd34ac386', 'GOLD-PPO', 'Gold PPO', 'PPO', 1000.00, 20, 6000.00, true),
       ('29dac661-9d16-40d8-b752-781711c8ea47', 'BRONZE-HDHP', 'Bronze High Deductible', 'HDHP', 6000.00, 40, 9200.00, true),
       ('88589f1a-71d7-45e4-a0e2-125e3c39e1dc', 'PLATINUM-EPO', 'Platinum EPO', 'EPO', 250.00, 10, 3500.00, true),
       ('6c1f0a52-3d0e-4f43-9a51-2f7c9d1be0aa', 'LEGACY-PPO-2019', 'Legacy PPO 2019', 'PPO', 1500.00, 25, 7000.00, false)
on conflict (id) do nothing;

insert into members (id, member_number, first_name, last_name, date_of_birth, email, plan_id, coverage_start, coverage_end, status)
values ('eb47888d-3ffb-4ad2-940a-ae35e825b454', 'M100001', 'Maria', 'Gonzalez', '1988-04-12', 'maria.gonzalez@example.com', 'd12663fe-bfd0-46fc-8900-fa6dd34ac386', '2025-01-01', null, 'ACTIVE'),
       ('9a98105c-6ecc-4f82-aaf5-ad034623584f', 'M100002', 'James', 'Carter', '1975-09-30', 'james.carter@example.com', 'b5162f83-88f0-4235-8d29-52924e844924', '2024-07-01', null, 'ACTIVE'),
       ('10ad1f24-ab09-4abe-95df-8047b0fd84c2', 'M100003', 'Ananya', 'Rao', '1992-01-22', 'ananya.rao@example.com', '88589f1a-71d7-45e4-a0e2-125e3c39e1dc', '2025-03-01', null, 'ACTIVE'),
       ('3c27b034-1060-4630-933a-e56e7891cdd5', 'M100004', 'David', 'Kim', '1969-11-05', 'david.kim@example.com', 'd12663fe-bfd0-46fc-8900-fa6dd34ac386', '2023-01-01', null, 'ACTIVE'),
       ('c108f60f-7cb6-46fe-80d9-2fe90a4acb76', 'M100005', 'Fatima', 'Noor', '1984-06-17', 'fatima.noor@example.com', '29dac661-9d16-40d8-b752-781711c8ea47', '2024-01-01', '2026-06-30', 'TERMINATED'),
       ('cd4efe76-22ec-449a-aa58-8e49e188c8d6', 'M100006', 'Robert', 'Miller', '1958-02-14', 'robert.miller@example.com', 'b5162f83-88f0-4235-8d29-52924e844924', '2022-05-01', null, 'ACTIVE'),
       ('51dc2269-0fb4-43cf-a041-c1aa478e4b46', 'M100007', 'Lakshmi', 'Iyer', '1990-08-09', 'lakshmi.iyer@example.com', 'd12663fe-bfd0-46fc-8900-fa6dd34ac386', '2025-06-01', null, 'ACTIVE'),
       ('c71fb762-1518-4870-8dbb-2a5a5d7f957f', 'M100008', 'Ethan', 'Brooks', '1979-12-01', 'ethan.brooks@example.com', '29dac661-9d16-40d8-b752-781711c8ea47', '2024-09-01', null, 'SUSPENDED'),
       ('4208b265-f551-4cc7-b168-398640fbd6ec', 'M100009', 'Sofia', 'Rossi', '1995-03-27', 'sofia.rossi@example.com', '88589f1a-71d7-45e4-a0e2-125e3c39e1dc', '2025-01-01', null, 'ACTIVE'),
       ('009bc37c-baba-4aa5-907d-1bdc2903c7a5', 'M100010', 'Arjun', 'Mehta', '1986-10-19', 'arjun.mehta@example.com', 'b5162f83-88f0-4235-8d29-52924e844924', '2023-04-01', null, 'ACTIVE'),
       ('1cfbcc87-47d8-47ae-8336-2a9efeeb03ea', 'M100011', 'Grace', 'Lee', '2001-05-03', 'grace.lee@example.com', 'd12663fe-bfd0-46fc-8900-fa6dd34ac386', '2026-01-01', null, 'ACTIVE'),
       ('6c325a42-ace3-415d-bc71-8876d891874f', 'M100012', 'Omar', 'Haddad', '1972-07-24', 'omar.haddad@example.com', '6c1f0a52-3d0e-4f43-9a51-2f7c9d1be0aa', '2019-01-01', '2025-12-31', 'TERMINATED')
on conflict (id) do nothing;

select setval('member_number_seq', greatest((select max(substring(member_number from 2)::bigint) from members), 100000));
