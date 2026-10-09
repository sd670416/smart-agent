# Project Board Metrics Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将经营看板的项目数量、类型、状态和区域分布问题迁入 `project.query`，在可见项目范围内只统计审批完成记录。

**Architecture:** `smart-boot` 保持现有可信项目 ID 集合和 `project.query` 的服务端聚合；仅补缺失的项目区域分组字段及必要测试。`smart-agent` 将这四类指标交给 PROJECT 域，并保留明确询问其他经营指标时的现有看板链路；不修改前端看板页面。比较仍按每组条件重新查询。

**Tech Stack:** Java 8 smart-boot、Java 21 smart-agent、Spring、MyBatis-Plus、JUnit。

**Spec:** `docs/superpowers/specs/2026-10-08-board-to-business-query-migration-design.md`

## Global Constraints

- 可信项目范围沿用档案权限链路；AI 业务查询最终只取审批完成状态 `2`。审批查询工具不受此约束。
- 菜单和用户可见项目 ID 来自可信上下文；模型及前端不能扩大范围。
- 每轮重新调用工具取得实时数据；历史只用于理解筛选条件和指代。
- 改动仅限项目基础统计，不同时迁移合同、预算、财务、库存指标。

---

### Task 1: 固定项目权限范围和审批完成过滤

**Files:**
- Modify: `smart-boot/smart-business/smart-project/src/test/java/com/smart/project/service/BusProjectAiQueryServiceTest.java`
- Inspect: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusProjectServiceImpl.java`

**Interfaces:** `BusProjectServiceImpl.findAccessibleListForAi(boolean queryByMember, String trustedUserId)` 与 `queryForAi(ProjectQueryRequestVO)`。

- [x] 在已有可信项目范围测试基础上，新增列表与聚合必须绑定审批完成状态 `2` 的断言。
- [x] 运行 `mvn -pl smart-business/smart-project -am '-Dtest=BusProjectAiQueryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`；新增两个断言失败，确认测试能捕捉缺失的状态过滤。
- [x] 只在 `queryForAi` 服务端查询上加入审批完成过滤，不收窄可信项目 ID 集合，以免影响审批工具或其他授权判断。
- [x] 重跑同一测试：6 个用例通过，0 失败，0 错误（2026-10-08）。

### Task 2: 区域字段和聚合

**Files:**
- Modify: `smart-boot/smart-service/src/main/java/com/smart/service/project/query/AiProjectQueryFieldRegistry.java`
- Modify: `smart-boot/smart-business/smart-project/src/test/java/com/smart/project/service/BusProjectAiQueryServiceTest.java`
- Modify: `smart-boot/smart-ai/src/test/java/com/smart/ai/project/query/AiProjectQueryValidatorTest.java`

**Interfaces:** `AiProjectQueryFieldRegistry.require(String)`；`project.query` 的 `groupBy` 使用已注册业务字段名。

- [x] 对照经营看板 `getContractQuantityPie`：区域为 `region_ids` 对应的省级行政区，空区域不计入区域图。
- [x] 先写失败测试：`groupBy` 使用“项目区域”时产生省名分组列；模型仅能使用注册字段。
- [x] 运行专项测试确认失败原因是字段未注册。
- [x] 扩展 `projectProvince` 字段注册及 DAO 省名派生列，不改变“项目地址”的含义。
- [x] 运行 `smart-project` 专项测试：7 个通过；本地 SQL 核对省名及空值分布。带登录权限的端到端范围验证归入 Task 4。
- [x] 2026-10-09 补齐模型工具字段说明及 `projectRegion` 到 `projectProvince` 的明确别名映射；真实失败参数已录入回归测试。
- [ ] 区域统计修复后的页面复测与原看板结果对账。

### Task 3: 自然语言路由至项目查询

**Files:**
- Modify: `agent/src/main/java/com/smart/agent/routing/BoardDomainContributor.java`
- Modify: `agent/src/main/java/com/smart/agent/routing/ProjectDomainContributor.java`
- Modify: `agent/src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`
- Modify: `agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Test: `agent/src/test/java/com/smart/agent/routing/BusinessDomainRegistryTest.java`

**Interfaces:** PROJECT 域允许 `project.query`，BOARD 域在其他指标迁移前仍保留 `board.query` 等工具。

- [x] 写路由测试：项目数量/区域进入 PROJECT，经营看板利润与项目完成进度保留 BOARD，普通“查项目”不再制造看板歧义。
- [x] 运行专项测试确认 4 个预期失败。
- [x] 移出 BOARD 域的项目基础指标词，补 PROJECT 指标语义，并同步调整提示词；其他看板指标保留旧入口。
- [x] 2026-10-09 补齐“经营看板的区域统计”“项目看板的地区统计”的项目域路由；新增测试先复现误入 BOARD，修复后专项测试 25 个通过。仅看板权限不能查询已迁移的项目统计。
- [ ] Agent 路由/提示词专项测试已通过 15 个；`ChatControllerIT` 仍有 18 失败、2 错误，需分离原有失败并完成真实追问与每轮重查验收。

### Task 4: 联调与阶段记录

**Files:**
- Modify: `agent/docs/superpowers/specs/2026-10-08-board-to-business-query-migration-design.md`

- [ ] 用有项目档案权限、无权限、仅报备权限、项目成员开关开启、`system` 五类账号验证 AI 数量只包含其可见项目中审批完成的记录；档案页面仍可能显示 `1~4`。
- [ ] 分别询问“项目总数”“按项目类型统计”“按项目状态统计”“按区域统计”“比较两组项目”及“查第一个项目详情”。
- [ ] 记录测试环境、输入、预期、实际、构建命令和重启服务；仅在代码与人工结果均通过后标记阶段完成。

## 2026-10-09 路由补齐记录

- 本次仅修改 smart-agent；重启 smart-agent。此前区域字段的 smart-boot 改动若尚未重启，仍需重启 smart-boot。
- 验证命令：`mvn -o '-Dtest=QueryIntentResolverTest,BusinessDomainRegistryTest,ProjectQueryToolTest,SystemInstructionCatalogTest' test`。
- 结果：25 tests，0 failures，0 errors；不是完整端到端回归结果。
- 页面复测：新对话输入“经营看板的区域统计”，应调用 `project.query`，区域分组使用 `projectProvince`；追问“按项目类型统计数量”，应重新查询项目。
- 回归：输入“经营看板的实际项目利润”，暂时仍应使用未迁移的 BOARD 链路；不能误认为利润已经迁入项目查询。
- 本记录时仍需处理原看板类型空值“其他类型”、空区域排除、状态分布排除立项中和立项未通过的统计口径，并验证权限及追问链路。旧 BOARD 工具尚未移除，整体迁移未完成。

## 2026-10-09 项目分布口径收尾

- [x] 项目类型分组的 null 结果显示为“其他类型”，不改变项目数量；普通列表仍保留 null，不改变原始字段。
- [x] 省级区域分组在 SQL 分页前排除 null 和空字符串，分组总数与分页同步，不在分页后丢弃行。
- [x] 默认项目状态分布除关闭 `10` 外，再排除立项中 `1` 和立项未通过 `3`。只有按项目状态分组时追加此规则，项目总数、其他分组和普通列表不追加此规则。
- [x] 用户明确指定项目状态时尊重条件；嵌套条件及“项目状态”中文别名通过字段注册表识别。审批完成过滤与可信项目范围始终保留。
- [x] 测试先运行 13 个用例、出现 4 个预期失败；修复后同一套件 13 个通过、0 失败、0 错误。命令：`mvn -o -pl smart-business/smart-project -am '-Dtest=BusProjectAiQueryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`。
- [ ] 页面实际查询、账号权限与数量对账仍待验收；未宣称完整迁移或端到端回归通过。

### 本次重启与测试

本次仅需重启 smart-boot；smart-agent 和 smart-web 本轮未修改。此前 Agent 路由改动如尚未重启，需另外重启 smart-agent。

| 测试输入 | 预期 |
| --- | --- |
| 按项目类型统计数量 | 调用 project.query；存在 null 类型时显示“其他类型”，数量不丢失 |
| 按省份统计项目数量 | 调用 project.query，groupBy 为 projectProvince；不显示空区域组 |
| 按项目状态统计数量 | 默认不包含立项中、立项未通过和关闭；只含当前用户可见且审批完成的项目 |
| 只看立项中的项目，按项目状态统计数量 | 显式状态条件不被默认排除；没有审批完成且可见记录时合法返回 0 |
| 查询项目列表 | 普通列表不自动追加分布专用的状态排除或区域非空条件 |

对账必须用相同账号、项目范围和日期条件；总数与状态图可能因状态 `1/3` 排除而不同，区域图也可能因空区域而少于总数，不能一概要求所有图合计相等。
