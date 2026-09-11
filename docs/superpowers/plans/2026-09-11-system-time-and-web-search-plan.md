# System Time and Web Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为工程管理智能助手增加可靠的系统时间能力和可在 OpenAI、智谱之间切换的通用公开互联网搜索能力。

**Architecture:** Agent 注册统一的 `system.current_time` 与 `web.search` 工具。联网工具通过 `WebSearchProvider` 隔离 OpenAI、智谱原生协议，通过 `WebSearchPolicy` 在出网前阻止内部业务数据与敏感内容；编排层和前端只消费统一结果。

**Tech Stack:** Java 21、Spring Boot、WebClient、LangChain4j、JUnit 5、Vue 3、Node Test

**Spec:** `docs/superpowers/specs/2026-09-11-system-time-and-web-search-design.md`

## Global Constraints

- 直接在当前目录开发，不使用 worktree。
- `smart-agent` 使用 Java 21；不得向 `smart-boot` 引入 Java 8 不支持的语法。
- 内部项目、合同、人员、Token、权限、附件和知识库原文不得发送到公网。
- 业务查询继续复用菜单、按钮和数据维度权限；联网权限使用 `ai:web-search`。
- 普通回答始终使用简体中文；联网回答必须展示公开来源。
- 每个任务完成并验证后暂停，等待用户确认效果。

---

### Task 1: 系统时间工具

**Files:**
- Create: `src/main/java/com/smart/agent/tool/system/CurrentTimeInput.java`
- Create: `src/main/java/com/smart/agent/tool/system/CurrentTimeResult.java`
- Create: `src/main/java/com/smart/agent/tool/system/CurrentTimeTool.java`
- Create: `src/main/java/com/smart/agent/tool/system/TimeToolConfiguration.java`
- Create: `src/test/java/com/smart/agent/tool/system/CurrentTimeToolTest.java`
- Modify: `src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`
- Modify: `src/test/java/com/smart/agent/model/ModelGatewayContractTest.java`

**Interfaces:**
- Produces: `CurrentTimeTool implements AgentTool<CurrentTimeInput, CurrentTimeResult>`，工具键 `system.current_time`，风险等级 L0。
- Consumes: `Clock` 与默认 `ZoneId`，测试使用固定时钟。

- [x] **Step 1: 写失败测试**

测试固定时钟 `2026-09-11T06:30:00Z` 在 `Asia/Shanghai` 返回日期 `2026-09-11`、时间 `14:30:00`、星期五和带偏移量时间；验证工具 schema 不接受任意系统命令。

- [x] **Step 2: 验证测试因工具不存在而失败**

Run: `mvn -Dtest=CurrentTimeToolTest test`

Expected: FAIL，原因是 `CurrentTimeTool` 尚不存在。

- [x] **Step 3: 实现最小时间工具**

使用 `ZonedDateTime.now(clock.withZone(zoneId))` 构造 `CurrentTimeResult`。输入只允许可选 `timezone`；非法时区抛出 `AGENT_TIMEZONE_INVALID`，默认时区读取 `agent.time-zone=Asia/Shanghai`。

- [x] **Step 4: 更新模型路由提示**

在 `SystemInstructionCatalog` 明确：当前时间和相对日期先调用 `system.current_time`；“今年项目”取得边界后调用业务工具，不调用公网搜索。

- [x] **Step 5: 验证并形成阶段检查点**

Run: `mvn -Dtest=CurrentTimeToolTest,ModelGatewayContractTest test`

Expected: PASS。手工验证“当前日期和时间”与“查询今年立项项目”的工具调用顺序，向用户汇报后暂停。

进度（2026-09-11）：时间工具实现、L0 权限执行、友好错误和 Spring 自动注册共 6 项针对性测试通过；真实聊天手工验收待服务重启后由用户确认。`ModelGatewayContractTest` 存在与本任务无关的既有契约断言差异，本阶段未修改。

### Task 2: 统一联网协议与出网安全策略

**Files:**
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchInput.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchResult.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchSource.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchProvider.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchPolicy.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchTool.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchProperties.java`
- Test: `src/test/java/com/smart/agent/tool/web/WebSearchPolicyTest.java`
- Test: `src/test/java/com/smart/agent/tool/web/WebSearchToolTest.java`

**Interfaces:**
- Produces: `WebSearchProvider.search(WebSearchInput)`；`WebSearchPolicy.validate(String query)`；工具键 `web.search`。
- Consumes: 当前 `ToolRegistry`、`ToolExecutor` 和 `AgentException`。

- [x] **Step 1: 写安全策略失败测试**

覆盖公开天气允许、公开政策允许，以及 Bearer Token、JWT、项目内部 ID、SQL、附件 URL、合同内部内容被拒绝。断言拒绝码为 `AGENT_WEB_SEARCH_SENSITIVE_INPUT`。

- [x] **Step 2: 验证测试因策略不存在而失败**

Run: `mvn -Dtest=WebSearchPolicyTest test`

Expected: FAIL，原因是策略类型不存在。

- [x] **Step 3: 实现白名单式输入协议和安全策略**

`WebSearchInput` 只包含 `query`、`maxResults`、`freshness`。限制 query 1-300 字、maxResults 1-10；策略拒绝鉴权信息、内部 URL、长数字业务 ID、数据库/权限系统字段及明确内部业务查询意图。

- [x] **Step 4: 实现工具开关和权限**

`WebSearchTool.requiredPermission()` 返回 `ai:web-search`，风险等级 L1。`agent.web-search.enabled=false` 时返回 `AGENT_WEB_SEARCH_DISABLED`；工具只向 Provider 传递清洗后的单轮 query。

- [ ] **Step 5: 验证并形成阶段检查点**

Run: `mvn -Dtest=WebSearchPolicyTest,WebSearchToolTest test`

Expected: PASS。向用户展示允许/拒绝样例后暂停。

进度（2026-09-11）：统一输入输出协议、联网开关、`ai:web-search` 权限和出网安全策略已完成。公开天气、政策、汇率允许；鉴权信息、内网地址、SQL、长业务 ID、附件原文和系统权限字段会在调用 Provider 前拒绝。阶段测试 7 项、连同时间工具回归共 13 项通过。外部 Provider 尚未接入，联网默认关闭。

### Task 3: OpenAI 原生联网适配器

**Files:**
- Create: `src/main/java/com/smart/agent/tool/web/OpenAiWebSearchProvider.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchClientConfiguration.java`
- Test: `src/test/java/com/smart/agent/tool/web/OpenAiWebSearchProviderTest.java`
- Modify: `src/main/java/com/smart/agent/model/ModelGatewayProperties.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Consumes: `WebSearchProvider`、当前模型 base URL/API key/model name。
- Produces: 统一 `WebSearchResult`，provider 值 `openai`。

- [x] **Step 1: 写 OpenAI 响应转换失败测试**

使用本地 HTTP stub 返回包含回答文本、URL citation、标题的 OpenAI 原生联网响应，断言转换后的摘要、来源、检索时间正确且请求不含会话历史。

- [x] **Step 2: 验证测试因适配器不存在而失败**

Run: `mvn -Dtest=OpenAiWebSearchProviderTest test`

Expected: FAIL。

- [x] **Step 3: 实现 OpenAI 请求和响应转换**

通过独立 WebClient 调用 OpenAI 原生联网端点；只发送 query、模型和 Web Search 工具配置。非 2xx、限流与超时分别映射为 `AGENT_WEB_SEARCH_FAILED`、`AGENT_WEB_SEARCH_RATE_LIMITED`、`AGENT_WEB_SEARCH_TIMEOUT`。

- [ ] **Step 4: 验证并形成阶段检查点**

Run: `mvn -Dtest=OpenAiWebSearchProviderTest,WebSearchToolTest test`

Expected: PASS。使用配置的 OpenAI 模型执行一次公开查询并核对来源后暂停。

进度（2026-09-11）：OpenAI `/responses` 联网适配、URL 引用转换、HTTPS 来源过滤、429/超时/服务失败映射、Spring 装配和权限可见性已完成，相关 11 项测试通过。复用现有模型 `base-url/api-key/chat-model`，请求仅包含清洗后的单轮 query。真实 OpenAI 账号联网验收待有效配置后执行。

### Task 4: 智谱原生联网适配器与自动切换

**Files:**
- Create: `src/main/java/com/smart/agent/tool/web/ZhipuWebSearchProvider.java`
- Create: `src/main/java/com/smart/agent/tool/web/WebSearchProviderRouter.java`
- Test: `src/test/java/com/smart/agent/tool/web/ZhipuWebSearchProviderTest.java`
- Test: `src/test/java/com/smart/agent/tool/web/WebSearchProviderRouterTest.java`
- Modify: `src/main/java/com/smart/agent/tool/web/WebSearchClientConfiguration.java`

**Interfaces:**
- Consumes: 当前模型 base URL/model name；OpenAI 和智谱 Provider。
- Produces: `WebSearchProviderRouter.search(WebSearchInput)`，根据配置选择 Provider。

- [x] **Step 1: 写智谱转换与路由失败测试**

覆盖智谱联网响应转统一来源；base URL/model 标识智谱时选择智谱，OpenAI 时选择 OpenAI，未知供应商返回 `AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED`。

- [x] **Step 2: 验证测试因适配器和路由不存在而失败**

Run: `mvn -Dtest=ZhipuWebSearchProviderTest,WebSearchProviderRouterTest test`

Expected: FAIL。

- [x] **Step 3: 实现智谱适配与供应商路由**

智谱适配器只发送清洗 query 和原生搜索配置；路由由服务端模型配置决定，拒绝模型或用户在工具参数中指定供应商。

- [x] **Step 4: 验证切换**

Run: `mvn -Dtest=OpenAiWebSearchProviderTest,ZhipuWebSearchProviderTest,WebSearchProviderRouterTest test`

Expected: PASS。分别切换两套本地配置，确认工具 schema 与聊天编排无需变化，然后暂停。

进度（2026-09-11）：智谱 `/chat/completions` 联网适配、公开来源转换及自动路由已完成。`auto` 会根据服务端 base URL 和模型名识别 OpenAI/智谱；显式配置可覆盖识别结果，未知供应商不会尝试出网。补充了模型网关 Schema 兼容测试，相关 18 项测试通过。

### Task 5: 统一中文错误、审计与持久化

**Files:**
- Modify: `src/main/java/com/smart/agent/chat/AgentErrorMessageCatalog.java`
- Modify: `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Modify: `src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`
- Test: `src/test/java/com/smart/agent/chat/AgentErrorMessageCatalogTest.java`
- Test: `src/test/java/com/smart/agent/chat/ChatControllerIT.java`

**Interfaces:**
- Consumes: 现有 `AI_DEBUG_TOOL_REQUEST/RESULT/ERROR` 和 `ai_run_step`。
- Produces: 可刷新恢复的联网请求摘要、供应商、耗时、结果来源和友好错误。

- [x] **Step 1: 写错误与持久化失败测试**

断言禁用、无权限、敏感输入、供应商不支持、限流、超时、无来源均返回具体中文；联网工具请求、结果和错误写入步骤，但不含 API key、Authorization 和完整内部上下文。

- [x] **Step 2: 验证测试失败**

Run: `mvn -Dtest=AgentErrorMessageCatalogTest,ChatControllerIT test`

Expected: FAIL，原因是联网错误码与持久化断言未满足。

- [x] **Step 3: 实现错误映射和安全审计摘要**

新增 `AGENT_WEB_SEARCH_DISABLED/FORBIDDEN/SENSITIVE_INPUT/PROVIDER_UNSUPPORTED/RATE_LIMITED/TIMEOUT/FAILED/NO_RESULTS` 中文文案。调试记录只保留 query 摘要、provider、耗时和公开来源。

- [x] **Step 4: 验证并形成阶段检查点**

Run: `mvn -Dtest=AgentErrorMessageCatalogTest,ChatControllerIT test`

Expected: PASS。刷新会话确认联网处理信息仍显示后暂停。

进度（2026-09-11）：联网禁用、无权限、敏感输入、供应商不支持、限流、超时、失败和无可靠来源均已增加具体中文提示。`web.search` 调试步骤只持久化白名单参数；敏感查询保存为已拦截标记，不保存原文。成功结果持久化 provider、耗时和 HTTPS 公开来源。修复了异步工具异常包装导致安全错误码丢失的问题，本阶段 11 项测试通过。

### Task 6: 来源展示与端到端验收

**Files:**
- Modify: `../smart-web/src/views/ai/assistant/index.vue`
- Modify: `../smart-web/src/views/ai/assistant/composables/conversation-events.js`
- Test: `../smart-web/src/views/ai/assistant/composables/conversation-events.test.js`
- Modify: `src/main/resources/application-local.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: `WebSearchResult.sources` 与已持久化调试步骤。
- Produces: 聊天中的来源列表和安全外链。

- [ ] **Step 1: 写历史来源恢复失败测试**

构造包含来源的实时事件与历史步骤，断言刷新前后生成相同来源数据；危险协议 URL 不进入可点击链接。

- [ ] **Step 2: 验证测试失败**

Run: `node --test src/views/ai/assistant/composables/conversation-events.test.js`

Workdir: `../smart-web`

Expected: FAIL，原因是来源尚未映射。

- [ ] **Step 3: 实现来源展示**

在助手回答下展示来源标题与域名；仅允许 `https:`，链接使用新窗口和 `rel="noopener noreferrer"`。不新增嵌套卡片，不展示 provider 调试字段。

- [ ] **Step 4: 增加配置说明**

在 `application-local.yml` 增加关闭状态的示例配置，在 README 说明 OpenAI/智谱启用条件、权限与安全限制，不写入真实密钥。

- [ ] **Step 5: 完整验证**

Run: `mvn test`

Workdir: `.`

Run: `pnpm build:test`

Workdir: `../smart-web`

Expected: 两端构建通过。人工验收当前时间、天气、公开政策、今年项目、内部合同禁止出网、OpenAI/智谱切换、刷新回显和来源链接。
