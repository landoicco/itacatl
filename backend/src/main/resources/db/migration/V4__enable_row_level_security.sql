-- Row-Level Security: PostgreSQL's own check that every row read or written belongs to the
-- business the application is working for, under Hibernate's tenant filter. It also covers what
-- that filter cannot see, such as native SQL. See docs/tenant-isolation.md.
--
-- Two roles take part:
-- - The tables' owner, which runs these migrations. Policies do not apply to a table's owner.
-- - itacatl_app, which the application connects as. It owns nothing and cannot bypass the
--   policies. It is created outside the migrations, with its password (docker/postgres,
--   Testcontainers, Terraform), and gets its privileges here.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'itacatl_app') THEN
        RAISE EXCEPTION 'Role itacatl_app does not exist: create it before migrating (docs/tenant-isolation.md)';
    END IF;
END
$$;

-- The business of the current connection, which the application sets for each Hibernate session
-- (TenantConnectionProvider). The values mirror TenantContext: a business ID, 0 for every
-- business (read-only here), and -1 when nothing was set, so a connection without a tenant sees
-- no business's rows
CREATE FUNCTION app_tenant() RETURNS BIGINT
    LANGUAGE sql
    STABLE
AS $$
    SELECT coalesce(nullif(current_setting('app.tenant_id', true), ''), '-1')::BIGINT
$$;

-- Each table gets two policies, and a row is visible if either allows it:
-- - <table>_tenant: reads and writes within the connection's business. WITH CHECK also stops an
--   insert or update from moving a row into another business.
-- - <table>_public_read: the marketplace reads every business, but only SELECT. This is the
--   application's CROSS_TENANT scope (app_tenant() = 0), which can never write.

ALTER TABLE business_hours ENABLE ROW LEVEL SECURITY;
CREATE POLICY business_hours_tenant ON business_hours
    USING (tenant_id = app_tenant())
    WITH CHECK (tenant_id = app_tenant());
CREATE POLICY business_hours_public_read ON business_hours FOR SELECT
    USING (app_tenant() = 0);

ALTER TABLE schedule_exception ENABLE ROW LEVEL SECURITY;
CREATE POLICY schedule_exception_tenant ON schedule_exception
    USING (tenant_id = app_tenant())
    WITH CHECK (tenant_id = app_tenant());
CREATE POLICY schedule_exception_public_read ON schedule_exception FOR SELECT
    USING (app_tenant() = 0);

ALTER TABLE item ENABLE ROW LEVEL SECURITY;
CREATE POLICY item_tenant ON item
    USING (tenant_id = app_tenant())
    WITH CHECK (tenant_id = app_tenant());
CREATE POLICY item_public_read ON item FOR SELECT
    USING (app_tenant() = 0);

-- Drafts are the owner's work in progress: the marketplace never sees them
ALTER TABLE menu ENABLE ROW LEVEL SECURITY;
CREATE POLICY menu_tenant ON menu
    USING (tenant_id = app_tenant())
    WITH CHECK (tenant_id = app_tenant());
CREATE POLICY menu_public_read ON menu FOR SELECT
    USING (app_tenant() = 0 AND status = 'PUBLISHED');

-- Nor the items of a draft
ALTER TABLE menu_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY menu_item_tenant ON menu_item
    USING (tenant_id = app_tenant())
    WITH CHECK (tenant_id = app_tenant());
CREATE POLICY menu_item_public_read ON menu_item FOR SELECT
    USING (
        app_tenant() = 0
        AND EXISTS (SELECT FROM menu m WHERE m.id = menu_item.menu_id AND m.status = 'PUBLISHED')
    );

-- Least privilege: data only, no DDL, and no access to Flyway's history. A table added later
-- gets its grant in its own migration; until then the application gets "permission denied"
GRANT SELECT, INSERT, UPDATE, DELETE ON
    business, business_hours, schedule_exception, item, menu, menu_item,
    app_user, membership,
    spring_session, spring_session_attributes
    TO itacatl_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO itacatl_app;
