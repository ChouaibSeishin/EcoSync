-- The Phase 1 runtime only reads identity/workspace metadata.
-- Provisioning and migration writes belong to the separate setup process.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ecosync_runtime') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA app FROM ecosync_runtime;
        GRANT SELECT ON app.organization, app.app_user, app.membership, app.role_assignment TO ecosync_runtime;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ecosync_migrator') THEN
            ALTER DEFAULT PRIVILEGES FOR ROLE ecosync_migrator IN SCHEMA app
                REVOKE INSERT, UPDATE, DELETE ON TABLES FROM ecosync_runtime;
        END IF;
    END IF;
END $$;
