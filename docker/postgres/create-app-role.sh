#!/bin/sh
# Creates itacatl_app, the role the backend connects as: it owns nothing and cannot bypass
# Row-Level Security. Its privileges come from the migrations (V4); its password stays out of
# them. The postgres image runs this once, when the database volume is created. See
# docs/tenant-isolation.md.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_password="$DB_APP_PASSWORD" <<'SQL'
-- :'app_password' lets psql quote the value, so any password works
CREATE ROLE itacatl_app LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
SQL
