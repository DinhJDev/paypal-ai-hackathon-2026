CREATE TABLE owner_access (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer varchar(512) NOT NULL CHECK (length(trim(issuer)) > 0),
    subject varchar(255) NOT NULL CHECK (length(trim(subject)) > 0),
    enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT owner_access_identity_unique UNIQUE (issuer, subject)
);
