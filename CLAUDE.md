# URL Shortener Project Instructions

## Project purpose

This is a production-oriented URL shortener prototype for an
AI-Proficient Software Engineer interview assignment.

AI assists with defined engineering tasks. The engineer reviews and
approves all designs, code changes, tests, and documentation.

## Technology

- Java 17
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Jakarta Validation
- H2 for the runnable prototype
- JUnit 5
- Mockito
- MockMvc
- Spring Boot Actuator

## Architecture rules

- Use controller, service, repository, entity, DTO, exception, and utility layers.
- Keep business logic out of controllers.
- Use constructor injection.
- Do not expose JPA entities directly through APIs.
- Keep it secure
- Validate all external inputs.
- Use global exception handling.
- Use appropriate HTTP status codes.
- Avoid unnecessary abstractions and dependencies.
- Do not add dependencies without explaining the reason.

## Engineering workflow

- Inspect the existing code before proposing changes.
- Present a plan before changing multiple files.
- List the files that will be created or modified.
- Identify assumptions, risks, and acceptance criteria.
- Wait for engineer approval before major implementation.
- Make focused changes only.
- Do not perform unrelated refactoring.
- Run the Maven tests after implementation.
- Explain what was generated, modified, or rejected.
- Never commit changes automatically.
- Never commit credentials or secrets.

## Quality requirements

Review every implementation for:

- Functional correctness
- Input validation
- URL security
- Database constraints
- Collision handling
- Transaction boundaries
- Concurrency risks
- Backward compatibility
- Test coverage
- Logging and observability
- Maintainability

## Commands

Build and test:

```bash
./mvnw clean test