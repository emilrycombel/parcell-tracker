# Design: <feature name>

## Requirements coverage
<Table or list mapping each requirement ID from requirements.md to the component(s) that satisfy it.>

## Approach
<The chosen approach and why, briefly. If there were real alternatives, name them and say
why they lost — this is what saves the next person from re-litigating the decision.>

## Data model
<New/changed tables, columns, indexes. Include the actual SQL or a diff against schema.sql.>

## API contract
<New/changed endpoints. Link to the openapi.yaml section rather than duplicating it —
update openapi.yaml first, then reference it here.>

## Components touched
<List files/packages that will be created or modified, one line each on what changes.>

## Error handling
<What can go wrong and what the system does about it — timeouts, partial failures,
duplicate registrations, courier API downtime, etc.>

## Testing strategy
<What gets a unit test, what gets an integration test, what's manually verified.>
