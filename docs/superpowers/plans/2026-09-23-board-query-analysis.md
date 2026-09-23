# 8 个看板智能查询与比较分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有看板页面接口的前提下，为 8 个看板提供按原筛选项执行的单组查询、分组比较和明细下钻能力。

**Architecture:** 在 `agent` 侧保留自然语言识别，在 `smart-boot` 侧增加统一看板工具入口和看板定义注册表。每个看板定义明确菜单匹配、筛选字段、指标、原始接口、日期口径和结果字段；smart-boot 负责权限校验、参数转换和确定性计算，agent 负责工具选择、中文解释和上下文快照。

**Tech Stack:** Java 8 (`smart-boot`)、Java 21 (`agent`)、Spring Boot、现有 HTTP 工具调用协议、Vue 3/现有 smart-web API 模式。

**Spec:** `agent/docs/superpowers/specs/2026-09-23-board-query-analysis-design.md`

## Global Constraints

- 看板权限只按看板菜单权限判断；拥有看板菜单后允许该看板汇总、比较和下钻明细。
- 每个看板使用自己的筛选项和原接口日期字段，不使用跨看板通用日期假设。
- 项目看板未指定项目时不自动选择页面第一个项目；库存不增加页面没有的项目筛选。
- smart-boot 必须兼容 JDK 8；不引入 JDK 9+ API。
- 不执行 Maven、pnpm、构建或测试命令；由用户负责构建和验证。
- 所有用户可见错误返回中文友好提示；不返回 `Agent request failed`。

---

### Task 1: 建立看板定义与原接口字段映射

**Files:**
- Create: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardQueryDefinition.java`
- Create: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardMetricDefinition.java`
- Create: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardQueryDefinitionRegistry.java`
- Modify: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusBoardCompanyOperatingStatusServiceImpl.java`
- Inspect/modify: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusBoardBudgetService.java`, `BusBoardReceivableService.java`, `BusBoardSupplierService.java`, `BusBoardInventoryService.java`, `BusBoardProjectDrillServiceImpl.java`, `BusBoardSceneScheduleServiceImpl.java`, `smart-boot/smart-business/smart-bid/src/main/java/com/smart/bid/service/BusBoardBidService.java`

**Interfaces:**
- Produces registry lookup by `boardType`, metric key, allowed filter keys, menu route, source endpoint, date field and display metadata.
- Registry entries: `manage`, `budget`, `receivable`, `supplier`, `bid`, `inventory`, `project`, `gantt`.

- [ ] Record the exact endpoint, request parameter, actual entity date field, project field, response value fields and Chinese labels for each first-phase metric.
- [ ] Register only metrics already exposed by the page; reject unknown metric and filter keys with a Chinese validation message.
- [ ] Register fixed parameters (`approvalStatus=2`, `depositType=1`) for bid queries.
- [ ] Register inventory filters as warehouse type, warehouse IDs, material keys and operation date; do not register project filter.
- [ ] Keep registry classes Java 8 compatible and avoid changing existing page controller signatures.

### Task 2: Implement menu-only authorization and normalized request models

**Files:**
- Create: `smart-boot/smart-service/src/main/java/com/smart/service/board/BoardQueryService.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/board/BoardQueryRequest.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/board/BoardCompareRequest.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/board/BoardDetailRequest.java`
- Modify: existing authenticated menu/context helper used by `AuthenticatedAgentContextFactory`

**Interfaces:**
- `BoardQueryRequest`: `boardType`, `metric`, `filters`, `dimension`, `page`, `pageSize`, `orderBy`.
- `BoardCompareRequest`: `boardType`, `metric`, `groups[]`, `dimension`, `page`, `pageSize`.
- `BoardDetailRequest`: `boardType`, `metric`, `resultId`, `projectId`, `recordId`, `filters`, `page`, `pageSize`.
- Service methods: `query(BoardQueryRequest)`, `compare(BoardCompareRequest)`, `detail(BoardDetailRequest)`.

- [x] Resolve menu access by stable route name first and route path second for each board route.
- [ ] Return a distinct permission result: `您当前没有该看板的访问权限，请联系管理员授权`.
- [ ] Do not invoke project-member or business-menu data-scope checks after board menu authorization.
- [ ] Validate board-specific filter keys through the registry before calling business services.
- [ ] Return a normalized result containing board, metric, unit, filters, summary, groups, rows and drill identifiers.

### Task 3: Add smart-boot internal board query endpoints

**Files:**
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiBoardToolController.java`
- Modify: `smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiProjectToolController.java` and `AiApprovalToolController.java` only for shared internal tool registration conventions

**Interfaces:**
- `POST /internal/ai/tools/board-query`
- `POST /internal/ai/tools/board-compare`
- `POST /internal/ai/tools/board-detail`

- [ ] Bind the three request models without exposing arbitrary SQL, table names or internal entity fields.
- [ ] Map validation failures, permission failures, empty results and business exceptions to stable error codes and Chinese messages.
- [ ] Preserve trace/run identifiers in logs while omitting sensitive request payload fields.
- [ ] Keep existing endpoints unchanged for the web pages.

> 2026-09-23 progress: agent-side `board.query`/`board.compare`/`board.detail` tool contracts and smart-boot endpoint boundary are in place. The endpoints currently return a stable Chinese "正在接入" response; actual board adapters and menu enforcement remain in Task 4.

### Task 4: Implement deterministic single-group query adapters

**Files:**
- Create: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardQueryAdapter.java`
- Create: board-specific adapter classes under `.../board/adapter/`
- Modify: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardQueryDefinitionRegistry.java`

- [ ] Implement adapters for manage and budget first, using the exact existing service methods and request field names.
- [ ] Implement receivable and supplier adapters, including multi-project and supplier filters.
- [ ] Implement bid and inventory adapters with their fixed/default filters.
- [ ] Implement project and gantt adapters, applying gantt task name/status filtering to the returned task list when the backend endpoint does not accept those filters.
- [ ] Normalize each response to Chinese display labels while retaining stable IDs for drill-down.

> 2026-09-23 progress: the first deterministic dispatch for the management board has been connected to the existing `BusBoardCompanyOperatingStatusService` drill methods. Budget and the remaining boards are intentionally still pending because their service implementations live in business modules and need dedicated adapters rather than guessed calls.

### Task 5: Implement multi-group comparison

**Files:**
- Modify: `smart-boot/smart-service/src/main/java/com/smart/service/board/BoardQueryService.java`
- Create: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardCompareCalculator.java`

- [ ] Execute each group through the same single-group adapter; never merge raw conditions before querying.
- [ ] Calculate absolute difference and percentage change in Java 8-safe code; return `无法计算` when the baseline is zero.
- [ ] Compare grouped rows by stable dimension ID, not display name alone.
- [ ] Return common, added and removed dimension items for project/dimension comparisons.
- [ ] Reject comparisons across different board types or metrics with a clear prompt.

### Task 6: Persist query snapshots and ordinal follow-ups

**Files:**
- Modify: existing `agent` conversation context persistence classes used for approval snapshots
- Create/modify: board snapshot DTO/serialization classes under `agent/.../context/`

- [ ] Persist board type, metric, normalized filters, groups, result rows and stable IDs under tenant/user/conversation scope.
- [ ] Resolve follow-ups such as “和上个月比较”“只看第二组”“查看第 3 个项目” locally from the latest board snapshot.
- [ ] Reject ordinal references when there is no matching snapshot and ask the user to restate the condition.
- [ ] Do not persist system fields, tokens, SQL or raw credentials.

### Task 7: Register agent tools and natural-language routing

**Files:**
- Modify: agent tool definitions/registry and model prompt assembly
- Modify: agent friendly error mapping and debug response handling

- [ ] Register `board.query`, `board.compare`, and `board.detail` with names matching the allowed tool-name pattern.
- [ ] Include board-specific filter definitions and metric labels in the tool schema/prompt.
- [ ] Require clarification when a metric, project, date range or comparison group is ambiguous.
- [ ] Instruct the model to return Chinese explanations and table-style results, while calculations come from tool output.
- [ ] Preserve raw request/response visibility only in development mode as currently configured.

### Task 8: Add optional frontend board result presentation

**Files:**
- Modify: existing AI chat result renderer in `smart-web`
- Create if needed: `smart-web/src/components/business/ai/board-result.vue`

- [ ] Render summary cards, comparison groups, difference/rate columns and detail rows using existing chat styles.
- [ ] Show the active board, metric, filters and date basis in a compact header.
- [ ] Provide stable “查看明细” actions using returned IDs.
- [ ] Keep long result tables horizontally scrollable and avoid wrapping metric labels.

### Task 9: User-led verification checklist

**Files:**
- Modify: `agent/docs/superpowers/plans/2026-09-23-board-query-analysis.md` only for status notes

- [ ] User builds `smart-boot`, `agent` and frontend.
- [ ] Verify one query per board with empty/default filters.
- [ ] Verify one query per board with each page-specific filter.
- [ ] Verify two-period comparison and two-project comparison for manage, receivable and inventory.
- [ ] Verify unauthorized board menu returns the friendly permission message.
- [ ] Verify ordinal follow-up uses the stored snapshot and does not repeat the original query unnecessarily.

No build or test commands are run by the coding agent in this project.
