# Deploying shiftapp to Railway + Neon

This app reads all environment-specific config from environment variables, with
local-dev defaults baked into `src/main/resources/application.properties`. So the
same build runs locally and in production — you only set env vars in Railway.

## 1. Create the database on Neon

1. Sign in at https://neon.tech and create a project (Postgres 16).
2. Open **Connect** on the project dashboard and copy the connection string. It
   looks like:

   ```
   postgresql://USER:PASSWORD@ep-xxxx-pooler.us-east-2.aws.neon.tech/DB?sslmode=require&channel_binding=require
   ```

3. Convert it into the pieces Spring Boot needs (note `jdbc:` prefix, keep
   `?sslmode=require`):

   | Variable                     | Value                                                                 |
   | ---------------------------- | --------------------------------------------------------------------- |
   | `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://ep-xxxx-pooler.us-east-2.aws.neon.tech/DB?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | `USER`                                                                |
   | `SPRING_DATASOURCE_PASSWORD` | `PASSWORD`                                                            |

   > Neon requires SSL. Keep `?sslmode=require` in the JDBC URL. Prefer the
   > **pooled** host (the `-pooler` one) for a web app.

## 2. Deploy the app on Railway

1. At https://railway.app create a **New Project → Deploy from GitHub repo**
   (or `railway up` from the CLI) and pick this repository.
2. Railway auto-detects the Gradle Spring Boot app via Railpack. Build/run
   commands are pinned in `railway.json`:
   - build:  `./gradlew clean bootJar -x test`
   - start:  `java -jar build/libs/shiftapp-0.0.1-SNAPSHOT.jar`
   - healthcheck: `/v3/api-docs` (public, returns 200 JSON)
3. In the service **Variables** tab, add (see `.env.example`):

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://...neon.tech/DB?sslmode=require
   SPRING_DATASOURCE_USERNAME=...
   SPRING_DATASOURCE_PASSWORD=...
   SPRING_JPA_HIBERNATE_DDL_AUTO=update
   SPRING_JPA_SHOW_SQL=false
   JWT_SECRET=<openssl rand -base64 48>
   APP_AUTH_REFRESH_COOKIE_SECURE=true
   ```

   Do **not** set `PORT` — Railway injects `$PORT` and the app binds to it via
   `server.port=${PORT:8080}`.
4. Deploy. Once healthy, your API + Swagger UI is at
   `https://<your-app>.up.railway.app/swagger-ui/index.html`.

## Notes

- `ddl-auto`: local default is `create-drop` (rebuild schema each run). On Neon
  use `update` so deploys don't wipe data. For real schema management later,
  consider Flyway/Liquibase.
- Data seeding (`app.seed.enabled=true`) populates an empty DB on first start
  using `app.seed.default-password` — change/disable for production as needed.
- Tests are unaffected: they use the in-memory H2 datasource, not these vars.

## Local development (unchanged)

Defaults still point at `localhost:5432` / `shiftapp_user` / `shiftapp_dev`.
Bring up local Postgres with `docker compose up -d` (see `docker-compose.yml`).
