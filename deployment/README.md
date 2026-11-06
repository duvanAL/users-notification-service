# Notification history in Azure

Apply notifications-access.sql as the database administrator. It creates only notifications.delivery_history and a role scoped to that schema/table, with SELECT/INSERT/UPDATE and RLS. The initial role is NOLOGIN; assign LOGIN and a password separately in the administrative UI. Do not commit passwords.

Set APP_HISTORY_ENABLED=true, NOTIFICATIONS_DATABASE_URL (jdbc:postgresql with TLS verification), NOTIFICATIONS_DATABASE_USERNAME=notifications_app, NOTIFICATIONS_DATABASE_PASSWORD (secret reference) and JWT_SECRET (same Base64 signing key as the other services, secret reference). Schema migration is explicit; starting the service does not create tables.

Expose port 8082 with internal ingress in env-users-management-demo. Only workloads in that Container Apps environment should access the history API. Keep JWT required for /api/notifications. The worker continues consuming Kafka as before, with minimum one replica. Do not scale the Kafka worker to zero using HTTP activity.

History is a secondary projection. Failure to record history is logged using event id and status only and does not interrupt email delivery or trigger a second provider call solely to save history. A database outage may therefore leave missing/stale history rows. Historical emails sent before this feature are not backfilled or reported as delivered.

Disable APP_HISTORY_ENABLED to roll back the history feature; keep schema and records. For the web BFF, use the internal service FQDN as NOTIFICATIONS_URL, never expose database access through the frontend.

The image includes the public Supabase root CA already used by File Storage. For the session pooler, use `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=verify-full&sslrootcert=/app/certs/supabase-root-2021.crt`. Use the username `notifications_app.<project-ref>`. This validates both the certificate chain and hostname; do not disable verification. The CA is public trust material, not a credential.

On Express environments, restarting a revision and assigning revision suffixes are unsupported. After changing runtime settings, verify the running process has picked them up; use the app-level stop/start operations if necessary. Verify health and Kafka partition assignment before connecting the web BFF.
