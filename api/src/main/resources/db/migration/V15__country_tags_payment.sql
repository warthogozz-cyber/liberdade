-- The family's country and language, and how a transaction was paid. Existing families are Brazilian.
ALTER TABLE workspaces
    ADD COLUMN country char(2) NOT NULL DEFAULT 'BR' CHECK (country IN ('BR')),
    ADD COLUMN locale  text    NOT NULL DEFAULT 'pt-BR' CHECK (locale IN ('pt-BR'));

-- A transfer moves money between the family's own accounts, so it has no payment method.
ALTER TABLE transactions
    ADD COLUMN payment_method text NULL
        CHECK (payment_method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH')),
    ADD CONSTRAINT transactions_transfer_without_payment_method CHECK (kind <> 'TRANSFER' OR payment_method IS NULL);
