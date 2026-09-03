# 本地开发运行手册

## 启动

在项目根目录执行。`.env` 仅用于本地开发，不要提交到版本库，也不要把真实密钥写入 `.env.example`、日志或测试代码。

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f compose.dev.yml up -d
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
Invoke-RestMethod http://localhost:8080/actuator/health
```

默认 Compose 端口只绑定 `127.0.0.1`。如果 Docker 运行在另一台主机，使用受保护的 SSH Docker context，并在 `.env` 中把数据库、Redis 和 Qdrant 地址改为该主机地址；不要开放未认证的 Docker TCP API。LAN 绑定时必须在 Docker 主机防火墙中限制 `3307`、`6380`、`6333`、`6334` 的来源。

## 配置和密钥

`AGENT_DB_PASSWORD`、`AGENT_QDRANT_API_KEY` 和模型 API key 只放在本地 `.env` 或部署平台的密钥管理中。启动日志、异常响应和提交内容不得包含这些值。若需要让 Maven 子进程读取 `.env`，请在当前 PowerShell 窗口导入变量后再启动；关闭窗口后变量应失效。

```powershell
Get-Content .env |
    Where-Object { $_ -match '^\s*[^#\s][^=]*=' } |
    ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
    }
```

## 停止和排查

```powershell
docker compose --env-file .env -f compose.dev.yml ps
docker compose --env-file .env -f compose.dev.yml logs --tail=100 mysql redis qdrant
docker compose --env-file .env -f compose.dev.yml down
```

不要使用 `down --volumes`，除非确认要删除本地开发数据。应用启动失败时先确认 MySQL 已通过健康检查、Qdrant HTTP `6333` 和 gRPC `6334` 可访问，以及 `AGENT_DB_URL` 与实际端口一致。
