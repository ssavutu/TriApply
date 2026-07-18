# triapply-portal deployment notes

## Running the portal on a VPS

The portal is a self-contained JVM server (Ring/Jetty). The uberjar bundles the
CSS/ClojureScript assets, so deploying is: build → ship one jar → run it under
systemd on a local port → reverse-proxy a subdomain to it with TLS.

### 1. Build (on your workstation)
```bash
npm install
npm run build   # builds assets, then `lein uberjar`
# -> target/uberjar/triapply-portal-0.1.0-SNAPSHOT-standalone.jar
```

### 2. Provision the server (once)
```bash
sudo apt install -y openjdk-21-jre-headless nginx    # JRE 17+ required (Jetty 12)
sudo useradd --system --home /opt/triapply triapply
sudo mkdir -p /opt/triapply /var/lib/triapply/uploads
sudo chown -R triapply:triapply /opt/triapply /var/lib/triapply/uploads
```

### 3. Ship the jar + env
```bash
scp target/uberjar/triapply-portal-*-standalone.jar \
    you@server:/opt/triapply/triapply-portal-standalone.jar
# Copy example.env, fill it in, and place it as /opt/triapply/portal.env
# (chmod 600, owned by triapply — it holds the Gmail OAuth creds and bot token).
```
For a first test you only need the Slack (`BOT_TOKEN`, `CHANNEL`,
`SIGNING_SECRET`) and Gmail (`TRIAPPLY_GMAIL_CLIENT_ID`/`_SECRET`/
`_REFRESH_TOKEN`) vars set; leave ATS/Sheet/storage unset and those sinks stay
off.

> Email goes out over the Gmail HTTPS API (port 443), not SMTP — most cloud hosts
> (DigitalOcean included) block outbound SMTP ports, so SMTP silently times out.
> It sends as the `notify@thetriangle.org` Google mailbox, so no domain DNS
> verification is needed. One-time: enable the Gmail API on a Google Cloud OAuth
> client and mint a refresh token for notify@ with the `https://mail.google.com/`
> scope (the OAuth Playground is the quickest way).

### 4. Run it under systemd
Install [triapply-portal.service](triapply-portal.service) to
`/etc/systemd/system/`, then:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now triapply-portal
journalctl -u triapply-portal -f        # expect "listening on http://localhost:4321"
```

### 5. Reverse proxy + TLS
Install [portal.nginx.conf](portal.nginx.conf) (edit the `server_name` to your
subdomain), then:
```bash
sudo certbot --nginx -d apply.example.com   # fills in the cert lines + :80 redirect
sudo nginx -t && sudo systemctl reload nginx
```
Confirm `https://apply.example.com/` shows the form.

### 6. Point Slack at it
In your Slack app → **Interactivity & Shortcuts** → Request URL:
`https://apply.example.com/slack/interactions`. Submit a test application, click
**Accept/Deny**, fill the modal, and confirm the email arrives.

### Redeploying a new jar
```bash
scp .../standalone.jar you@server:/opt/triapply/triapply-portal-standalone.jar
sudo systemctl restart triapply-portal
```

> Note: this proxy serves the **public** portal (form + Slack callback) — no auth.
> The separate résumé host below (`files.triangle.org`) is the Authentik-gated one.


## Applicant uploads → files.triangle.org (Authentik-gated)

The portal persists uploaded PDFs to `TRIAPPLY_UPLOAD_DIR` and records
`https://files.triangle.org/<submissionId>/<file>` URLs into the Google Sheet's
`files` column (never into Slack, per reviewer preference). **The portal
enforces no auth on those files** — access is gated by Authentik forward-auth at
the `files.triangle.org` reverse proxy.

### The one thing that must line up
The nginx `root` and the portal's `TRIAPPLY_UPLOAD_DIR` must be the **same
directory**, and nginx must be able to read it:

| Side | Setting |
|------|---------|
| Portal | `TRIAPPLY_UPLOAD_DIR=/var/lib/triapply/uploads` |
| nginx  | `root /var/lib/triapply/uploads;` (in [files-triangle-org.nginx.conf](files-triangle-org.nginx.conf)) |
| base URL | `TRIAPPLY_FILE_BASE_URL=https://files.triangle.org` (default) |

If the portal and the file host are different machines, point
`TRIAPPLY_UPLOAD_DIR` at shared/object storage that nginx also serves.

### Auth
See the header of [files-triangle-org.nginx.conf](files-triangle-org.nginx.conf)
for the Authentik Proxy Provider + embedded-outpost setup. Current policy: any
authenticated Authentik user. To restrict to an `editors`/`recruiting` group
later, bind a group policy to the Application in Authentik — **no nginx or
portal change required**.

## Google Sheet audit trail (Apps Script web app)

The Sheet sink ([../scripts/sheets-webhook.gs](../scripts/sheets-webhook.gs)) is
a Google Apps Script Web App bound to the Sheet. The portal POSTs each
submission to it, and the same script later stamps the decision back onto the
matching row.

Columns are **header-driven**: it seeds meta columns
(`submissionId · receivedAt · sections · files · decision · position · decidedAt`)
and then gives **each answer/supplemental field its own column**, created the
first time that field name appears. The layout adapts to the form automatically.
Because headers are dynamic, start from a **fresh/empty tab** — an old tab with
the previous `answers`/`supplementals` JSON columns will keep those stale columns
alongside the new per-field ones.

### First deploy
1. Open the target Google Sheet → **Extensions → Apps Script**.
2. Replace the default `Code.gs` with the full contents of
   [../scripts/sheets-webhook.gs](../scripts/sheets-webhook.gs); **Save**.
3. **Deploy → New deployment** → type **Web app**:
   - **Execute as:** `Me`
   - **Who has access:** `Anyone` (the unguessable `/exec` URL is the shared secret)
4. **Deploy**, then authorize when prompted (for an unverified personal script,
   **Advanced → Go to <project> (unsafe) → Allow** is expected).
5. Copy the Web app URL (ends in `/exec`) into `TRIAPPLY_SHEET_WEBHOOK_URL`.

### Redeploying after script edits
The `/exec` URL keeps serving the **old code** until you cut a new version:
**Deploy → Manage deployments →** ✏️ edit the existing deployment **→ Version:
New version → Deploy** (the URL is unchanged). Do this whenever
`sheets-webhook.gs` changes, otherwise new columns/behavior won't take effect.

### Idempotency
The portal sends a stable `submissionId` per application (a hidden form field
that survives browser resubmits). The script appends a row only for an unseen
id and no-ops on repeats, so retries after a partial-delivery failure never
duplicate rows.
