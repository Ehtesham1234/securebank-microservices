INSERT INTO users (
    first_name,
    last_name,
    email,
    password,
    phone_number,
    role,
    user_status,
    created_at,
    updated_at
) VALUES (
             'Super',
             'Admin',
             'admin@securebank.com',
             '$2a$10$OCDQggV490UfAJKvRuRDP.cZYlKhXRDXWUEfCyZGnDDJ/bqFZHHny',
             '0000000000',
             'ADMIN',
             'ACTIVE',
             NOW(),
             NOW()
         );