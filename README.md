# ducklake-demo

`ducklake-demo` 是一个标准 Spring Boot MVC 示例工程，通过 DuckDB JDBC 连接远程 DuckLake：

- PostgreSQL 保存 DuckLake catalog；
- SeaweedFS S3 保存 DuckLake Parquet 数据文件；
- Java 进程内嵌 DuckDB JDBC，加载 `httpfs`、`postgres`、`ducklake` extension；
- 同一张测试表分别由原生 JDBC、Spring `JdbcTemplate`、MyBatis、JPA/Hibernate 操作。

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
