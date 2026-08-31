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
