# Spring Boot 到 DolphinScheduler 的 ETL 闭环

## 分层原则

业务前端只调用 `ducklake-demo`。业务请求使用逻辑 `projectId/workflowId/nodeId`；DolphinScheduler 数字 code 只存在于转换平台生成的 catalog 中。

扫描、编译和同步属于 `data-platform-etl-control` 控制面，不暴露成普通业务 API：

- `scan/validate/plan`：只读本地 Gitea 工作副本，不调用 DolphinScheduler；
- `ds-plan`：查询 DolphinScheduler Project/Workflow 详情并计算差异，不写入；
- `ds-apply`：创建/更新/上线 Workflow，并生成 catalog v2；
- `ducklake-demo`：加载 catalog v2，提供业务运行生命周期、契约和血缘查询。

## 业务功能与 DolphinScheduler API 映射

| 业务功能 | Spring Boot API | DolphinScheduler API | 说明 |
|---|---|---|---|
| 上传 Excel/CSV/Parquet | `POST /api/v1/etl/artifacts` | 无 | 流式写 SeaweedFS，返回 `artifactId`/S3 URI/SHA-256。 |
| 查询参数与表契约 | `GET /api/scheduler/tasks/{taskId}/contract` | 无 | 直接读取 catalog 中的 `ETL_META` 编译结果。 |
| 查询全局/单任务血缘 | `GET /api/scheduler/lineage`、`GET /api/scheduler/tasks/{taskId}/lineage` | 无 | 直接读取声明血缘，不在线执行 Python。 |
| 物料主数据刷新 | `POST /api/v1/material-master/refresh` | `POST /projects/{projectCode}/executors/start-workflow-instance` | 先解析 artifact、校验参数、写 run manifest/ledger，再启动固定逻辑 Workflow。 |
| 任意受管 Workflow | `POST /api/v1/etl/projects/{projectId}/workflows/{workflowId}/runs` | 同上 | catalog 将逻辑 ID 解析为 `workflowDefinitionCode`。 |
| 单 Node 诊断/补跑 | `POST /api/scheduler/projects/{projectId}/workflows/{workflowId}/nodes/{nodeId}/runs` | 同上 | 额外传 `startNodeList=<nodeCode>`；仍使用已登记 run manifest。 |
| 查询实例状态 | `GET /api/scheduler/projects/{projectId}/runs/{instanceId}/status` | `GET /projects/{projectCode}/workflow-instances/{instanceId}` | 返回原始状态，并更新本地 ledger 的终态/关注状态。 |
| 查询 Node 实例 | `GET /api/scheduler/projects/{projectId}/runs/{instanceId}/tasks` | `GET /projects/{projectCode}/workflow-instances/{instanceId}/tasks?pageNo=1&pageSize=100` | 返回每个 Node 的 task instance ID、名称、状态和时间。 |
| 查询日志 | `GET /api/scheduler/projects/{projectId}/runs/{instanceId}/log` | 先查 tasks，再 `GET /log/detail?taskInstanceId=...` | 可指定 `taskInstanceId` 和行范围；未指定时取第一个 task。 |
| 停止实例 | `POST /api/scheduler/projects/{projectId}/runs/{instanceId}/stop` | `POST /projects/{projectCode}/executors/execute` | 表单为 `workflowInstanceId` + `executeType=STOP`；接受后继续轮询状态。 |
| 查询 TaskGroup 队列 | `GET /api/scheduler/projects/{projectId}/task-groups/{taskGroupId}/queue` | `GET /task-group/query-list-by-group-id` | 只返回 API 快照顺序，`exactPosition=false`，不伪造权威名次。 |

## 物料刷新完整时序

1. 前端上传文件，获得 `artifactId`；
2. 后端从 `etl_control.etl_artifact` 读取受控 S3 URI；
3. 后端从 catalog 读取 Workflow 参数 Schema，校验 `delta_uri/plan_id/version_id`；
4. 后端生成不可变 `etl_run_manifest`，写入 SeaweedFS 并记录 SHA-256；
5. 后端先写 `etl_control.etl_run`，再调用 DolphinScheduler start API；
6. DolphinScheduler 的所有 Shell Node 使用同一个 runner，仅 `etl_task_id` 不同；
7. Node 拉取固定 Gitea ref，runner 校验 manifest URI/SHA-256 和 `ETL_META`，调用 `run_etl(context)`；
8. 后端按 instance ID 查询 status/tasks/log，支持 stop 和队列快照；
9. 前端可随时查询任务契约和血缘，完全不需要知道 Python 具体实现。

## 可执行入口

- IntelliJ/VS Code REST Client：[`examples/etl-closed-loop.http`](../examples/etl-closed-loop.http)
- PowerShell 7：[`scripts/invoke-etl-closed-loop.ps1`](../scripts/invoke-etl-closed-loop.ps1)

PowerShell 示例会执行契约查询、血缘查询、可选文件上传、业务 Workflow 启动、状态轮询、任务与日志查询；只有显式传 `-StopAfterStart` 才发停止命令。
