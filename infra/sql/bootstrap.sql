-- Creates the two application roles and databases (D9). Run by the maintainer over an SSM port-forwarding
-- session, as the RDS master user. Passwords come in as psql variables, never in this file:
--   psql "host=127.0.0.1 port=15432 dbname=postgres user=liberdade_admin sslmode=require" \
--     -v production_password="$PRODUCTION_PASSWORD" -v staging_password="$STAGING_PASSWORD" \
--     -f infra/sql/bootstrap.sql
-- Safe to run again: each statement is generated only when the role or database is missing.

-- Roles
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'liberdade_production', :'production_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'liberdade_production')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'liberdade_staging', :'staging_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'liberdade_staging')\gexec

-- The RDS master is not a superuser: it must be a member of a role to create a database owned by it.
SELECT format('GRANT %I TO CURRENT_USER', r)
FROM unnest(ARRAY['liberdade_production', 'liberdade_staging']) AS r\gexec

-- Databases, owned by the matching role
SELECT format('CREATE DATABASE %I OWNER %I', 'liberdade_production', 'liberdade_production')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'liberdade_production')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'liberdade_staging', 'liberdade_staging')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'liberdade_staging')\gexec

-- Nobody else may connect
REVOKE CONNECT ON DATABASE liberdade_production FROM PUBLIC;
REVOKE CONNECT ON DATABASE liberdade_staging FROM PUBLIC;
