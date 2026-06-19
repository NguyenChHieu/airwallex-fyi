# Stack Research: Airwallex FYI

## Recommendation

Use Kotlin Spring Boot with Gradle Kotlin DSL.

## Core Libraries

- Spring Boot Web: admin endpoints and HTTP service runtime.
- Spring Scheduling: scheduled polling.
- Spring Data JDBC: simple explicit relational persistence without JPA complexity.
- PostgreSQL driver: production persistence.
- Flyway: database migrations.
- Jackson Kotlin module: JSON handling with Kotlin types.
- Jsoup: fallback HTML extraction.
- WebClient or RestClient: outbound HTTP to Airwallex, OpenAI, and Twilio.
- JUnit 5, Spring Boot Test, MockWebServer/WireMock: focused HTTP and service tests.

## Notes

Spring Boot has first-class Kotlin support and Kotlin project defaults include kotlin-spring/all-open behavior, kotlin-reflect, and Jackson Kotlin support when generated correctly.

## Confidence

High for Kotlin Spring Boot. Medium for exact Spring Boot version until the project is scaffolded from current Spring Initializr or Gradle plugin metadata.
