CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    login_id VARCHAR(20) NOT NULL UNIQUE,
    email_ciphertext TEXT NOT NULL,
    email_lookup_hash VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
