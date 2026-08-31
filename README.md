# 工程管理智能体（smart-agent）

本仓库用于工程管理系统智能体的设计与开发。

## 设计文档

- [系统总体设计说明书](docs/superpowers/specs/2026-08-31-smart-agent-system-design.md)

## 当前技术基线

- Java 21
- Spring Boot
- LangChain4j
- MySQL
- Redis
- Qdrant
- MinIO / OSS

当前阶段只服务现有工程管理系统，并通过 `smart-boot` 的 AI 业务适配接口继承用户身份、项目范围和数据权限。

## 本地开发

复制 `.env.example` 为 `.env` 并按实际环境调整服务地址。Docker 在其他主机运行时，请在运行 Compose 的终端设置 `DOCKER_HOST`；本文件保持为可部署的 Compose 输入。开发服务仅绑定到运行 Docker 主机的 `127.0.0.1`：MySQL `3307`、Redis `6380`、Qdrant HTTP `6333` 和 gRPC `6334`。

```bash
mvn test
mvn -DskipTests package
```
