# 回款登记查询实施计划

**范围：** 用户已确认回款登记列表、数量及到账金额合计。归财务回款登记独立业务域，不代表全部已收项目款。沿用现有检出，不切分支、不提交、不启动应用、不连接数据库。

**接口：** `paymentRegistration.query`、`paymentRegistration.clarifyDate`；签名内部接口 `/internal/ai/tools/payment-registration-query`。返回既有 `ProjectQueryResultVO`，detail 分页，summary 精确 COUNT 与 SUM(received_amount)。

**日期：** receivedDate 到账日期、createDate 登记创建日期、projectCreateDate 项目创建日期。日期不明确追问，结束日包含整天。禁止累计回款字段求和。

**权限：** 可信上下文仅从回款登记页面叶子菜单生成 `menu:finance:payment-registration`，递归扫描多级菜单，父菜单或看板菜单不等于授权。system 账号沿用超管特例；请求项目与可信项目范围求交，空交集不查询。业务及项目审批完成，项目状态排除 1/3/10。

## 任务

- [x] 1. 先补菜单解析、后端查询及控制器测试并观察失败；实现字段白名单、日期校验、精确汇总、分页和签名权限保护。
- [x] 2. 先补 Agent 输入、签名客户端及路由测试；实现独立财务域、项目精确定位、日期确认、每轮实时查询及上下文恢复。
- [x] 3. 测试结果交回模型、数量追问、分页、日期选择、审批域切换、无权限不回退旧看板；运行 Agent 全量及后端定向测试。
- [x] 4. 回填真实验证结果、未验证项、重启范围和人工测试话术，开发完成后标注待验收。

**主要文件：** smart-finance 的 PaymentRegistrationAiQueryService 与 DAO/XML；smart-model 的 PaymentRegistrationQueryRequestVO；smart-service 的 PaymentRegistrationQueryService；smart-ai 的控制器、菜单解析器、AgentContextFactory；Agent 的 tool/payment、PaymentRegistrationDomainContributor、QueryIntentResolver、ChatOrchestrator、ToolExecutor 和 SystemInstructionCatalog。

**测试命令：** Boot：`mvn -o -pl smart-ai,smart-business/smart-finance -am '-Dtest=PaymentRegistrationAiQueryServiceTest,AiPaymentRegistrationToolControllerTest,PaymentRegistrationMenuResolverTest,AgentContextFactoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`。Agent：`mvn -o test`。每仓库 Maven 串行执行。

**人工验收：** 查询回款登记列表；追问数量、到账金额合计、下一页；按到账日期查一月及最后一天；日期不明确时确认后续查；同名项目先确认；切换待办；无回款菜单、仅财务父菜单、仅看板权限、空项目范围、system 账号分别验证。金额与相同账号、相同审批及日期范围的回款登记记录对账。

## 2026-10-16 实施结果：待验收

- 已实现独立回款登记业务域及上述列表、数量、到账金额汇总能力。结果继续交回模型生成中文业务答复，不改为直接渲染。
- 回款登记叶子菜单授权及 system 账号特例已接入；空可信项目范围不查询，服务端再次取交集。父级财务菜单、看板菜单不授予回款登记权限。
- 明确提问“回款登记”走新工具；回款登记待办、已办等流程问题仍走审批工具。本批不接管泛称“全部回款”或“已收项目款”的完整跨来源指标。
- 项目默认审批完成且排除状态 1/3/10；回款登记本身也仅审批完成。日期不明确先确认，结束日包含整天。金额合计只取到账金额，不累加累计金额。
- Agent 全量 `mvn -o test`：418 项通过，0 失败、0 错误、0 跳过。随后补充客户端 HTTP 403 错误域测试，客户端定向 3 项通过（全量结果不冒称已包含此新增用例）。
- Boot 上述定向命令：smart-ai 21 项、smart-finance 7 项，共 28 项通过；包含嵌套菜单权限、system 授权、项目交集、空范围、到账日期结束日及反向日期校验。
- 财务模块原先缺少 JUnit 测试依赖，已按现有 smart-pm 模式补齐；新增测试中的旧产值编号 setter 已更正。两个仓库 `git diff --check` 无空白错误，仅有 CRLF 提示。
- 未启动服务、未连接数据库、未切换分支、未提交代码；真实模型路由、数据库租户 SQL 重写及页面金额对账仍待人工验收。单元测试不等于真实端到端验证。

### 重启与测试话术

重新构建并重启 **smart-boot、smart-agent**；本批未修改 smart-web，无需重启。无需数据库结构迁移或新增菜单记录，使用现有回款登记页面菜单。

| 测试话术 | 预期 |
| --- | --- |
| 查询回款登记列表 | 展示中文字段列表，仅可见项目、审批完成记录 |
| 随后：数量有多少；到账金额合计多少；下一页 | 每轮重新调用回款登记工具，不复用历史金额或只计算当前页 |
| 按到账日期查询2026年1月的回款登记金额合计 | 按到账日期统计，包含1月31日全天 |
| 查询2026年1月回款登记 | 日期含义不明确先提供选择，选到账日期后继续原查询 |
| 查询某项目的回款登记 | 精确定位项目；同名项目先确认，不扩大范围 |
| 随后：查我的待办 | 正确切换审批查询 |
| 无回款登记菜单的账号查询回款登记 | 提示回款登记查看权限不足，不误提示项目权限，不回退旧看板 |

金额只与相同条件的回款登记来源对账，不与包含其他回款的“已收项目款”总额直接比较。回款详情下钻、其他回款及跨来源完整指标、两组比较不在本批交付范围。
