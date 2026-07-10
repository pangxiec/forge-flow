# ForgeFlow

AI full-lifecycle development platform for internal R&D delivery.

## Modules

```text
frontend
forge-flow-common
forge-flow-pojo
forge-flow-dao
forge-flow-third
forge-flow-message
forge-flow-api-admin
```

## Requirements

- Java 17
- Maven 3.9+
- MySQL 8.x
- Node.js 22+
- npm 11+

## Database

Initialize the first tables with:

```text
database/schema.sql
```

Then apply incremental migrations in date order:

```text
database/20260701_llm_call_log.sql
database/20260701_prototype_artifact.sql
database/20260706_generation_task_step.sql
database/20260710_agent_runtime.sql
```

The Agent Runtime migration adds durable checkpoints used by PRD and Prototype task resume.

See [Agent-Runtime.md](Agent-Runtime.md) for workflow states, graph definitions, and resume APIs.

Sensitive configuration such as database password, Gitea token, LLM API key, and MinIO secret is intentionally empty by default.

## Build

Backend:

```bash
mvn clean package
```

Frontend:

```bash
cd frontend
npm install
npm run build
```

## Run

Backend:

Set local datasource variables, then run:

```powershell
$env:FORGE_FLOW_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/forge_flow?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:FORGE_FLOW_DATASOURCE_USERNAME="root"
$env:FORGE_FLOW_DATASOURCE_PASSWORD="your-local-password"
```

```bash
mvn -pl forge-flow-api-admin -am spring-boot:run
```

Frontend:

```bash
cd frontend
npm run dev
```

## First API

Create project:

```http
POST /api/v1/project/create
Content-Type: application/json

{
  "projectName": "ForgeFlow MVP",
  "projectCode": "forge-flow-mvp",
  "description": "First-stage AI full-lifecycle platform",
  "managerId": 1
}
```

Project detail:

```http
GET /api/v1/project/detail/{id}
```
