# Club Owner Verification Service (The Club)

This service handles verifying users who want to become club owners.
Every user is first a normal user (ROLE_USER). After submitting proof
and passing manual admin review, they receive ROLE_CLUB_OWNER and can
create/manage their own club.

Contents:
- verification_flow.txt (high-level system flow)
- db_schema.sql (database table structure)
- api_endpoints.md (REST API contract)
- admin_workflow.md (how admins review)
- service_structure.txt (package layout + infra notes)

Microservice Responsibilities:
- Receive owner verification applications
- Store documents (URLs of uploaded images/files)
- Allow admins to approve/reject/ask-more-info
- Update user's role by calling User Service
- Optionally trigger Club creation by calling Club Service
- Notify user about verification status

Tech Stack:
- Spring Boot 3.x
- Java 17+
- Spring Web + Spring Data JPA
- MySQL/PostgreSQL
- JWT verification using Auth Service
- FeignClient to call User Service, Club Service, Notification Service