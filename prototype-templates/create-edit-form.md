# Create Edit Form Template

Purpose: create, edit, submit, or save a business object.

Use when the PRD mentions create, edit, submit, import, upload, draft, or form.

Required structure:
- Grouped fields with labels.
- Required fields and helper text where appropriate.
- Domain-specific select options and sample input values.
- Footer actions: save draft, submit, cancel.

Interaction requirements:
- Submit should navigate to detail or approval page and show changed status.
- Save draft can show local feedback.
- Validation messages should be represented as static examples or local state.
