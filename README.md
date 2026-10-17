# QA Hub

Stub server, app status board and sample payloads for QA, shipped as one Spring Boot JAR.

- **backend/**: Spring Boot 4, Java 21. Serves the stubs, the API the UI uses, and the built UI.
- **frontend/**: React + Vite. Two pages: Stubs and App status (with Samples).

## Run locally

```bash
# Terminal 1
cd backend && mvn spring-boot:run          # http://localhost:8080

# Terminal 2
cd frontend && npm install && npm run dev  # http://localhost:5173 (proxies /api and /stub to 8080)
```

## Build and deploy

```bash
./build.sh        # npm build into backend/static, then mvn package
```

Copy `backend/target/qa-hub.jar` and the `deploy/` scripts to the Linux box. Only Java 21 is needed there.

```bash
./start.sh        # nohup, logs to qa-hub.log, PORT=8085 ./start.sh to change port
./stop.sh
```

With sudo, use `deploy/qa-hub.service` instead.

### Change config without rebuilding

Files inside the JAR need a rebuild. To change things with only a restart, put copies next to the JAR:

```
/opt/qa-hub/
  qa-hub.jar
  config/stubs.yml      # overrides the packaged stubs.yml
  config/monitor.yml
  config/samples.yml
  data/preference.json  # point to it with file:./data/preference.json
```

Lists are replaced, not merged, so an override `stubs.yml` must contain every endpoint you want.

## Adding a stub endpoint

1. Add an entry under `qa-hub.stub.endpoints` in `stubs.yml`.
2. Add its data file (JSON array of records) and, optionally, sample request/response and error bodies.
3. Restart. The endpoint is live at `/stub{path}` and appears on the Stubs page automatically.

The UI reads the YAML through `GET /api/catalog`, so nothing is copied into the frontend.

### Request pipeline

| Step | Result |
|---|---|
| No endpoint for path | 404 (405 if path exists with another method) |
| `x-delay-ms: 3000` header | waits 3s, then continues |
| `x-http: 503` header | returns 503 immediately (2xx values return the normal body with that status) |
| Auth missing / wrong | 401 / 403 |
| Required header missing or not in `allowed` | 400 |
| Required param missing | 400 |
| No record matches | 404 |
| Record has `_delayMs` | waits, then responds |
| Record has `_status` | returns that status |
| Otherwise | 200 with the record (or `record[response-field]`) |

### Data file conventions

Each record is matched on the fields named by the params' `field`. Keys starting with `_` control behaviour and are never returned:

- `_status`: always return this status for this record (e.g. 503 for retry tests)
- `_delayMs`: wait this long before responding (timeout tests)
- `_note`: shown on the Stubs page

### Error bodies

Put `{code}.json` files in the endpoint's `errors` folder. Placeholders `{{status}}`, `{{message}}`, `{{path}}`, `{{timestamp}}` are filled in. Codes without a file use a default shape.

### Auth

`type`: `none`, `bearer`, `basic`, `api-key`. Set globally under `qa-hub.stub.auth`, override per endpoint with `auth:`. An empty `tokens`/`users` list accepts any value.

## App status

List apps in `monitor.yml` with their actuator health URL. All apps are pinged in parallel every `interval-ms` (30s). 2xx with `status: UP` is green, anything else is red. The QA box must be able to reach each app's management port.

## Samples

List files in `samples.yml`. They show collapsed on the App status page and load when opened. Sample files are read on every open, so `file:` samples update without a restart.

## Logo

`frontend/public/logo.svg` is a placeholder. Replace it with the official logo from your brand assets.
