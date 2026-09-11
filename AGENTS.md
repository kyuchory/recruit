# Development Instructions

Before making changes, read:

1. PROJECT_CONTEXT.md
2. recruit-inbox-mvp-design-v1.1.md
3. technical-design.md

The product requirements source of truth is
recruit-inbox-mvp-design-v1.1.md.

Preserve the existing architecture unless there is a strong reason to change it.

Before completing a task:

Backend:
./gradlew test
./gradlew build

Frontend:
npm run lint
npx tsc --noEmit
npm run build

Do not commit secrets.

Use small feature-focused commits.