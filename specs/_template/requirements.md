# Requirements: <feature name>

## Overview
<1-3 sentences: what this feature is and why it's needed.>

## Glossary
<Only if the feature introduces terms that aren't obvious from the rest of the repo.>

## Requirements

Use EARS syntax (Easy Approach to Requirements Syntax) for every acceptance criterion so
each one is independently testable. Number them so `tasks.md` and commit messages can
reference them (e.g. "implements R3").

### R1: <short title>
- **WHEN** <trigger/event> **THE SYSTEM SHALL** <observable behavior>
- **WHEN** <trigger/event> **THE SYSTEM SHALL** <observable behavior>

### R2: <short title>
- **IF** <precondition> **THEN THE SYSTEM SHALL** <behavior>

### R3: <short title>
- **WHILE** <ongoing state> **THE SYSTEM SHALL** <behavior>

## Out of scope
<Explicitly excluded behavior, so scope creep during implementation has something to check against.>

## Open questions
<Anything not yet decided. Resolve before writing design.md, or note the assumption made.>
