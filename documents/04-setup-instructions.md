# Setup Instructions

The README provides the project overview. This document contains the complete
installation, execution, validation, and troubleshooting steps.

## 1. Execution Options

| Option | Use case | Requirements |
|---|---|---|
| **Standalone Docker image** | Quick functional review | Docker |
| **Full scalable Docker stack** | Review PostgreSQL, Redis, app1, app2, and NGINX | Docker and Git |
| **Local development** | Modify or debug the source code | Java 17 and Git |

---

## 2. Option 1 — Standalone Docker Image

This option runs one Spring Boot container using the default H2 profile.

It supports URL creation, redirects, custom aliases, expiration, aggregate and
detailed analytics, and the browser interface.

It does not start PostgreSQL, Redis, NGINX, `app1`, or `app2`.

No repository clone or `.env` file is required.

The Redis health indicator is disabled because Redis is not used in standalone
mode. This allows `/actuator/health` and the browser health check to report the
standalone application as `UP`.

### macOS/Linux

```bash
docker pull aditiv0401/ai-assisted-url-shortener:1.0.0

docker run -d \
  --name url-shortener \
  -p 8080:8080 \
  -e APP_BASE_URL=http://localhost:8080 \
  -e INSTANCE_NAME=standalone \
  -e MANAGEMENT_HEALTH_REDIS_ENABLED=false \
  aditiv0401/ai-assisted-url-shortener:1.0.0
```

### Windows PowerShell

```powershell
docker pull aditiv0401/ai-assisted-url-shortener:1.0.0

docker run -d `
  --name url-shortener `
  -p 8080:8080 `
  -e APP_BASE_URL=http://localhost:8080 `
  -e INSTANCE_NAME=standalone `
  -e MANAGEMENT_HEALTH_REDIS_ENABLED=false `
  aditiv0401/ai-assisted-url-shortener:1.0.0
```

### Verify

Open:

```text
http://localhost:8080
```

macOS/Linux:

```bash
curl -i http://localhost:8080/actuator/health
```

Windows PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected health status:

```text
UP
```

View logs:

```bash
docker logs -f url-shortener
```

Stop and remove:

```bash
docker stop url-shortener
docker rm url-shortener
```

---

## 3. Option 2 — Full Scalable Docker Stack

This option starts:

```text
PostgreSQL
Redis
Spring Boot app1
Spring Boot app2
NGINX
```

Both application instances use the published application image and share
PostgreSQL and Redis.

The repository is required because `compose.release.yml` uses the included
NGINX configuration under `infra/nginx/nginx.conf`.

### Prerequisites

- Docker Desktop or Docker Engine
- Docker Compose
- Git

Verify Docker:

```bash
docker --version
docker compose version
docker info
```

### Clone the Repository

```bash
git clone https://github.com/aditi040306/ai-assisted-url-shortener.git
cd ai-assisted-url-shortener
```

### macOS/Linux

Set local demonstration credentials in the current terminal:

```bash
export DB_USERNAME=urlshortener
export DB_PASSWORD=urlshortener_demo
```

Start:

```bash
docker compose \
  -p url-shortener \
  -f compose.release.yml \
  up -d
```

### Windows PowerShell

Set local demonstration credentials in the current PowerShell session:

```powershell
$env:DB_USERNAME="urlshortener"
$env:DB_PASSWORD="urlshortener_demo"
```

Start:

```powershell
docker compose `
  -p url-shortener `
  -f .\compose.release.yml `
  up -d
```

### Check Status

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml ps
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml ps
```

Expected services:

```text
postgres
redis
app1
app2
nginx
```

### Open and Check Health

Open:

```text
http://localhost:8080
```

macOS/Linux:

```bash
curl -i http://localhost:8080/actuator/health
```

Windows PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

### View Logs

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml \
  logs -f app1 app2 nginx
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml `
  logs -f app1 app2 nginx
```

### Stop

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml down
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml down
```

Use `down -v` to also remove PostgreSQL and Redis volumes.

> The credentials above are local demonstration values. Do not use real
> production credentials directly in shell commands.

---

## 4. Option 3 — Local Development

Use this option to modify, debug, or test the source code.

### Prerequisites

- Java 17
- Git
- IntelliJ IDEA or another Java IDE

The Maven Wrapper is included, so Maven does not need to be installed
separately.

Verify Java:

```bash
java -version
```

### Run Tests

macOS/Linux:

```bash
chmod +x mvnw
./mvnw clean test
```

Windows PowerShell:

```powershell
.\mvnw.cmd clean test
```

Expected result:

```text
BUILD SUCCESS
```

### Start the H2 Profile

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

Stop with `Control+C`.

---

## 5. Validate the Application

The following tests work with all three execution options.

### Create a Short URL

macOS/Linux:

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/setup-test"}'
```

Windows PowerShell:

```powershell
curl.exe -i -X POST http://localhost:8080/api/v1/urls `
  -H "Content-Type: application/json" `
  -d "{\"originalUrl\":\"https://example.com/setup-test\"}"
```

Expected:

```text
HTTP/1.1 201
```

Copy the returned `shortCode`.

### Test Redirect

macOS/Linux:

```bash
curl -i http://localhost:8080/YOUR_CODE
```

Windows PowerShell:

```powershell
curl.exe -i http://localhost:8080/YOUR_CODE
```

Expected:

```text
HTTP/1.1 302
Location: https://example.com/setup-test
```

### Test Aggregate Analytics

macOS/Linux:

```bash
curl -i http://localhost:8080/api/v1/urls/YOUR_CODE/analytics
```

Windows PowerShell:

```powershell
curl.exe -i http://localhost:8080/api/v1/urls/YOUR_CODE/analytics
```

Expected:

```text
HTTP/1.1 200
```

### Test Detailed Analytics

macOS/Linux:

```bash
curl -i http://localhost:8080/api/v1/urls/YOUR_CODE/click-analytics
```

Windows PowerShell:

```powershell
curl.exe -i http://localhost:8080/api/v1/urls/YOUR_CODE/click-analytics
```

Detailed events are asynchronous, so this result may briefly lag behind the
aggregate click count.

### Test a Custom Alias

macOS/Linux:

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl":"https://example.com/custom",
    "customAlias":"My_Link"
  }'
```

Windows PowerShell:

```powershell
curl.exe -i -X POST http://localhost:8080/api/v1/urls `
  -H "Content-Type: application/json" `
  -d "{\"originalUrl\":\"https://example.com/custom\",\"customAlias\":\"My_Link\"}"
```

Expected returned short code:

```text
my_link
```

A second request using the same alias should return:

```text
409 Conflict
```

---

## 6. Validate the Scalable Stack

These checks apply only to Option 2.

### Verify Request Distribution

macOS/Linux:

```bash
for i in {1..10}; do
  curl -s -D - http://localhost:8080/actuator/health \
    -o /dev/null | grep -i X-App-Instance
done
```

Windows PowerShell:

```powershell
1..10 | ForEach-Object {
  curl.exe -s -D - http://localhost:8080/actuator/health -o NUL |
    Select-String "X-App-Instance"
}
```

Responses should include both:

```text
X-App-Instance: app1
X-App-Instance: app2
```

### Inspect Redis

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml \
  exec redis redis-cli GET redirect:YOUR_CODE
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml `
  exec redis redis-cli GET redirect:YOUR_CODE
```

The expected value is the original URL after a successful redirect.

---

## 7. Troubleshooting

### Docker Is Not Running

Start Docker Desktop and wait until this succeeds:

```bash
docker info
```

### Standalone Health Reports `DOWN`

Confirm that the standalone container was started with:

```text
MANAGEMENT_HEALTH_REDIS_ENABLED=false
```

Recreate it if necessary:

```bash
docker rm -f url-shortener
```

Then run the complete standalone command from Section 2 again.

### Port 8080 Is Already in Use

macOS/Linux:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Windows PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 8080
```

### A Container Exits

Standalone:

```bash
docker logs --tail=100 url-shortener
```

Scalable stack on macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml ps -a
docker compose -p url-shortener -f compose.release.yml \
  logs --tail=100 SERVICE_NAME
```

Scalable stack on Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml ps -a
docker compose -p url-shortener -f .\compose.release.yml `
  logs --tail=100 SERVICE_NAME
```

### Clean Scalable-Stack Restart

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml down -v
docker compose -p url-shortener -f compose.release.yml up -d
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml down -v
docker compose -p url-shortener -f .\compose.release.yml up -d
```

---

## 8. Final Submission Checks

Confirm that:

- The Maven test suite passes.
- The standalone Docker image starts without cloning the repository.
- The standalone browser health check reports `UP`.
- URL creation, redirect, and analytics work.
- All five services start in the scalable stack.
- Both `app1` and `app2` appear in scalable response headers.
- `.env` is not committed if one is created locally.
- `.env.example` remains tracked.
- Documentation links work.
- The Git working tree is clean.
