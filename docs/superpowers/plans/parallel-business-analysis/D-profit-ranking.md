# D 盈利与利润率排名实施计划

**Goal:** 完成场景7，默认预算利润率降序前20个项目，支持其他利润指标与稳定分页。
**Architecture:** 读取A统一利润快照，全量计算、过滤和排序后分页；不改原利润普通查询与compare行为。
**Tech Stack:** Boot Java8/BigDecimal、Agent Java21。
**Spec:** [总计划](00-start-here.md)、[已确认规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A-handoff.md。

## 依赖与文件

A合入后可并行。独占：
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/ranking/ProjectProfitRankingService.java`。
- Boot `smart-service/src/main/java/com/smart/service/analysis/ranking/ProjectProfitRankingQueryService.java`（任务私有子包）。
- Boot `smart-model/src/main/java/com/smart/model/business/analysis/ranking/ProjectProfitRankingRequestVO.java`。
- Boot `smart-ai/src/main/java/com/smart/ai/controller/AiProjectProfitRankingToolController.java`。
- Agent `src/main/java/com/smart/agent/tool/profit/ranking/` 下专属Input、Tool、Client、Configuration。

不修改原profit Client/Input/Controller/Mapper。确有必要修复原有公式时先记录D-handoff交A/H统一，不能在本任务分支形成第二套基础指标定义。

## 接口

Tool `projectProfit.rank`，POST `/internal/ai/tools/project-profit-ranking`。复用PROJECT_PROFIT域，但该域公共descriptor由H追加新工具。

请求：metric(budgetProfitRate/budgetProfit/actualProfitRate/actualProfit)、direction(desc/asc)、topN（默认20，1至100；不把每页当topN）、page/pageSize（1至100）、项目筛选及dateMode。CURRENT默认；其他日期受A真实能力限制。

响应ProjectQueryResultVO：rank、projectId/Code/Name、性质、经理、合同额、目标成本、对应利润及利润率、rankStatus；appliedFilters清楚返回排序指标、方向、有效项目数、不可排名数。summary另包含总量信息，不用页面行数代替项目总数。

## 执行步骤

- [ ] 读取A指标完整性规则，构造需要的可信项目子集，不允许用户projectIds扩大scope。
- [ ] 缺目标成本/缺合同/比率分母0时不参与对应排名；真实预算0允许参与，不能与无预算记录混淆。保留负利润。
- [ ] 按BigDecimal未提前四舍五入数值比较，金额同值或率同值同名次；并列采用竞赛排名1、1、3，项目编号/ID稳定打散行顺序。
- [ ] 先排序、排名再截取topN和分页；默认前20条，多人并列不会无限扩张TopN，响应说明截取规则。
- [ ] 按指标验证来源菜单：只算预算榜不要求所有实际收支来源；少权限拒绝该指标，不换成其他榜单。
- [ ] 完成Controller及签名Agent工具，新工具独立bean命名不撞原有profit工具。
- [ ] 编译并写D-handoff，给H路由示例、默认提示文案和JSON契约。

## 验收

H接入后重启Boot/Agent。“做个盈利排名”应明确“按预算利润率从高到低，展示前20个项目”。“改按利润金额”“倒过来”“下一页”“只看亏损项目”按已支持请求筛选执行并实时查询，不把序号当项目ID。对照A合同1000万、成本800万=20%，B合同500万、成本350万=30%：率榜B在前，金额榜A在前。
