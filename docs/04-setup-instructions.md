# Setup Instructions

The README provides a project overview. This document contains the complete
installation, execution, validation, and troubleshooting steps.

## 1. Execution Options

| Option | Use case | Requirements |
|---|---|---|
| **Standalone Docker image** | Quick functional review | Docker |
| **Full scalable Docker stack** | Review PostgreSQL, Redis, app1, app2, and NGINX | Docker and Git |
| **Local development** | Modify or debug the source code | Java 17 and Git |

---

## 2. Option 1 — Standalone Docker Image

This option runs one Spring Boot container with the default H2 profile.

It supports URL creation, redirects, aliases, expiration, analytics, and the
browser interface. It does not include PostgreSQL, Redis, NGINX, `app1`, or
`app2`.

No repository clone or `.env` file is required.

### macOS/Linux

```bash
docker pull aditiv0401/ai-assisted-url-shortener:1.0.0

docker run -d \
  --name url-shortener \
  -p 8080:8080 \
  -e APP_BASE_URL=http://localhost:8080 \
  -e INSTANCE_NAME=standalone \
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
  aditiv0401/ai-assisted-url-shortener:1.0.0
```

Open:

```text
http://localhost:8080
```

Health check:

```bash
curl -i http://localhost:8080/actuator/health
```

Windows PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Stop:

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

The repository is required because `compose.release.yml` uses the included
NGINX configuration.

### Clone the repository

```bash
git clone https://github.com/aditi040306/ai-assisted-url-shortener.git
cd ai-assisted-url-shortener
```

### macOS/Linux

Set local demonstration credentials:

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

Set local demonstration credentials:

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

### Check status

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

Open:

```text
http://localhost:8080
```

View logs:

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

Stop:

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

### Prerequisites

- Java 17
- Git
- IntelliJ IDEA or another Java IDE

The Maven Wrapper is included, so Maven does not need to be installed
separately.

### Run tests

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

### Start the H2 profile

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

### Create a short URL

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

Expected status:

```text
HTTP/1.1 201
```

Copy the returned `shortCode`.

### Test the redirect

```bash
curl -i http://localhost:8080/YOUR_CODE
```

Expected result:

```text
HTTP/1.1 302
Location: https://example.com/setup-test
```

### Test aggregate analytics

```bash
curl -i http://localhost:8080/api/v1/urls/YOUR_CODE/analytics
```

Expected status:

```text
HTTP/1.1 200
```

### Test detailed analytics

```bash
curl -i http://localhost:8080/api/v1/urls/YOUR_CODE/click-analytics
```

Detailed events are asynchronous, so the response may briefly lag behind the
aggregate click count.

### Test a custom alias

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl":"https://example.com/custom",
    "customAlias":"My_Link"
  }'
```

Expected returned short code:

```text
my_link
```

A second request using the same alias should return `409 Conflict`.

---

## 6. Validate the Scalable Stack

These checks apply only to Option 2.

### Verify request distribution

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

### Docker is not running

Start Docker Desktop and wait until this succeeds:

```bash
docker info
```

### Port 8080 is already in use

macOS/Linux:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Windows PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 8080
```

### A container exits

macOS/Linux:

```bash
docker compose -p url-shortener -f compose.release.yml ps -a
docker compose -p url-shortener -f compose.release.yml \
  logs --tail=100 SERVICE_NAME
```

Windows PowerShell:

```powershell
docker compose -p url-shortener -f .\compose.release.yml ps -a
docker compose -p url-shortener -f .\compose.release.yml `
  logs --tail=100 SERVICE_NAME
```

### Clean scalable-stack restart

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
- URL creation, redirect, and analytics work.
- All five services start in the full scalable stack.
- Both `app1` and `app2` appear in response headers.
- `.env` is not committed if one is created locally.
- `.env.example` is tracked.
- Documentation links work.
- The Git working tree is clean.
