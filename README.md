# ducklake-demo

`ducklake-demo` 是一个标准 Spring Boot MVC 示例工程，通过 DuckDB JDBC 连接远程 DuckLake：

- PostgreSQL 保存 DuckLake catalog；
- SeaweedFS S3 保存 DuckLake Parquet 数据文件；
- Java 进程内嵌 DuckDB JDBC，加载 `httpfs`、`postgres`、`ducklake` extension；
- 同一张测试表分别由原生 JDBC、Spring `JdbcTemplate`、MyBatis、JPA/Hibernate 操作。
- 可选的 ETL API 按转换平台生成的逻辑目录调用受管 Project、Workflow 和 Node，并将上传文件、运行清单及运行台账与业务代码解耦。

包名按要求使用 `com.lanxinai.data.paltform.ducklake`。其中 `paltform` 保留了指定拼写。

## 版本

- Spring Boot `4.1.0`（当前稳定 GA）；
- Java `21`；
- DuckDB JDBC `1.5.4.0`（对应 DuckDB `1.5.4`）；
- MyBatis Spring Boot Starter `4.0.0`；
- springdoc-openapi `3.0.3`（Spring Boot 4 对应版本）；
- Hibernate 版本由 Spring Boot BOM 管理。

JPA/Hibernate 使用 `PostgreSQLDialect` 生成基础 SQL，实际 JDBC 连接仍是 DuckDB。DuckLake 不支持数据库层 PRIMARY KEY，示例中的 `@Id` 是 Hibernate 实体标识，UUID 由应用生成，表本身不创建主键约束。

## 分层

```text
Controller
  └─ DuckLakeDemoService
       ├─ JdbcDemoRecordDao
       ├─ JdbcTemplateDemoRecordDao
       ├─ MyBatisDemoRecordDao -> DemoRecordMapper
       └─ JpaDemoRecordDao -> EntityManager/Hibernate

DataSource
  └─ DuckDB JDBC
       ├─ DuckLake metadata -> PostgreSQL
       └─ DuckLake data -> SeaweedFS S3
```

测试表：`my_lake.main.ducklake_demo_record`。应用启动时执行 `CREATE TABLE IF NOT EXISTS`。

## 环境变量

真实密码不得写入 Git。配置契约见 `.env.example`。

工程启动入口会自动读取项目根目录的 `.env`，支持 dotenv 常用的单双引号和 `export` 写法。因此从 IDE 直接运行 `DuckLakeDemoApplication`、执行 `mvn spring-boot:run` 或运行 jar 时都可以读到本地配置。加载值作为 Spring Boot 最低优先级默认配置，操作系统环境变量、启动参数和 Docker/K8s Secret 都可以覆盖。

如果 env 文件不在工程目录，可以显式指定：

```powershell
java -Dducklake.env.file=C:/secure/ducklake-demo.env -jar target/ducklake-demo-0.1.0-SNAPSHOT.jar
```

首次在新电脑使用时：

```powershell
Copy-Item .env.example .env
# 然后只在本机编辑 .env，填入实际 PG/S3 凭据
```

使用项目根目录的 `.env` 启动：

```powershell
cd C:\path\to\ducklake-demo
.\scripts\run-with-env.ps1 -Action run
```

脚本只把 env 加载到当前 Maven 子进程，不打印密码。

如果当前目录已经有 `.env`，也可以直接启动：

```powershell
mvn spring-boot:run
```

## 可交互 API 文档

应用启动后打开：

- 首页（自动跳转 Swagger UI）：`http://127.0.0.1:8080/`；
- Swagger UI：`http://127.0.0.1:8080/swagger-ui.html`；
- OpenAPI JSON：`http://127.0.0.1:8080/v3/api-docs`。

在 Swagger UI 中展开接口，点击 `Try it out`，然后点击 `Execute` 即可真实调用 DuckLake。推荐先执行“一键执行完整 CRUD 场景”，它会返回新增、修改、删除、总条数变化和剩余数据。

控制台会输出以下非敏感过程信息：

- 本地 DuckDB 连接壳路径；
- DuckLake catalog、S3 endpoint 和 ATTACH 耗时；
- 测试表是否就绪；
- 每次 API 的 DAO 类型、影响条数、数据总数和执行耗时；
- Swagger UI 与 OpenAPI JSON 地址。

日志不会输出 PostgreSQL 密码或 S3 Secret。默认日志文件为 `logs/ducklake-demo.log`，可通过 `LOG_FILE` 环境变量修改。

如果 DuckDB extension 已经预装或使用离线缓存：

```text
DUCKLAKE_INSTALL_EXTENSIONS=false
DUCKDB_EXTENSION_DIRECTORY=/opt/duckdb/extensions
```

未显式设置 `DUCKDB_PATH` 时，应用使用系统临时目录下按进程隔离的 DuckDB 壳文件。DuckLake 的共享数据仍在 PostgreSQL + S3，不在这个本地文件中。

## API

DAO 路径值：`jdbc`、`jdbc-template`、`mybatis`、`jpa`。

### 建表

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/ducklake/table
```

### 查询与计数

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/ducklake/jdbc/records?limit=100
Invoke-RestMethod http://127.0.0.1:8080/api/ducklake/jdbc/count
```

### 新增 N 条

```powershell
$insert = Invoke-RestMethod -Method Post "http://127.0.0.1:8080/api/ducklake/jdbc-template/records?n=10"
$insert.batchId
```

### 修改该批次前 N 条

```powershell
$batchId = $insert.batchId
Invoke-RestMethod -Method Put "http://127.0.0.1:8080/api/ducklake/jdbc-template/records/$batchId?n=5&suffix=-changed"
```

### 删除该批次前 N 条

```powershell
Invoke-RestMethod -Method Delete "http://127.0.0.1:8080/api/ducklake/jdbc-template/records/$batchId?n=3"
```

### 一次执行完整场景

下面的请求会新增 10 条、修改其中 5 条、删除其中 3 条，并返回每一步的总行数和最终剩余数据：

```powershell
Invoke-RestMethod -Method Post "http://127.0.0.1:8080/api/ducklake/mybatis/scenario?insertN=10&updateN=5&deleteN=3"
```

依次验证全部 DAO：

```powershell
.\scripts\smoke-crud.ps1 -InsertN 5 -UpdateN 3 -DeleteN 2
```

## DolphinScheduler facade

Facade 默认关闭。启用时只配置 DolphinScheduler 入口、服务端凭据和转换平台生成的 catalog 文件：

```text
DS_ENABLED=true
DS_BASE_URL=http://dolphinscheduler-api:12345/dolphinscheduler
DS_TOKEN=<Kubernetes Secret 注入>
ETL_SCHEDULER_CATALOG_PATH=/etc/data-platform/etl-scheduler-catalog.json
```

Project、Workflow、Node 和 TaskGroup 均来自 catalog。Spring Boot 不固定业务 code，也不接受客户端传入任意 DolphinScheduler URL 或 numeric code。
catalog 还包含 tenant、WorkerGroup、失败策略和实例优先级等环境执行参数；应用在每次请求时重新加载，转换平台更新 catalog 后不需要修改 Java 代码或重启应用。
每个 Workflow 必须同时提供 `parameterSchemaVersion=2`、原始 `parameterSchema` 和 `parameterSchemaSha256`。哈希对象是按所有 Map key 排序后的紧凑 canonical JSON：`{"parameterSchema":...,"schemaVersion":2}`；应用加载 catalog 时会重新计算并拒绝不一致的内容。

- `GET /api/scheduler/catalog`：读取受管逻辑目录；
- `GET /api/scheduler/lineage`：读取编译生成的数据集、任务 transformation 和 Workflow 依赖血缘；
- `GET /api/scheduler/tasks/{taskId}/contract|lineage`：按逻辑任务 ID 读取参数、输入/输出/中间数据集和单任务血缘；
- `GET /api/scheduler/projects/{projectId}/workflows/{workflowId}/parameter-schema`：原样返回版本化参数 schema 及其 canonical SHA-256；
- `POST /api/scheduler/projects/{projectId}/workflows/{workflowId}/runs`：使用已有 run manifest 执行完整 Workflow；
- `POST /api/scheduler/projects/{projectId}/workflows/{workflowId}/nodes/{nodeId}/runs`：只执行指定逻辑 Node，用于诊断和补跑；
- `GET /api/scheduler/projects/{projectId}/runs/{instanceId}/status|tasks|log`：状态、任务和日志；状态查询会把 DolphinScheduler 原始状态幂等回写到 ETL ledger，并返回 `terminal`、`attentionRequired` 和 `stateChangedAt`；
- `POST /api/scheduler/projects/{projectId}/runs/{instanceId}/stop`：提交停止命令；`accepted=true` 只表示命令被 DolphinScheduler 接受，调用方必须按 `statusEndpoint` 继续轮询终态；
- `GET /api/scheduler/projects/{projectId}/task-groups/{taskGroupId}/queue`：TaskGroup 队列快照。

队列接口明确返回 `exactPosition=false`。DolphinScheduler API 的列表顺序不能包装成权威执行名次。

完整的业务功能到 DolphinScheduler API 映射和可直接执行案例见 [`docs/etl-dolphinscheduler-api-closed-loop.md`](docs/etl-dolphinscheduler-api-closed-loop.md)。

ETL ledger 默认每 30 秒分批对账未终态实例，单个实例查询失败不会中断整批。`READY_STOP` 持续超过 2 分钟时，状态 API 返回 `attentionRequired=true`，提示运维检查 Master failover；该告警不会把原始 DolphinScheduler 状态改写成伪造的 `STOP`。对账开关、间隔、阈值和批量上限分别由 `ETL_RECONCILIATION_ENABLED`、`ETL_RECONCILIATION_FIXED_DELAY`、`ETL_READY_STOP_STALE_AFTER` 和 `ETL_RECONCILIATION_BATCH_SIZE` 配置。

## ETL 业务 API

启用 `ETL_PLATFORM_ENABLED=true` 后，应用使用独立的 SeaweedFS Bucket `dp-springboot-files`。默认 Prefix 为 `data-platform-dev/etl-platform/`：

- `artifacts/yyyy/MM/dd/`：前端上传的 Excel、Parquet、CSV；
- `run-manifests/yyyy/MM/dd/`：每次执行生成的不可变 JSON 参数清单。

DuckLake 自身的数据文件仍使用 `s3://dp-ducklake/data-platform-dev/ducklake/`，两者不得混用。应用使用 S3 path-style 寻址；Bucket、Prefix、Endpoint、Region 均通过环境变量配置。

现有 PostgreSQL DuckLake catalog 使用 `ducklake_catalog` schema，运行环境需设置 `DUCKLAKE_METADATA_SCHEMA=ducklake_catalog`。应用通过 DuckLake `ATTACH ... (METADATA_SCHEMA ...)` 连接既有 catalog，不在 `public` schema 误建第二套 metadata 表。

### 上传文件

下面的调用以流式方式上传，不把整个文件载入 JVM 内存。返回值包含 `artifactId`、`uri`、大小和 SHA-256：

```powershell
$artifact = Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8080/api/v1/etl/artifacts `
  -Headers @{ 'X-Requested-By' = 'doctor' } `
  -Form @{ file = Get-Item C:\data\material.parquet }
```

### 执行物料主数据 Workflow

业务 API 只接收业务参数和 `artifactId`，不接收 DolphinScheduler numeric code，也不需要知道 Python 实现：

```powershell
$body = @{
  artifactId = $artifact.artifactId
  planId = 'plan-2026-07'
  versionId = 'v1'
  reason = 'manual validation'
} | ConvertTo-Json

$run = Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8080/api/v1/material-master/refresh `
  -Headers @{ 'X-Requested-By' = 'doctor' } `
  -ContentType application/json `
  -Body $body
```

应用会依次完成：解析 artifact URI、按 catalog 的 `parameterSchema` 校验参数、上传 run manifest、写入 PostgreSQL `dp_etl_control` 数据库中的 `etl_control.etl_run`、启动完整 Workflow、记录 workflow instance ID。若提交被拒绝，台账状态会写为 `FAILED`。

ETL 控制面数据库必须与 DuckLake catalog 隔离：`dp_etl_control` 保存 artifact/run 台账，`dp_ducklake` 只保存 DuckLake catalog。两者可以位于同一 PostgreSQL 实例，但不得把 `etl_control` schema 建在 `dp_ducklake` 中。

### 执行任意受管 Workflow

```powershell
$body = @{ parameters = @{ some_parameter = 'value' }; reason = 'manual run' } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8080/api/v1/etl/projects/notebooks-etl/workflows/material.master_refresh/runs `
  -Headers @{ 'X-Requested-By' = 'doctor' } `
  -ContentType application/json `
  -Body $body
```

逻辑 Project/Workflow 映射由转换平台 catalog 决定。物料业务入口的默认映射也可通过 `ETL_MATERIAL_PROJECT_ID` 和 `ETL_MATERIAL_WORKFLOW_ID` 覆盖。

## 构建

```powershell
.\scripts\run-with-env.ps1 -Action test
.\scripts\run-with-env.ps1 -Action package
```

## 设计限制

- 这是连接与分层示例，不是高并发 OLTP 服务；Hikari pool 固定为 1，避免嵌入式 DuckDB 多连接壳和锁语义混乱。
- 动态 DAO 类型采用枚举白名单；动态 catalog/schema/table 标识符经过校验。
- 所有值参数使用 JDBC/MyBatis/JPA 参数绑定。
- DuckLake 不支持 PRIMARY KEY、UNIQUE、FOREIGN KEY 和普通索引；业务唯一性需要应用层或 `MERGE INTO` 语义处理。
- 公网 PG/S3 仅适合受控开发演示，生产环境应使用内网/VPN、TLS、来源白名单和最小权限账号。
