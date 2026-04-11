-- Seed three demo customers whose IDs are referenced throughout the mock data sources.
INSERT INTO customers (id, name, email)
VALUES ('11111111-1111-1111-1111-111111111111', 'Alice Johnson',   'alice.johnson@example.com'),
       ('22222222-2222-2222-2222-222222222222', 'Bob Smith',       'bob.smith@example.com'),
       ('33333333-3333-3333-3333-333333333333', 'Carol Williams',  'carol.williams@example.com');
