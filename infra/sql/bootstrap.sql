-- Creates the two application roles and databases (D9). Run by the maintainer over an SSM port-forwarding
-- session, as the RDS master user, with psql 15 or newer. The passwords come in through environment
-- variables, never in this file or on the command line:
--   PRODUCTION_PASSWORD=... STAGING_PASSWORD=... \
--     psql "host=127.0.0.1 port=15432 dbname=postgres user=liberdade_admin sslmode=require" \
--     -f infra/sql/bootstrap.sql
-- Create-only: it can run again without error, but it never rotates the password of a role that already
-- exists. Change those with ALTER ROLE.

\set ON_ERROR_STOP on
\getenv production_password PRODUCTION_PASSWORD
\getenv staging_password STAGING_PASSWORD

-- An unset environment variable leaves the psql variable undefined.
SELECT (:{?production_password} AND :{?staging_password}) AS passwords_set \gset
\if :passwords_set
\else
  DO $$ BEGIN RAISE EXCEPTION 'PRODUCTION_PASSWORD and STAGING_PASSWORD must both be set'; END $$;
\endif

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
