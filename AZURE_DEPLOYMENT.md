# Deploying OpenQ to Azure (Azure for Students)

This guide deploys OpenQ (Spring Boot JAR + PostgreSQL) using the Azure Portal and Azure CLI.

> **What is verified and what is not.** Items marked ✅ were checked against Microsoft Learn while writing this guide.
> Items marked ⚠️ could **not** be verified (pages did not state them, or access was blocked) — confirm them in the
> Portal / on the linked pricing page before relying on them. Prices, free offers, quotas and region availability change
> and differ per subscription.

## 0. Read this first: what is free and what is not

| Piece | Cost reality |
|---|---|
| **App Service Free (F1) plan** | No compute charge. Shared VM, **60 CPU-minutes/day, 1,024 MB memory per plan, 1 GB storage, no Always On, no custom domains, 1 shared instance** ✅ ([App Service limits](https://learn.microsoft.com/azure/azure-resource-manager/management/azure-subscription-service-limits)). Linux is listed as available for Free ✅. **F1 is a shared tier — it is not a guarantee of a dedicated 1 GB of RAM or a dedicated CPU.** |
| **Azure Database for PostgreSQL Flexible Server** | **Not free by default.** It is billed for compute (e.g. Burstable `Standard_B1ms`) and storage while it exists. A *free-account* offer exists (Burstable B1ms hours + storage for 12 months) ⚠️ but this guide could not confirm its terms, and Microsoft states the 12-month free account is not available to direct pay-as-you-go signups in India or China ✅. **Whether your Azure for Students subscription gets a free PostgreSQL tier must be checked in the Portal (look for a "Free" / "offer applied" banner on the create page) — do not assume it.** |
| **Azure for Students** | $100 credit valid 12 months, no credit card required, for education/non-commercial use ✅ ([FAQ](https://learn.microsoft.com/azure/education-hub/faq)). Credit is consumed by anything billable (such as PostgreSQL). When the credit runs out or 12 months pass, deployed resources are decommissioned, with 90 days to upgrade ✅. **Region restrictions and restricted SKUs/quotas for student subscriptions are not documented on that page** ⚠️ — you may find only some regions or sizes allowed. |

Nothing here is "free forever". If PostgreSQL turns out to cost credit, the cheapest honest options are: the smallest
Burstable size + 32 GB storage and **stopping/deleting it when not demoing** (see section I), or running PostgreSQL
locally for development. Do not swap in a non-PostgreSQL database — OpenQ's double-booking guard is a PostgreSQL
partial unique index.

---

## A. Prerequisites

1. **Azure for Students account** — sign up at <https://azure.microsoft.com/free/students> with your school email.
2. **Azure CLI** (≥ 2.60) — <https://learn.microsoft.com/cli/azure/install-azure-cli>. Check: `az version`, then `az login`
   (`az login --use-device-code` on headless machines). Check the subscription: `az account show -o table`.
3. **JDK 21** and **Maven 3.9+** — `java -version`, `mvn -version`.
4. **Git and a GitHub account** (only for the GitHub Actions option).
5. **A PostgreSQL client** (`psql`) to create the application user and inspect data —
   macOS `brew install libpq`, Ubuntu `sudo apt install postgresql-client`, Windows: PostgreSQL installer (tick "Command Line Tools").

Set shell variables once (bash shown; PowerShell equivalents in the comments). Pick your own values:

```bash
RG="openq-rg"
LOCATION="<REGION>"            # choose after the checks in B.2, e.g. centralindia / southeastasia / westeurope
APP_NAME="<APP_NAME>"          # must be globally unique: becomes <APP_NAME>.azurewebsites.net
PLAN_NAME="openq-plan"
PG_SERVER="<PG_SERVER_NAME>"   # globally unique: becomes <PG_SERVER_NAME>.postgres.database.azure.com
PG_ADMIN="pgadmin"             # admin login name for the server
# PowerShell:  $RG = "openq-rg"; $LOCATION = "<REGION>"; ...
```

---

## B. PostgreSQL database setup

### B.1 Check what your subscription allows (do this before creating anything)

```bash
az account list-locations --query "[?metadata.regionType=='Physical'].name" -o tsv | sort      # regions in the catalogue
az postgres flexible-server list-skus --location "$LOCATION" -o table                          # sizes available in that region
```

If the create step later fails with `LocationNotAvailableForResourceType`, `RequestDisallowedByPolicy` or
`SubscriptionIsOverQuotaForSku`, your student subscription restricts that region/size: try another region (and the
resource group's region should normally be the same one). Also check **Portal → Subscriptions → your subscription →
Usage + quotas**.

### B.2 Region considerations

* Put the database and the web app in the **same region** (lower latency, no cross-region data charges).
* Pick a region close to your users that your subscription allows (B.1). Free/student subscriptions often restrict regions.
* App Service F1 and PostgreSQL may be offered in different sets of regions — use one that supports both.

### B.3 Create the server — Azure Portal

1. Portal → **Create a resource** → *Azure Database for PostgreSQL flexible server* → **Create**.
2. **Basics**: Resource group `openq-rg` (create new); Server name `<PG_SERVER_NAME>`; Region = your region;
   PostgreSQL version **16**; Workload type **Development** (selects Burstable); Compute + storage →
   **Burstable, Standard_B1ms (1 vCore, 2 GiB)**, storage **32 GiB** (the smallest), *Backup retention 7 days*,
   *Geo-redundancy off*, *High availability off* (HA doubles cost).
   If the page shows a free-offer banner for your subscription, apply it — otherwise assume standard billing.
3. **Authentication**: *PostgreSQL authentication only* (simplest for the beginner path); admin username `pgadmin`; a
   strong password (store it in a password manager, never in Git).
4. **Networking**: *Public access (allowed IP addresses)*. Tick **Add current client IP address** (so you can run
   `psql`). For the app's access see B.5.
5. **Review + create**. Note the monthly estimate shown on this page — that is your real cost check.

### B.3 (alternative) — Azure CLI

```bash
az group create --name "$RG" --location "$LOCATION"

az postgres flexible-server create \
  --resource-group "$RG" --name "$PG_SERVER" --location "$LOCATION" \
  --version 16 --tier Burstable --sku-name Standard_B1ms --storage-size 32 \
  --admin-user "$PG_ADMIN" --admin-password "<PG_ADMIN_PASSWORD>" \
  --backup-retention 7 --high-availability Disabled \
  --public-access "<YOUR_PUBLIC_IP>" --yes
```

(`<YOUR_PUBLIC_IP>`: find it with `curl -s https://api.ipify.org`.) Run `az postgres flexible-server create --help`
to confirm the flags in your CLI version.

### B.4 TLS / SSL

Flexible Server requires encrypted connections by default (`require_secure_transport = on`) — leave it on. OpenQ's
JDBC URL uses `sslmode=require` (encrypts and works with the bundled Java trust store). For full server
authentication use `sslmode=verify-full` and the DigiCert/Microsoft root certificates described in the Azure docs
("Connect with TLS/SSL"); this is optional for a demo. Check the setting:

```bash
az postgres flexible-server parameter show -g "$RG" -s "$PG_SERVER" --name require_secure_transport --query value -o tsv   # on
```

### B.5 Firewall / networking

The app connects over the public endpoint, so the database firewall must allow the App Service's outbound addresses.
Two choices (public access without VNet integration):

* **Preferred for a demo with data you care about** — allow only the app's outbound IPs (after you create the web app in section C):

  ```bash
  az webapp show -g "$RG" -n "$APP_NAME" --query possibleOutboundIpAddresses -o tsv     # comma-separated list
  # one rule per IP:
  az postgres flexible-server firewall-rule create -g "$RG" -n "$PG_SERVER" \
     --rule-name "app-ip-1" --start-ip-address <APP_OUTBOUND_IP> --end-ip-address <APP_OUTBOUND_IP>
  ```
  On shared tiers these IPs can change when the app is scaled or moved — re-check after changes.
* **Simplest** — the special "Allow public access from any Azure service" rule (`0.0.0.0`–`0.0.0.0`). It lets **any**
  Azure customer's service reach your server's *endpoint* (they still need valid credentials). Acceptable for a throwaway demo only:

  ```bash
  az postgres flexible-server firewall-rule create -g "$RG" -n "$PG_SERVER" \
     --rule-name allow-azure-services --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0
  ```

Never use `0.0.0.0`–`255.255.255.255`. Production would use private access (VNet integration + private endpoint), which
needs a paid App Service tier.

### B.6 Create the database and a least-privilege application user

```bash
az postgres flexible-server db create -g "$RG" -s "$PG_SERVER" --database-name openq
psql "host=$PG_SERVER.postgres.database.azure.com port=5432 dbname=postgres user=$PG_ADMIN sslmode=require"
```

In `psql` (the admin must be a member of a role in order to create objects owned by it):

```sql
CREATE ROLE openq_app LOGIN PASSWORD '<APP_DB_PASSWORD>';
GRANT openq_app TO pgadmin;              -- replace pgadmin with your admin login
ALTER DATABASE openq OWNER TO openq_app;
REVOKE ALL ON DATABASE openq FROM PUBLIC;
GRANT CONNECT ON DATABASE openq TO openq_app;
```

* **Beginner path (default):** `openq_app` owns only the `openq` database. OpenQ runs `schema.sql` (DDL) at
  startup, so it needs to own the schema objects. It has no rights on other databases and is not the server admin.
* **Stricter path:** run `schema.sql` and `data.sql` once as the admin (`psql … -f src/main/resources/schema.sql`),
  create a role with only `SELECT, INSERT, UPDATE` on the three tables and usage on their sequences, and set the app
  setting `SPRING_SQL_INIT_MODE=never` so the app never runs DDL. You then run `data.sql` yourself when you want more future slots.

The admin account (`pgadmin`) is only for you; the web app must never use it.

### B.7 Cost and quota verification

* Portal → **Cost Management + Billing → Cost analysis**, filter by resource group `openq-rg`.
* Portal → the server → **Overview** shows the SKU and storage; **Compute + storage** shows the hourly price estimate.
* Student credit balance: <https://www.microsoftazuresponsorships.com/> ✅.
* If any budget matters, set one: Cost Management → **Budgets** → alert at 50/80/100 % of $100.

---

## C. App Service creation (Java 21)

**About F1:** a shared plan — your app runs on a VM shared with other customers' apps, with a **60-minute daily CPU
quota (and 3 CPU-minutes per 5 minutes)** ✅. If the quota is exhausted the app is stopped until the quota resets, and
because there is no Always On the app is unloaded when idle and has a **cold start** (Spring Boot start-up on shared
CPU can take a minute or more). Microsoft's limits table lists 1,024 MB memory per plan for Free ✅; treat this as a
shared limit, not a promise of dedicated RAM. OpenQ is tuned small (5 DB connections, no background jobs) but it is a
JVM — expect it to be tight on F1. If it keeps restarting or running out of quota, move to **B1 Basic** (paid, ~dedicated
1.75 GB, Always On available) — check the price in the Portal; with student credit it is usually affordable for a short demo ⚠️.

### C.1 Check availability

```bash
az webapp list-runtimes --os linux | grep -i "JAVA:"          # shows the exact strings, e.g. JAVA:21-java21
az appservice list-locations --sku F1 --linux-workers-enabled -o table   # regions where F1 Linux is offered
```

If F1 is not offered in your region or your subscription's *Free VMs* quota is 0 (error `Operation cannot be completed
without additional quota` / `... Free VMs`), choose another region, or use **B1** (Basic) instead — replace `--sku F1`
with `--sku B1` below.

### C.2 Create — Azure Portal

Portal → **Create a resource → Web App**: Subscription; Resource group `openq-rg`; Name `<APP_NAME>`; Publish **Code**;
Runtime stack **Java 21**; Java web server stack **Java SE (Embedded Web Server)**; Operating System **Linux**;
Region = your region; Pricing plan → create new → **Free F1**. Skip Database/Deployment tabs; **Review + create**.

### C.2 (alternative) — Azure CLI

```bash
az group create --name "$RG" --location "$LOCATION"          # skip if it already exists (created in B.3)

az appservice plan create --resource-group "$RG" --name "$PLAN_NAME" --location "$LOCATION" \
   --is-linux --sku F1

az webapp create --resource-group "$RG" --plan "$PLAN_NAME" --name "$APP_NAME" \
   --runtime "JAVA:21-java21"

az webapp config show -g "$RG" -n "$APP_NAME" --query linuxFxVersion -o tsv     # verify the runtime
az webapp show -g "$RG" -n "$APP_NAME" --query defaultHostName -o tsv           # the real URL host name
```

Use the exact runtime string printed by `list-runtimes`. The **default host name** may include a hash and region
suffix (newer apps), so always use the value printed above instead of assuming `<APP_NAME>.azurewebsites.net`.

OpenQ listens on the `PORT` environment variable that App Service provides for built-in runtimes ✅
(`server.port=${PORT:8080}` in `application.properties`); you do not set a port yourself. Force HTTPS-only:

```bash
az webapp update -g "$RG" -n "$APP_NAME" --https-only true
```

---

## D. Application settings

OpenQ reads three settings, which App Service exposes to the JVM as environment variables:

| Setting | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<DB_HOST>.postgres.database.azure.com:5432/openq?sslmode=require` |
| `DB_USERNAME` | `openq_app` (the plain role name — flexible server does not use the old `user@server` form) |
| `DB_PASSWORD` | the password of `openq_app` |

**JDBC URL notes:** host = `<PG_SERVER_NAME>.postgres.database.azure.com`; port `5432`; database name `openq`;
`sslmode=require` forces TLS. The password is a *separate* setting, so it needs **no URL encoding**. If you ever put
credentials inside the URL (`?user=…&password=…`) you must percent-encode special characters (`@`→`%40`, `#`→`%23`,
`/`→`%2F`, `%`→`%25`) — avoid doing this.

### Portal

Web App → **Settings → Environment variables → App settings → + Add**: add the three names above (and the optional
ones below) → **Apply** → **Confirm** (the app restarts).

### CLI

```bash
az webapp config appsettings set -g "$RG" -n "$APP_NAME" --settings \
  DB_URL="jdbc:postgresql://<DB_HOST>.postgres.database.azure.com:5432/openq?sslmode=require" \
  DB_USERNAME="openq_app" \
  DB_PASSWORD="<DB_PASSWORD>" \
  JAVA_OPTS="-Xms96m -Xmx320m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m" \
  WEBSITES_CONTAINER_START_TIME_LIMIT=600
```

* `JAVA_OPTS` ✅ is passed to the `java` command by App Service; by default the max heap is 70 % of the plan's memory
  ✅. The values above are a conservative **starting point I could not test on F1** — adjust if you see out-of-memory kills.
* `WEBSITES_CONTAINER_START_TIME_LIMIT` ✅ extends how long the platform waits for the container to come up
  (default ≈ 230–240 s, max 1800 s; the docs describe it for custom containers, so ⚠️ confirm it helps with Java SE).
  Useful because Spring Boot starts slowly on shared CPU.
* Type the password directly in your terminal, or read it from a secure prompt; do not commit it or paste it into
  workflow files. In PowerShell wrap values containing `&`, `;` or `$` in single quotes. Bash users: prefix the command
  with a space or use `read -s DBPW` and `DB_PASSWORD="$DBPW"` to keep it out of shell history.

### Application settings vs Key Vault

App Service *Application settings* are encrypted at rest and shown only to people with access to the app, which is fine
for this demo. **Key Vault references** (`@Microsoft.KeyVault(SecretUri=...)`) keep the secret in a vault with its own
access policy, rotation and audit log, and let the app read it through a **managed identity** — the right choice once
the data matters. It adds moving parts (a vault, an identity, role assignments) and may incur small charges, so it is
not part of the beginner path. The next step beyond that is passwordless Microsoft Entra authentication to PostgreSQL
using the same managed identity.

---

## E. Deployment option 1 — GitHub Actions

The workflow is already in the repository: `.github/workflows/deploy.yml`. On every push to `main` it: checks out the
code → sets up JDK 21 with Maven caching → runs **all tests** (`mvn verify`, including the PostgreSQL integration tests
against a throw-away PostgreSQL service container) → builds `target/openq.jar` → uploads it → a second job logs into
Azure and deploys that exact JAR with `azure/webapps-deploy@v3`. App Service renames a OneDeploy-deployed JAR to `app.jar`
in `/home/site/wwwroot` and runs it ✅ — you do not need a custom startup command.

### E.1 Authentication: OpenID Connect (recommended)

OIDC lets GitHub request a short-lived token — **no password or publish profile is stored** ✅. Create an Entra app +
federated credential once:

```bash
SUB_ID=$(az account show --query id -o tsv)
az ad app create --display-name "openq-github-deploy" --query appId -o tsv        # -> <CLIENT_ID>  (AZURE_CLIENT_ID)
az ad sp create --id <CLIENT_ID>
SP_OBJECT_ID=$(az ad sp show --id <CLIENT_ID> --query id -o tsv)

# Allow it to deploy to ONLY this web app (least privilege):
az role assignment create --assignee-object-id "$SP_OBJECT_ID" --assignee-principal-type ServicePrincipal \
  --role "Website Contributor" \
  --scope "/subscriptions/$SUB_ID/resourceGroups/$RG/providers/Microsoft.Web/sites/$APP_NAME"

# Trust GitHub tokens from your repo's main branch:
cat > credential.json <<'EOF'
{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:<GITHUB_USER>/<REPO>:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}
EOF
az ad app federated-credential create --id <CLIENT_ID> --parameters credential.json
```

(Microsoft's page is inconsistent about whether `--id` takes the app's *object ID* or *client ID* ⚠️; if one is
rejected, try the other. `az ad app show --id <CLIENT_ID> --query id -o tsv` gives the object ID.)

Where each secret value comes from:

| GitHub setting (Settings → Secrets and variables → Actions) | Value | How to get it |
|---|---|---|
| Secret `AZURE_CLIENT_ID` | Application (client) ID | output of `az ad app create` (`appId`) |
| Secret `AZURE_TENANT_ID` | Directory (tenant) ID | `az account show --query tenantId -o tsv` |
| Secret `AZURE_SUBSCRIPTION_ID` | Subscription ID | `az account show --query id -o tsv` |
| **Variable** `AZURE_WEBAPP_NAME` | your `<APP_NAME>` | the name from section C |

**Student-account caveat ⚠️:** a school-managed Microsoft Entra tenant may block students from registering apps
(`Insufficient privileges`). If so, use the fallback below.

### E.2 Fallback: publish-profile authentication

1. Enable basic-auth publishing for SCM (disabled by default on new apps):
   ```bash
   az resource update --resource-group "$RG" --name scm --namespace Microsoft.Web \
     --resource-type basicPublishingCredentialsPolicies --parent sites/$APP_NAME --set properties.allow=true
   ```
2. Download the profile: `az webapp deployment list-publishing-profiles -g "$RG" -n "$APP_NAME" --xml > profile.xml`
   (or Portal → Web App → **Download publish profile**). Paste the file's contents into a GitHub secret named
   `AZURE_WEBAPP_PUBLISH_PROFILE`, then **delete `profile.xml` locally** — it contains a password.
3. In `deploy.yml`, in the `deploy` job, delete the `azure/login` and `az logout` steps and the `id-token: write`
   permission, and give the deploy step `publish-profile: ${{ secrets.AZURE_WEBAPP_PUBLISH_PROFILE }}`.

Publish profiles are long-lived passwords; rotate them (Portal → **Reset publish profile**) if they leak.

### E.3 Run it

```bash
git init && git add . && git commit -m "OpenQ"
git branch -M main
git remote add origin https://github.com/<GITHUB_USER>/<REPO>.git
git push -u origin main
```

Watch the **Actions** tab. The `build` job needs no Azure access; the `deploy` job needs the secrets above.

---

## F. Deployment option 2 — manual (Azure CLI)

```bash
# 1. Tests (unit tests only; add the PostgreSQL integration tests by exporting TEST_DB_* and omitting -DskipITs)
mvn -B clean test

# 2. Build the executable JAR (skip ITs unless a test database is available)
mvn -B clean package -DskipITs

# 3. Verify the artifact exists and is a Spring Boot executable JAR
ls -l target/openq.jar                          # PowerShell: Get-Item target\openq.jar
unzip -p target/openq.jar META-INF/MANIFEST.MF | grep -E "Main-Class|Start-Class"   # JarLauncher + com.openq.OpenQApplication
#   (no unzip? "jar tf target/openq.jar | head" also works)

# 4. Deploy (OneDeploy; renames the file to app.jar on the server)
az login
az webapp deploy --resource-group "$RG" --name "$APP_NAME" --src-path target/openq.jar --type jar

# 5. Make sure settings are applied, then restart
az webapp restart --resource-group "$RG" --name "$APP_NAME"

# 6. Logs
az webapp log config --resource-group "$RG" --name "$APP_NAME" --application-logging filesystem --level information
az webapp log tail   --resource-group "$RG" --name "$APP_NAME"              # Ctrl+C to stop
#   or download:  az webapp log download -g "$RG" -n "$APP_NAME" --log-file logs.zip

# 7. Verify using the real URL (first request after idle can take a minute - cold start)
HOST=$(az webapp show -g "$RG" -n "$APP_NAME" --query defaultHostName -o tsv)
curl -i "https://$HOST/doctors"
curl -s "https://$HOST/api/doctors/1/slots" | head -c 400
```

No Maven Azure plugin is needed. If `az webapp deploy` times out on first deploy, wait a few minutes and re-check the
log stream — start-up on F1 is slow.

---

## G. Post-deployment verification checklist

- [ ] **App starts** — `az webapp log tail` shows `Started OpenQApplication in … seconds`; `https://<HOST>/` returns 200.
- [ ] **PostgreSQL connection works** — no `Connection refused` / `password authentication failed` in logs; `/doctors` lists data.
- [ ] **Doctor directory renders** — four doctors with open-slot counts.
- [ ] **Booking works** — pick a slot, submit the form, land on the confirmation page with ID, doctor, date, time, patient.
- [ ] **Booked slot disappears** — reload that doctor's slots; the slot is gone (and the doctor's count dropped by one).
- [ ] **Second booking attempt is rejected** — open the same booking-form URL in two tabs before booking in either; submit
      the second → friendly "slot has just been taken" page (HTTP 409).
- [ ] **Cancellation restores availability** — `/admin` → *Cancel*; the slot reappears and can be booked again.
- [ ] **Restart does not duplicate seed data** — `az webapp restart …`, then check:
      `psql … -c "SELECT count(*) FROM doctor;"` is still `4` and slot counts did not double.
- [ ] **Logs do not reveal secrets** — search the log stream/download for your DB password and for patient e-mails (`grep -i`);
      OpenQ logs neither, and Hibernate SQL logging is off.
- [ ] **HTTPS only** and the DB firewall is not `0.0.0.0–255.255.255.255`.

---

## H. Troubleshooting

| Symptom | Causes and fixes |
|---|---|
| **403** "Web app is stopped" / quota exceeded | F1's 60 CPU-minutes/day quota is used up, or the app is stopped. Wait for the reset, `az webapp start`, or scale to B1. Also check **IP restrictions** (Networking → Access restrictions) and that SCM basic auth is enabled if you deploy with a publish profile. |
| **404** | Wrong host name (use `defaultHostName`), wrong path (`/doctors`, not `/doctor`), or the JAR is not running (check logs). |
| **500** | Application error. Look in the log stream. A generic OpenQ error page means the exception was logged server-side. Often DB credentials/connectivity or a schema mismatch. |
| **503** "Service Unavailable" | Container still starting (cold start/slow CPU), crashed, or is being restarted. Raise `WEBSITES_CONTAINER_START_TIME_LIMIT`, reduce `-Xmx`, check logs for `OutOfMemoryError`. A 503 *page from OpenQ* ("system is busy… try again") is the 5 s row-lock timeout — retry. |
| Java runtime mismatch (`UnsupportedClassVersionError`, class file version 65) | The plan runs an older Java than the JAR (built with 21). Check `az webapp config show … --query linuxFxVersion` → must be `JAVA|21-java21`. Fix with `az webapp config set -g $RG -n $APP_NAME --linux-fx-version "JAVA|21-java21"`, or build with `<java.version>17</java.version>` and use Java 17. |
| Container fails health/warm-up ("didn't respond to HTTP pings on port") | App not listening on `PORT` (don't override `server.port`), start-up too slow (raise start-time limit), or crash on startup (DB unreachable). Read `az webapp log tail`. |
| Wrong startup / old JAR running | Deploy with `az webapp deploy --type jar` or `azure/webapps-deploy` (renames to `app.jar`). Avoid FTP (does not rename). If you set a custom Startup Command earlier, clear it (Configuration → General settings). |
| PostgreSQL firewall: `no pg_hba.conf entry for host "x.x.x.x"` / timeout | Add the web app's outbound IPs (B.5) or the `0.0.0.0` rule; re-check after plan changes. For `psql` from home add your own IP. |
| TLS errors (`SSL error`, `server does not support SSL`, certificate problems) | Keep `sslmode=require` in `DB_URL`. If you used `verify-full`, the root certificate must be in the trust store. |
| Invalid JDBC URL (`Driver … claims to not accept jdbcUrl`) | URL must start `jdbc:postgresql://`; no spaces/quotes in the setting value; `?sslmode=require` after the DB name. |
| `remaining connection slots are reserved` / pool timeouts | Burstable B1ms allows a limited number of connections. OpenQ's pool is 5; don't run many instances/other clients. Restarting the app releases connections. |
| `Schema-validation: missing table …` or wrong column | The database was created by something else, or an old schema exists. Drop/recreate the `openq` database (demo data only!) or align the schema. Check `DB_URL` points to the right database. |
| `permission denied for schema public` / `must be owner` | The app user must own the `openq` database (B.6) because `schema.sql` runs at startup; or run the stricter path. |
| `Operation cannot be completed without additional quota` / `RequestDisallowedByPolicy` / `LocationNotAvailableForResourceType` | Subscription quota or policy. Try another region, another SKU (F1 → B1), or request quota (Portal → Help + support). Student subscriptions are restricted ⚠️. |

---

## I. Cost and security checklist

**Cost**
- [ ] Portal → **Cost Management → Cost analysis**, group by *Resource*; check daily. Set a **budget alert**.
- [ ] Check credit at <https://www.microsoftazuresponsorships.com/> ✅.
- [ ] **PostgreSQL bills while it exists and is running, and storage is billed even when stopped.** Stop it when not in use:
      `az postgres flexible-server stop -g $RG -n $PG_SERVER` (Azure restarts a stopped server automatically after up to 7 days ⚠️ — check),
      or delete it when you are done.
- [ ] **Stopping or deleting the App Service does NOT stop PostgreSQL billing.** They are separate resources.
- [ ] Remove everything when finished (deletes the web app, plan, database server and all data — irreversible):
      ```bash
      az group delete --name "$RG" --yes --no-wait
      ```
- [ ] Look for leftovers: `az resource list -o table` and the Portal's *All resources* page.
- [ ] Don't enable High Availability, geo-redundant backups, larger SKUs, Always On or extra instances unless you accept the cost.

**Security**
- [ ] Use HTTPS-only; keep `require_secure_transport` on.
- [ ] No secrets in Git, workflow files or screenshots; `.env` is git-ignored; rotate any password that leaked.
- [ ] App uses `openq_app`, never the admin account. Restrict the DB firewall to the app's IPs where possible.
- [ ] **The `/admin` URL is not secure.** It has no login: anyone who knows or guesses the address can read every patient's
      name, e-mail and phone number and cancel any appointment, and the confirmation URLs are guessable sequential IDs.
      Treat all data as public. Use only fake data. Real patient data would require authentication, authorization,
      audit logging, encryption/key management, privacy controls and legal compliance, none of which OpenQ has.
- [ ] Optionally restrict `/admin` while demoing: Web App → **Networking → Access restrictions** to your own IP.
      That is a network filter for demos, not an application security control.

---

### Sources

- [App Service limits (Free tier column)](https://learn.microsoft.com/azure/azure-resource-manager/management/azure-subscription-service-limits)
- [Configure a Java app for Azure App Service](https://learn.microsoft.com/azure/app-service/configure-language-java-deploy-run)
- [App Service on Linux FAQ (PORT, warm-up time limit)](https://learn.microsoft.com/azure/app-service/faq-app-service-linux)
- [App Service hosting plans overview](https://learn.microsoft.com/azure/app-service/overview-hosting-plans)
- [Deploy to App Service with GitHub Actions (OIDC)](https://learn.microsoft.com/azure/app-service/deploy-github-actions)
- [Azure Education Hub FAQ (Azure for Students credit)](https://learn.microsoft.com/azure/education-hub/faq)
- [Azure free services page (free-account eligibility)](https://azure.microsoft.com/pricing/free-services)
