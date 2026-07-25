---
name: "project-rules"
description: "Applies project-wide rules and conventions for architecture, coding style, state management, data access, concurrency, configuration, comments, code modification, technical decisions, quality, and completion criteria. Invoke when working on any task in this project to ensure compliance with established standards."
---

# Project Rules

## General Principles

- Always prioritize:
  1. Official documentation
  2. Official best practices
  3. Stable release documentation
  4. Maintainability over development speed
  5. Simplicity over unnecessary complexity

- If these rules conflict with the latest stable official guidance, follow the official guidance.

- Never fabricate APIs, documentation, version information, performance data or project facts.

- If uncertain about APIs, versions or recommended practices:
  - Check official documentation first.
  - If still uncertain, explicitly state that the information cannot be confirmed.

---

## Architecture

- Keep the architecture modular.
- Each module should have a single responsibility.
- Maintain low coupling and high cohesion.
- Avoid circular dependencies.
- Centralize shared functionality.
- Do not duplicate existing implementations.
- Do not change project architecture unless explicitly requested.

---

## Layer Responsibilities

Maintain clear separation of responsibilities.

UI
- UI rendering
- User interaction

Business
- Business logic
- State management
- Workflow coordination

Data
- Repository
- Network
- Database
- Cache

Never bypass layers.

---

## Coding Style

Prefer:

- Clear naming
- Small functions
- Small classes
- Immutable data
- Readable code
- Single Responsibility Principle

Avoid:

- Long methods
- Large classes
- Duplicate code
- Deep nesting
- Magic numbers
- Unnecessary global state

Keep new code consistent with the existing project style.

---

## State Management

- Use unidirectional data flow.
- Keep state centralized.
- UI should always be state-driven.
- Avoid maintaining duplicate state.

---

## Data Access

- Use a unified data access layer.
- Business logic must not directly access concrete data sources.
- Use consistent error handling.
- Handle exceptions explicitly.
- Never silently ignore failures.

---

## Concurrency

- Follow the project's asynchronous programming approach.
- Never block the main thread.
- Avoid shared mutable state.
- Ensure thread safety.

---

## Configuration

Never hardcode:

- URLs
- Secrets
- API keys
- Version numbers
- Configuration values

Support multiple environments when applicable.

---

## Comments

Only write comments explaining:

- Why the design exists
- Business rules
- Compatibility handling
- Performance considerations
- Security considerations

Do NOT write comments that simply describe what the code already clearly expresses.

---

## Code Modification

Default to the smallest possible change.

Do NOT:

- Modify unrelated files
- Modify unrelated code
- Perform unnecessary refactoring
- Change architecture without request

Only recommend refactoring when:

- There is a bug
- Maintainability is poor
- Performance is significantly affected
- The user explicitly requests it

---

## Technical Decisions

- Prefer existing project technologies.
- Do not introduce new frameworks without justification.
- When multiple solutions exist, explain:
  - Advantages
  - Disadvantages
  - Impact on the current project

Do not present one solution as the only correct choice.

---

## Quality

New code should:

- Be readable
- Be maintainable
- Follow existing architecture
- Preserve backward compatibility when applicable
- Be testable whenever practical

Prefer incremental improvements over large rewrites.

---

## Completion

When modifying:

- Public APIs
- Core business logic
- Project architecture

Explain:

- Why the change was made
- Benefits
- Potential drawbacks
- Whether related components also require changes
