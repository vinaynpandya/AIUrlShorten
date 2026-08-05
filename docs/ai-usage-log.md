## Greenfield Scenario — Core URL Shortener

### Intent

Build the initial URL-shortening system from scratch with URL creation,
redirection, basic analytics, validation and error handling.

### Task decomposition

1. Create the persistence entity and repository.
2. Implement seven-character Base62 code generation.
3. Define API request and response DTOs.
4. Implement URL creation and redirect services.
5. Add atomic click-count updates.
6. Add REST controllers and global exception handling.
7. Perform manual end-to-end testing.
8. Add unit, controller and integration tests.

### AI contribution

Claude Code assisted with:

- Entity and repository implementation.
- Short-code generator.
- DTO generation.
- Service-layer implementation.
- Controller and exception-handler implementation.
- Unit and integration test generation.

### Engineer decisions

- Selected Java 17, Spring Boot and Maven.
- Selected H2 for the runnable prototype.
- Used seven-character Base62 codes.
- Required bounded collision retries.
- Required a unique database constraint.
- Required atomic analytics updates.
- Used HTTP 302 redirects.
- Accepted only HTTP and HTTPS URLs.
- Kept authentication and rate limiting outside Phase 1.

### Engineer oversight

- Approved file changes individually.
- Prevented Claude from making broad repository changes.
- Rejected unrelated formatting changes.
- Stopped Claude when it moved outside the requested task boundary.
- Corrected outdated Spring Boot test imports.
- Reviewed generated code and test results.
- Performed manual API validation.

### Validation

- URL creation returned HTTP 201.
- Redirect returned HTTP 302.
- The Location header contained the original URL.
- Analytics increased after redirects.
- Blank and malformed URLs returned HTTP 400.
- Unknown codes returned HTTP 404.
- Maven unit and integration tests passed.

### Status

Completed.