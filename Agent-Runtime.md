# ForgeFlow Agent Runtime

PRD Agent and Prototype Agent run on the same durable node runtime. A workflow is defined by:

- a serializable state object;
- a start node;
- registered nodes and tool names;
- node results that select the next node, pause for user input, or complete.

## Runtime states

| Status | Meaning | Resume behavior |
| --- | --- | --- |
| `READY` | A node is ready to run | Continue with `nextNode` |
| `RUNNING` | A node started and its stable input was checkpointed | Retry `currentNode` after failure/restart |
| `WAITING_USER` | The workflow requested human input | Reload business input and restart planning |
| `FAILED` | A node failed | Restore `stateJson` and retry `nextNode` |
| `COMPLETED` | The workflow reached its terminal node | No further execution |

Each node persists its state transition and `generation_task_step` in one independent transaction. The checkpoint written before node execution is retained if the node fails, so recovery retries from a stable state.

## PRD graph

```text
analyze-requirement -> assess-autonomy
  -> request-clarification -> WAITING_USER
  -> select-tools -> call-tools -> generate-draft -> review-draft
       -> revise-draft -> review-draft
       -> memory-save -> COMPLETED
```

## Prototype graph

```text
assess-autonomy
  -> request-clarification -> WAITING_USER
  -> select-tools -> call-tools -> generate-html -> review-prototype
       -> revise-html -> review-prototype
       -> memory-save -> COMPLETED
```

Review nodes act as planners: their result dynamically routes to revision or completion. Both graphs limit revision loops in their workflow state.

## API

```http
GET /api/v1/generation-task/{taskId}/runtime
GET /api/v1/generation-task/{taskId}/steps
POST /api/v1/generation-task/{taskId}/resume
```

For `WAITING_USER`, update the underlying Requirement or PRD first and then resume. For `FAILED`, resume restores the last stable checkpoint and retries the failed node.

## Database

Apply `database/20260710_agent_runtime.sql` after the generation task step migration.
