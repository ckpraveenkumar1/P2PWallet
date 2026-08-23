-- V2__rename_idempotency_key_to_transaction_id.sql
-- Rename idempotency_key column to transaction_id in transfers table

ALTER TABLE transfers RENAME COLUMN idempotency_key TO transaction_id;
