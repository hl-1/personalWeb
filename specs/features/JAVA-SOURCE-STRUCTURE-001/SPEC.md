# Java Source Structure Specification

## Goal

Keep Java source ownership explicit by allowing at most one top-level type in each `.java` file.

## Rules

- Every production and test Java source file contains at most one top-level class, interface, enum, or record.
- Nested types remain allowed when their lifecycle and visibility are owned by the enclosing type.
- Package-private shared helpers use their own same-named source file in the same package.
- Structural extraction must preserve package names, type names, visibility, annotations, and runtime behavior.
- Administrative API errors are handled by the registered global `AdminApiExceptionHandler`; unregistered duplicate handlers are not retained.
- A JDK AST-based test enforces the rule so formatting and comments cannot create false positives.

## Acceptance Criteria

- No Java source file under `server/src/main/java` or `server/src/test/java` declares multiple top-level types.
- Content and portfolio field rules, slug converters, and URL policy retain their existing callers and package visibility.
- `JpaAdminAuditRepository` remains injectable through `AdminAuditRepository` with unchanged persistence behavior.
- Admin API error responses remain covered by the existing integration tests.
- Modulith boundaries, source-scope contracts, and the complete server verification pass.
