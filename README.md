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

本机开发时复制环境模板即可。`local` profile 会把项目根目录中被忽略的 `.env` 作为可选 Spring 配置导入；Compose 则通过 `--env-file` 使用同一文件。默认 `AGENT_DOCKER_BIND_HOST=127.0.0.1`，MySQL `3307`、Redis `6380`、Qdrant HTTP `6333` 和 gRPC `6334` 只在 Docker 主机本机可访问。

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f compose.dev.yml up -d
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
mvn test
mvn -DskipTests package
```

`.env` 文件不会由 PowerShell 自动导出为进程环境变量。Spring Boot 本地 profile 已能直接读取它；如果 Maven 插件或其他 JVM 子进程也必须收到这些变量，请在当前 PowerShell 窗口中执行下面的导入，再启动应用：

```powershell
Get-Content .env |
    Where-Object { $_ -match '^\s*[^#\s][^=]*=' } |
    ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
    }

mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

这些变量只写入当前 PowerShell 进程及其子进程，关闭窗口后即失效。

### Docker 位于局域网主机

不要开放未认证的 Docker TCP API。先在开发机和 Docker 主机之间配置 SSH 密钥认证，再一次性创建命名 context（把示例用户名和地址替换为实际值）：

```powershell
$DockerUser = 'deploy'
$DockerHostAddress = '192.168.1.200'
docker context create smart-agent-lan --docker "host=ssh://${DockerUser}@${DockerHostAddress}"
```

应用运行在另一台局域网主机时，将 `.env` 中相关项改为 Docker 主机的 LAN 地址：

```dotenv
AGENT_DOCKER_BIND_HOST=192.168.1.200
AGENT_DB_URL=jdbc:mysql://192.168.1.200:3307/smart_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
AGENT_REDIS_HOST=192.168.1.200
AGENT_REDIS_PORT=6380
AGENT_QDRANT_HOST=192.168.1.200
AGENT_QDRANT_GRPC_PORT=6334
```

随后通过命名 context 部署，不需要设置 `DOCKER_HOST`：

```powershell
docker --context smart-agent-lan compose --env-file .env -f compose.dev.yml up -d
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

将服务绑定到 LAN 接口后，必须在 Docker 主机防火墙中把 TCP `3307`、`6380`、`6333`、`6334` 的入站来源限制为运行本应用的固定 IP；拒绝其他局域网来源和所有公网来源。SSH 端口也应只允许受信管理地址访问。若应用与 Docker 同机运行，请保持默认的 `127.0.0.1` 绑定。
