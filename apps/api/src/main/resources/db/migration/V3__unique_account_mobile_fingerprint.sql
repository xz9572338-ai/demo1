ALTER TABLE accounts
    ADD CONSTRAINT uk_accounts_mobile_fingerprint UNIQUE (contact_mobile_fingerprint);
