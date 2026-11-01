-- The role the application connects as, created before the migrations like docker/postgres does
-- for docker compose. A test-only password: the container lives for one test run
CREATE ROLE itacatl_app LOGIN PASSWORD 'itacatl-app-test'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
