# 新聊天启动指令

先阅读[总计划](00-start-here.md)。每个任务使用独立checkout/worktree最稳妥，先由用户在聊天中选择对应目录；不要让不同聊天在同一目录切分支。任务中的共同约束：UAT配置、保留他人改动、用户验证业务、不启动服务、不擅自改数据库、不修改其他Owner文件、完成后交接并说明重启和测试问题。

以下每段可直接作为相应新聊天的第一条消息。先执行A，A交接合入后再启动B/C/D/G。

## 聊天A

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\A-common-foundation.md，先阅读同目录00-start-here.md和已链接的业务方案。任务是公共VO、服务契约、基础指标及UAT来源核对；先完成这项，供其他聊天依赖。严格按文件所有权开发，不自行切分支，不覆盖现有改动。以UAT实际配置为准，不启动服务、不部署、不写数据库；保证主代码和测试代码编译通过，业务由我验证。已确认规则不用重问。完成后写A-handoff.md，固定契约版本和指标来源，说明依赖如何合入及后续可并行的任务。
```

## 聊天B

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\B-output-payment.md，先读00-start-here.md、已确认业务方案和A-handoff.md，并确认A实现已在当前checkout。只开发场景2产值与付款分析，遵守文件所有权；不要改公共路由、ChatOrchestrator、提示词或A接口，接入需求交H。使用UAT配置，不启动服务、不改数据库，以编译通过为检查目标，业务由我验证。完成写B-handoff.md，给工具请求/响应、来源权限、日期支持及测试问题。
```

## 聊天C

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\C-partner-history.md，先读00-start-here.md、已确认业务方案和A-handoff.md，并确认A已合入。负责甲方和供应商合作历史以及供应商净账款服务；先交甲方，再交供应商。不要修改其他任务或公共路由/提示词，接入需求写给H。UAT环境，不启动服务、不改数据库，以编译通过为检查目标，业务由我验证。完成写C-handoff.md，特别说明合同/结算/付款/收票/材料退款净额如何对应与去重。
```

## 聊天D

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\D-profit-ranking.md，先读00-start-here.md、已确认业务方案和A-handoff.md，并确认A已合入。只做场景7盈利排名，默认预算利润率降序前20并明确告知排序指标；全量排序再分页，不能缺预算按0算。遵守所有权，不改公共接入文件，交H接入。UAT环境，不启动服务、不改数据库，检查编译，业务由我验证。完成写D-handoff.md，列出并列、TopN、缺失、权限、追问和人工测试。
```

## 聊天G

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\G-site-risk.md，先读00-start-here.md、已确认业务方案和A-handoff.md，并确认A已合入。负责工期事实及进度/质量/安全风险Provider，优先交工期事实供E依赖，再交风险Provider。严格保留未评估状态与证据，禁止将未完工项目已历工期当最终总工期。不要改公共路由或其他Owner文件，交H接入。UAT环境，不启动服务、不改数据库，保证编译，业务由我验证。完成写G-handoff.md。
```

## 聊天E

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\E-project-comparison.md，先读00-start-here.md、业务方案和A/B/G-handoff.md，确认依赖实现已合入当前checkout。负责2至5个项目经营比较矩阵及相对基准差额，后端一次取数；不改B文件和公共路由，由H统一PROJECT_OPERATION域。UAT环境，不启动服务、不改数据库，以编译通过为检查目标，业务由我验证。缺少依赖先列出，不能造假实现。完成写E-handoff.md及2/3项目响应样例和测试问题。
```

## 聊天F

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\F-financial-risk.md，先读00-start-here.md、业务方案和A/B/C-handoff.md，确认依赖实现已合入。负责成本/付款/回款/开票/收票五维风险Provider，不做公共聊天工具或总览，由H统一。按已确认阈值，缺来源未评估，不重复扣退款，不把未付余额直接说成逾期拖欠。UAT环境，不启动服务、不改数据库，编译验证由你做，业务由我验收。完成写F-handoff.md，列出规则版本、配置格式、证据和测试问题。
```

## 聊天H

```text
请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\H-integration.md，先读00-start-here.md、已确认业务方案及已完成任务handoff。你独占公共路由、权限映射、提示词、上下文、风险总览；只接入已经合入的业务实现，按B/C/D/E/F/G交付分批接入。不要重写各任务公式或改Owner业务源码。保留每轮实时查询、歧义追问、原项目/审批/材料价功能，新增schema必须符合当前适配器支持关键字。UAT环境，不启动服务、不写数据库，编译检查，业务由我验证。每批告诉我重启哪些服务、怎么问、预期是什么；写H-handoff.md并说明下一步。
```

如果任务checkout缺少本方案目录，先由用户把方案文档同步到该checkout，不依据旧聊天记忆自行重建规则。A未交付时其余任务只能读代码和设计，不重复创建公共类型。
