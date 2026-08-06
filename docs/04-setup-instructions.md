# Setup Instructions

## 1. Prerequisites

The project requires:

- Java 17
- Git
- Docker Desktop
- Docker Compose
- `curl`
- IntelliJ IDEA or another Java IDE

The project includes the Maven Wrapper, so a separate Maven installation is
not required.

Verify the tools:

```bash
java -version
docker --version
docker compose version
docker info
```

## 2. Run the automated tests

From the repository root, run:

```bash
chmod +x mvnw
./mvnw clean test
```

The expected final result is:

```text
BUILD SUCCESS
```

## 3. Run the default local profile

The default profile uses H2 and `NoOpRedirectCache`.

Start the application:

```bash
./mvnw spring-boot:run
```

Open the browser interface:

```text
http://localhost:8080
```

Check health:

```bash
curl -i http://localhost:8080/actuator/health
```

The response should include:

```text
X-App-Instance: local-instance
```

Stop the application with `Control+C`.

## 4. Run the scalable Docker profile

Copy the environment template:

```bash
cp .env.example .env
```

Do not commit `.env`. Confirm that `.gitignore` contains:

```text
.env
```

The scalable application properties use these environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
APP_BASE_URL
INSTANCE_NAME
REDIS_HOST
REDIS_PORT
```

Validate the Compose configuration:

```bash
docker compose config
docker compose config --services
```

The expected services are:

```text
postgres
redis
app1
app2
nginx
```

Start the full stack:

```bash
docker compose down -v --remove-orphans
docker compose up --build -d
docker compose ps
```

The public endpoint is:

```text
http://localhost:8080
```

The direct diagnostic endpoints are expected to be:

```text
http://localhost:8081
http://localhost:8082
```

## 5. Use the browser interface

Open:

```text
http://localhost:8080/
```

The interface supports URL creation, custom aliases, expiration, analytics,
copy/open actions, and service health.

## 6. Create a short URL

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/setup-test"}'
```

The expected status is:

```text
HTTP/1.1 201
```

Copy the returned `shortCode`.

## 7. Test redirect

```bash
curl -i http://localhost:8080/YOUR_CODE
```

The expected result is:

```text
HTTP/1.1 302
Location: https://example.com/setup-test
```

## 8. Test analytics

```bash
curl -i \
  http://localhost:8080/api/v1/urls/YOUR_CODE/analytics
```

The expected status is:

```text
HTTP/1.1 200
```

## 9. Test a custom alias

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl":"https://example.com/custom",
    "customAlias":"My_Link"
  }'
```

The expected returned `shortCode` is:

```text
my_link
```

A second request using `my_link` should return `409 Conflict`.

## 10. Verify request distribution

```bash
for i in {1..10}; do
  curl -s -D - http://localhost:8080/actuator/health \
    -o /dev/null | grep -i X-App-Instance
done
```

Repeated responses should show both:

```text
X-App-Instance: app1
X-App-Instance: app2
```

## 11. Verify shared persistence

Create a mapping directly through one application instance:

```bash
curl -i -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/shared-state"}'
```

Resolve the returned code through the second instance:

```bash
curl -i http://localhost:8082/YOUR_CODE
```

The expected result is `302 Found`.

## 12. Inspect Redis

After a successful redirect, inspect the mapping:

```bash
docker compose exec redis redis-cli GET redirect:YOUR_CODE
```

The expected value is the original URL.

## 13. Verify Redis fallback

```bash
docker compose stop redis
curl -i http://localhost:8080/YOUR_CODE
docker compose start redis
```

A valid database-backed URL should continue to redirect. Retain the terminal
output before marking this test as completed.

## 14. Verify application-instance failover

```bash
docker compose stop app1
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/YOUR_CODE
docker compose start app1
```

Retain the output before describing this test as passed.

## 15. View logs

```bash
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
docker compose logs --tail=100 app1
docker compose logs --tail=100 app2
docker compose logs --tail=100 nginx
```

Follow all logs:

```bash
docker compose logs -f
```

## 16. Troubleshooting

### Docker is not running

Start Docker Desktop and wait until this command succeeds:

```bash
docker info
```

### Port 8080 is already in use

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
kill <PID>
```

### Environment variables are missing

```bash
cp .env.example .env
docker compose config
```

### A container exits

```bash
docker compose ps -a
docker compose logs --tail=100 SERVICE_NAME
```

### A clean restart is required

```bash
docker compose down -v --remove-orphans
docker compose up --build -d
```

## 17. Stop the environment

Stop containers while retaining volumes:

```bash
docker compose down
```

Stop containers and remove volumes:

```bash
docker compose down -v
```

## 18. Final submission checks

```bash
./mvnw clean test
git status --short
```

Confirm that:

- the Maven test suite passes;
- all five Docker services start;
- creation, redirect, and analytics work;
- both `app1` and `app2` appear in response headers;
- `.env` is ignored;
- `.env.example` is tracked;
- the architecture images are present;
- all documentation links work;
- the Git working tree is clean.
