ARG RUNTIME_IMAGE=swr.cn-east-3.myhuaweicloud.com/data-platform/dp-ducklake-demo@sha256:55dfc84b4048ac536a26ee3fd0491d68d9ae4e226b6e5b7ad469ab4af9fbf4f2
FROM ${RUNTIME_IMAGE}

# 运行时、Java 21 和 DuckDB extensions 全部继承自已固定 digest 的内部镜像。
# Docker 构建仅复制本地 Maven 已生成并测试通过的 jar，不访问公网仓库。
COPY --chown=10001:10001 target/ducklake-demo-0.1.0-SNAPSHOT.jar /app/app.jar
