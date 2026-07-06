# ForgeFlow AI全链路开发平台

AI full-lifecycle development platform for internal R&D delivery.

需求提交 —> 生成需求文档 —> 生成 PRD —> PRD 确认 —> 生成页面原型 <br><br>
审核页面原型 —> 设计技术架构 —> 沉淀需求+架构文档推送 git —> 进行开发

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

## Effect Picture

<img width="1913" height="893" alt="image" src="https://github.com/user-attachments/assets/df31de3d-897a-4d53-b94a-9608f7eece18" />
<img width="1913" height="1494" alt="image" src="https://github.com/user-attachments/assets/7da26333-d585-426c-87bd-0065130c9fae" />
<img width="1630" height="859" alt="image" src="https://github.com/user-attachments/assets/83eb30df-1c07-40eb-8597-d173ce72d14d" />
<img width="1635" height="763" alt="image" src="https://github.com/user-attachments/assets/c25ea7ee-091f-47b2-b887-e32e8d75adc7" />
<img width="1612" height="859" alt="image" src="https://github.com/user-attachments/assets/867bc383-d913-4b9c-9943-180cb261dbfa" />


