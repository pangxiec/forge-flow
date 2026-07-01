# AI 全链路开发平台第一阶段开发设计

## 1. 文档说明

本文档基于以下资料编写：

- `AI全链路开发平台第一阶段需求沉淀.md`
- `审批流规则.md`
- `knowledge-base/后端项目结构规范.md`
- `knowledge-base/数据库表设计规范.md`

第一阶段目标是完成从需求上传、AI 分析、PRD 生成、原型生成、开发包生成到 Gitea 交付的最小闭环。平台第一阶段只服务内部研发团队，不做多租户、多项目空间、团队空间和复杂权限隔离。

## 2. 总体设计目标

1. 支持内部研发团队按标准流程完成 AI 辅助交付。
2. 所有关键产物必须经过审批、冻结和版本留痕。
3. AI 生成代码作为正式项目骨架继续开发，而不是一次性 Demo。
4. 生成代码必须通过基础编译检查和启动验证。
5. 敏感配置默认留空，敏感数据推送大模型前必须脱敏。
6. 平台操作、AI 生成、审批、Git 推送均需要审计记录。

## 3. 系统模块划分

| 模块 | 职责 |
| --- | --- |
| 项目管理模块 | 创建项目、维护项目基础信息、项目成员和当前阶段状态 |
| 需求管理模块 | 上传原始需求、保存结构化需求、管理需求版本 |
| AI 生成模块 | 调用 PRD、Prototype、Architecture、Code、Test 等 Agent |
| PRD 模块 | 保存 PRD 内容、版本、评论、冻结记录 |
| 原型模块 | 保存原型生成结果、预览地址、版本和冻结记录 |
| 架构设计模块 | 保存数据库设计、接口设计、模块设计和技术方案 |
| 开发包模块 | 管理代码生成任务、生成结果、编译检查和启动验证 |
| 审批模块 | 管理审批任务、审批记录、会签、驳回、冻结和阻断规则 |
| 知识库模块 | 管理规范文档，供 AI 生成时检索引用 |
| Git 集成模块 | 创建 Gitea 仓库、创建分支、提交代码和记录交付结果 |
| 审计日志模块 | 记录用户操作、审批动作、生成任务、Git 操作和异常 |
| 系统配置模块 | 管理模型、Gitea、MinIO、脱敏规则等平台配置 |

## 4. 推荐后端工程结构

后端遵循 `knowledge-base/后端项目结构规范.md`，采用 Spring Boot 3.x + Java 17 + Maven 多模块结构。

```text
forge-flow/
  pom.xml
  forge-flow-common/
  forge-flow-pojo/
  forge-flow-dao/
  forge-flow-third/
  forge-flow-message/
  forge-flow-api-admin/
```

模块职责：

| 模块 | 职责 |
| --- | --- |
| common | 通用注解、常量、枚举、上下文、统一返回、异常、工具类 |
| pojo | 请求 VO、响应 VO、输入 DTO、输出 DTO |
| dao | Domain、Mapper、Mapper XML |
| third | Gitea、LLM Gateway、MinIO、外部通知等第三方调用封装 |
| message | 站内信、企业微信/飞书/钉钉、邮件等通知能力 |
| api-admin | 后台管理端入口，包含 Controller、Service、Manager、Config |

## 5. 第一阶段核心流程

```text
需求创建
↓
AI 分析
↓
产品确认
↓
需求方确认
↓
AI 生成 PRD
↓
PRD 审批
↓
PRD 冻结
↓
AI 生成原型
↓
原型审批
↓
原型冻结
↓
AI 生成架构设计
↓
架构师确认
↓
AI 生成开发包
↓
编译检查和启动验证
↓
项目经理确认 Git 初始化
↓
创建 Gitea 仓库并推送代码
↓
开发开始
```

## 6. 状态机设计

### 6.1 项目主状态

| 状态 | 说明 |
| --- | --- |
| DRAFT | 草稿 |
| REQUIREMENT_ANALYZING | AI 分析需求中 |
| REQUIREMENT_REVIEWING | 需求确认中 |
| PRD_GENERATING | PRD 生成中 |
| PRD_REVIEWING | PRD 审批中 |
| PRD_FROZEN | PRD 已冻结 |
| PROTOTYPE_GENERATING | 原型生成中 |
| PROTOTYPE_REVIEWING | 原型审批中 |
| PROTOTYPE_FROZEN | 原型已冻结 |
| ARCHITECTURE_GENERATING | 架构设计生成中 |
| ARCHITECTURE_REVIEWING | 架构设计确认中 |
| CODE_GENERATING | 开发包生成中 |
| CODE_VALIDATING | 编译和启动验证中 |
| CODE_REVIEWING | 开发包确认中 |
| GIT_INITIALIZING | Git 初始化中 |
| GIT_PUBLISHED | Git 已交付 |
| DEVELOPMENT_STARTED | 开发已开始 |

### 6.2 异常状态

| 状态 | 说明 |
| --- | --- |
| REJECTED | 当前审批节点被驳回 |
| BLOCKED | 流程被阻断，需要人工处理 |
| FAILED | 生成、验证或 Git 操作失败 |
| CANCELLED | 项目被取消 |

## 7. 审批设计

审批规则以 `审批流规则.md` 为准。开发实现中建议使用审批实例 + 审批任务 + 审批记录三层模型。

| 概念 | 说明 |
| --- | --- |
| WorkflowInstance | 一个项目的一条流程实例 |
| ApprovalTask | 某个节点分配给某个角色/用户的待审批任务 |
| ApprovalRecord | 每一次审批动作记录，包括通过、驳回、评论、重新生成 |

第一阶段审批节点：

| 节点 | 审批角色 | 通过条件 |
| --- | --- | --- |
| 需求确认 | 产品经理、需求方 | 全部通过 |
| PRD 确认 | 产品经理、需求方、测试负责人 | 全部通过 |
| PRD 冻结 | 项目经理 | 项目经理确认 |
| 原型确认 | 产品经理、需求方、前端负责人、后端负责人 | 全部通过 |
| 原型冻结 | 项目经理 | 项目经理确认 |
| 架构方案确认 | 架构师、后端负责人、前端负责人、测试负责人 | 全部通过 |
| 开发包确认 | 架构师、后端负责人、前端负责人、测试负责人 | 全部通过且质量门禁通过 |
| Git 初始化确认 | 项目经理 | 项目经理确认 |

驳回规则：

1. 驳回必须填写原因。
2. 驳回原因必须记录问题描述、影响范围、建议修改方向、是否需要 AI 重新生成。
3. 驳回后由产品经理或对应负责人手动调整，再重新提交审批或重新触发生成。
4. 驳回记录写入 `approval_record` 和 `audit_log`。

## 8. 数据库设计

数据库设计遵循 `knowledge-base/数据库表设计规范.md`：

- 表名和字段名统一小写下划线。
- 主键统一为 `id BIGINT`。
- 不使用数据库物理外键。
- 核心业务表必须包含 `created_at`、`updated_at`、`created_by`、`updated_by`、`deleted`、`version`。

### 8.1 核心表清单

| 表名 | 说明 |
| --- | --- |
| project | 项目主表 |
| project_member | 项目成员表 |
| requirement | 原始需求表 |
| requirement_version | 需求版本表 |
| prd_document | PRD 文档表 |
| prd_version | PRD 版本表 |
| prototype | 原型表 |
| architecture_design | 架构设计表 |
| database_design | 数据库设计表 |
| api_design | 接口设计表 |
| code_package | 开发包表 |
| generation_task | AI 生成任务表 |
| workflow_instance | 流程实例表 |
| approval_task | 审批任务表 |
| approval_record | 审批记录表 |
| artifact | 产物表 |
| knowledge_document | 知识库文档表 |
| git_repository | Git 仓库表 |
| audit_log | 审计日志表 |
| system_config | 系统配置表 |

### 8.2 关键表字段

#### project

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键ID |
| project_name | VARCHAR(128) | 项目名称 |
| project_code | VARCHAR(64) | 项目编码 |
| description | VARCHAR(512) | 项目描述 |
| current_stage | VARCHAR(64) | 当前阶段 |
| status | VARCHAR(64) | 项目状态 |
| manager_id | BIGINT | 项目经理ID |

#### generation_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键ID |
| project_id | BIGINT | 项目ID |
| task_type | VARCHAR(64) | 任务类型：PRD、PROTOTYPE、ARCHITECTURE、CODE、TEST |
| status | VARCHAR(64) | 任务状态 |
| input_artifact_id | BIGINT | 输入产物ID |
| output_artifact_id | BIGINT | 输出产物ID |
| model_name | VARCHAR(128) | 模型名称 |
| error_message | TEXT | 错误信息 |
| started_at | DATETIME | 开始时间 |
| finished_at | DATETIME | 结束时间 |

#### approval_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键ID |
| workflow_instance_id | BIGINT | 流程实例ID |
| project_id | BIGINT | 项目ID |
| node_code | VARCHAR(64) | 节点编码 |
| approver_role | VARCHAR(64) | 审批角色 |
| approver_id | BIGINT | 审批人ID |
| status | VARCHAR(64) | 状态：PENDING、APPROVED、REJECTED、CANCELLED |
| due_at | DATETIME | 截止时间 |

#### approval_record

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键ID |
| approval_task_id | BIGINT | 审批任务ID |
| workflow_instance_id | BIGINT | 流程实例ID |
| project_id | BIGINT | 项目ID |
| node_code | VARCHAR(64) | 节点编码 |
| approver_id | BIGINT | 审批人ID |
| approver_role | VARCHAR(64) | 审批角色 |
| approval_action | VARCHAR(64) | APPROVE、REJECT、COMMENT、REGENERATE、FINAL_CONFIRM |
| approval_comment | TEXT | 审批意见 |
| before_status | VARCHAR(64) | 操作前状态 |
| after_status | VARCHAR(64) | 操作后状态 |
| ip_address | VARCHAR(64) | 操作IP |
| user_agent | VARCHAR(512) | User Agent |
| approved_at | DATETIME | 审批时间 |

#### git_repository

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键ID |
| project_id | BIGINT | 项目ID |
| repository_name | VARCHAR(128) | 页面填写的 Gitea 仓库名称 |
| repository_url | VARCHAR(512) | 仓库地址 |
| branch_name | VARCHAR(128) | 页面填写的分支名称，默认 main |
| default_branch | VARCHAR(128) | 默认分支 |
| git_provider | VARCHAR(32) | 固定为 GITEA |
| push_status | VARCHAR(64) | 推送状态 |
| pushed_at | DATETIME | 推送时间 |

## 9. 接口设计规范

接口返回规范遵循 `knowledge-base/后端项目结构规范.md`，Controller 返回原始数据，由统一响应处理器包装为：

```json
{
  "code": "000000",
  "success": true,
  "msg": "success",
  "data": {}
}
```

接口路径统一使用：

```text
/api/v1/{module}/{operation}
```

### 9.1 核心接口分组

| 分组 | 路径前缀 | 说明 |
| --- | --- | --- |
| 项目 | /api/v1/project | 项目创建、详情、列表、成员 |
| 需求 | /api/v1/requirement | 需求上传、分析结果、版本 |
| PRD | /api/v1/prd | PRD 生成、编辑、冻结 |
| 原型 | /api/v1/prototype | 原型生成、预览、冻结 |
| 架构 | /api/v1/architecture | 架构设计、接口设计、数据库设计 |
| 开发包 | /api/v1/code-package | 代码生成、编译检查、启动验证 |
| 审批 | /api/v1/approval | 审批任务、审批动作、审批记录 |
| Git | /api/v1/git | 仓库创建、分支配置、推送 |
| 知识库 | /api/v1/knowledge | 知识库文档管理 |
| 审计 | /api/v1/audit | 审计日志查询 |
| 系统配置 | /api/v1/system-config | 模型、Gitea、脱敏规则配置 |

### 9.2 关键接口示例

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| /api/v1/project/create | POST | 创建项目 |
| /api/v1/requirement/upload | POST | 上传需求材料 |
| /api/v1/requirement/analyze | POST | 触发 AI 需求分析 |
| /api/v1/prd/generate | POST | 生成 PRD |
| /api/v1/approval/approve | POST | 审批通过 |
| /api/v1/approval/reject | POST | 审批驳回 |
| /api/v1/prd/freeze | POST | 冻结 PRD |
| /api/v1/prototype/generate | POST | 生成原型 |
| /api/v1/prototype/freeze | POST | 冻结原型 |
| /api/v1/code-package/generate | POST | 生成开发包 |
| /api/v1/code-package/validate | POST | 编译检查和启动验证 |
| /api/v1/git/init | POST | 初始化 Gitea 仓库并推送 |

## 10. AI 生成设计

### 10.1 Agent 类型

| Agent | 输入 | 输出 |
| --- | --- | --- |
| PRD Agent | 原始需求、澄清信息、知识库 | PRD、验收标准、澄清问题 |
| Prototype Agent | 冻结 PRD、前端规范 | Vue3 原型页面 |
| Architecture Agent | 冻结 PRD、冻结原型、知识库 | 数据库设计、接口设计、模块设计 |
| Backend Agent | 架构设计、数据库设计、后端规范 | Spring Boot 工程代码 |
| Frontend Agent | 原型、接口设计、前端规范 | Vue3 工程代码 |
| Test Agent | PRD、接口设计 | 测试用例、接口测试脚本 |
| Git Agent | 开发包、仓库配置 | Gitea 仓库、分支、提交记录 |

### 10.2 生成任务状态

```text
PENDING
RUNNING
SUCCESS
FAILED
CANCELLED
```

每次重新生成必须新建任务，保留旧版本产物，不覆盖冻结版本。

## 11. 知识库接入设计

第一阶段知识库先接入本地规范文档：

| 文档 | 用途 |
| --- | --- |
| knowledge-base/后端项目结构规范.md | 后端工程结构、接口返回、分层、命名、配置规范 |
| knowledge-base/数据库表设计规范.md | 表命名、字段命名、主键、通用字段、外键规则 |

生成时按任务类型检索：

| 生成任务 | 需要引用的知识库 |
| --- | --- |
| 数据库设计 | 数据库表设计规范 |
| 接口设计 | 后端项目结构规范、数据库表设计规范 |
| 后端代码 | 后端项目结构规范、数据库表设计规范 |
| 开发包 README | 后端项目结构规范 |

## 12. 敏感数据与脱敏设计

### 12.1 敏感数据范围

- 数据库连接地址、用户名、密码。
- Redis、MinIO、Gitea 等中间件连接信息。
- Token、AccessKey、SecretKey。
- 内部服务地址。
- 账号密码、手机号、身份证、银行卡等个人敏感信息。

### 12.2 处理规则

1. 推送给公网大模型前必须脱敏。
2. 生成初始项目时敏感配置默认为空。
3. 代码生成产物不得包含真实密钥、真实数据库连接和真实 Token。
4. 脱敏前后内容需要记录审计，但审计日志不保存明文敏感值。

配置示例：

```yaml
spring:
  datasource:
    url:
    username:
    password:

redis:
  host:
  port:
  password:

minio:
  endpoint:
  access-key:
  secret-key:
```

## 13. 开发包生成与质量门禁

开发包结构：

```text
project-root/
  docs/
  frontend/
  backend/
  database/
  tests/
  docker/
  README.md
```

质量门禁：

| 门禁 | 说明 |
| --- | --- |
| 后端编译检查 | 执行 Maven 编译或打包 |
| 后端启动验证 | 启动 Spring Boot 应用并检查健康状态 |
| 前端编译检查 | 执行依赖安装和构建命令 |
| 前端启动验证 | 启动前端服务并检查页面可访问 |
| 敏感信息扫描 | 检查生成代码中是否包含真实密钥、数据库连接、Token |

任一门禁失败，流程进入 `BLOCKED` 或 `FAILED`，不得进入 Git 初始化。

## 14. Gitea 集成设计

第一阶段统一使用 Gitea。

页面配置项：

| 字段 | 说明 |
| --- | --- |
| repository_name | 仓库名称，页面填写 |
| branch_name | 分支名称，页面填写，默认 main |

执行步骤：

1. 校验仓库名称是否为空。
2. 校验分支名称是否为空，为空时使用 main。
3. 调用 Gitea API 创建仓库。
4. 初始化本地 Git 工作区。
5. 写入开发包文件。
6. 创建或切换目标分支。
7. 提交代码。
8. 推送到 Gitea。
9. 保存仓库地址、分支、提交结果和推送日志。

Gitea 凭证第一阶段作为系统配置项管理，后续可扩展为平台机器人账号或用户 Token。

## 15. 审计日志设计

需要记录的操作：

- 登录、退出。
- 项目创建、修改、删除。
- 需求上传、分析、版本变更。
- AI 生成任务创建、成功、失败、重新生成。
- PRD、原型、架构、开发包冻结。
- 审批通过、驳回、评论、最终确认。
- Git 仓库创建、分支创建、代码提交、推送失败。
- 系统配置变更。
- 知识库文档新增、修改、删除。

审计字段：

| 字段 | 说明 |
| --- | --- |
| id | 主键ID |
| user_id | 操作人ID |
| user_role | 操作人角色 |
| operation_type | 操作类型 |
| target_type | 操作对象类型 |
| target_id | 操作对象ID |
| before_content | 操作前内容摘要 |
| after_content | 操作后内容摘要 |
| ip_address | IP 地址 |
| user_agent | User Agent |
| created_at | 操作时间 |

## 16. 第一阶段开发顺序

建议按以下顺序开发：

1. 后端基础工程、统一返回、异常处理、审计日志基础能力。
2. 用户角色、项目管理、项目成员。
3. 需求上传、文件存储、需求版本。
4. AI 生成任务框架和 PRD Agent 接入。
5. 审批流、审批任务、审批记录、冻结能力。
6. PRD 管理和 PRD 冻结。
7. 原型生成、预览、审批和冻结。
8. 架构设计、数据库设计、接口设计。
9. 开发包生成、编译检查、启动验证、敏感信息扫描。
10. Gitea 仓库创建、分支提交、交付记录。
11. 知识库管理和规范检索接入。
12. 系统配置、日志查询、验收修正。

## 17. 第一阶段默认假设

以下内容不阻塞开发设计，第一阶段按默认方案实现：

1. Gitea 凭证先使用系统配置项，后续再扩展平台机器人账号或用户 Token。
2. 通知先支持站内通知，企业微信/飞书/钉钉作为扩展。
3. 知识库先支持本地 Markdown 文档导入和检索，后续再扩展审批入库机制。
4. 编译和启动验证先按沙箱命令执行，后续再扩展为容器化执行。
5. 复杂权限隔离、多项目空间、团队空间放入后续迭代。

## 18. 验收关注点

1. 可以创建项目并上传需求。
2. 可以触发 AI 分析和 PRD 生成。
3. PRD、原型、架构、开发包均有版本和审批记录。
4. 审批未通过时不能进入下一阶段。
5. 驳回后可修改并重新提交或重新生成。
6. 冻结产物不能直接覆盖。
7. 生成代码符合后端项目结构规范和数据库表设计规范。
8. 生成代码通过基础编译和启动验证。
9. Git 初始化页面可填写仓库名称和分支名称，默认分支为 main。
10. 平台可以自动创建 Gitea 仓库并推送代码。
11. 审计日志完整记录关键操作。
12. 初始项目不包含真实敏感配置。
