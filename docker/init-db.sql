-- postgres only lets you specify one database at container creation time via
-- POSTGRES_DB, and each service here wants its own database, so the rest get
-- created here on first startup
CREATE DATABASE fleetpulse_telemetry;
CREATE DATABASE fleetpulse_health;
CREATE DATABASE fleetpulse_alert;
