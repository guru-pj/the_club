# UUID Strategy — The Club

## Summary
We use time-ordered UUIDs (UUIDv7-style) generated inside each microservice so that IDs are:
- Globally unique across services and databases
- Time-ordered (reduces index fragmentation)
- Generated without DB coordination

## What this package includes
- UuidGenerator.java (use the TimeBasedEpochGenerator from java-uuid-generator)
- Example entities for user-service, auth-service, activity-service
- Repository examples using `JpaRepository<Entity, UUID>`
- Service snippets to assign IDs before saving
- Unit tests to verify uniqueness and ordering
- Optional UUIDConverter to store UUIDs compactly as BINARY(16)

## Quick steps
1. Add dependency to each service `pom.xml`:
   - `com.fasterxml.uuid:java-uuid-generator:5.0.0`
   - `org.junit.jupiter:junit-jupiter:5.10.0` (test scope)
2. Add `com.theclub.<service>.util.UuidGenerator` to each service.
3. Update entities to set ID using `UuidGenerator.generate()` in constructor or service create method.
4. Use `JpaRepository<Entity, UUID>` for repositories.
5. Run the provided `UuidGeneratorTest` in `src/test/java`.
6. (Optional) Migrate to BINARY(16) using `UUIDConverter`.

## File locations (suggested)
- `user-service/src/main/java/com/theclub/userservice/util/UuidGenerator.java`
- `auth-service/src/main/java/com/theclub/authservice/util/UuidGenerator.java`
- `activity-service/src/main/java/com/theclub/activityservice/util/UuidGenerator.java`
- `user-service/src/main/java/.../entity/User.java` (example)
- `auth-service/src/main/java/.../entity/AuthToken.java` (example)
- `activity-service/src/main/java/.../entity/Activity.java` (example)
- `src/test/java/.../UuidGeneratorTest.java` (per service)

## Notes
- We store UUID as CHAR(36) initially for simplicity and visibility.
- Later migration to BINARY(16) is recommended for large tables.
- Keep each generator class inside its service to avoid extra cross-service dependencies.
