# E 多项目经营横向比较实施计划

**Goal:** 完成场景6，2至5个项目的统一经营指标矩阵及差额。
**Architecture:** 后端编排A基础指标、B产值回款、G工期事实，一次工具调用返回矩阵；不依赖模型自行跨页算金额。
**Tech Stack:** Boot Java8/BigDecimal、Agent Java21。
**Spec:** [总计划](00-start-here.md)、[已确认规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A/B/G-handoff.md。

## 依赖及独占文件

A、B、G合入后再实现正式编排，可先只读设计。不得临时实现假服务让编译通过。
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/comparison/ProjectOperationComparisonService.java`。
- Boot `smart-service/src/main/java/com/smart/service/analysis/comparison/ProjectOperationCompareService.java`。
- Boot `smart-model/src/main/java/com/smart/model/business/analysis/comparison/ProjectOperationCompareRequestVO.java`。
- Boot `smart-ai/src/main/java/com/smart/ai/controller/AiProjectOperationCompareToolController.java`。
- Agent `src/main/java/com/smart/agent/tool/operationcompare/` 下CompareInput、Tool、Client、Configuration。

与B共享域名PROJECT_OPERATION，但不修改B的文件或创建第二个同CODE域Contributor；交H统一注册两个工具。

## 请求与结果

Tool `projectOperation.compare`，POST `/internal/ai/tools/project-operation-compare`。

请求projects数组，每项projectId/projectCode/projectName；2至5个去重真实项目。metricKeys受支持目录、baselineProjectId、dateMode。多项目同名必须逐个确认，不能默认第一个。未指定基准时以用户列出的第一个已确认项目为基准，并在答复说明；无法保持用户次序则先确认。

第一版CURRENT；PERIOD/AS_OF不能支持统一指标时明确说明，不以当前累计数据模拟同比/历史。

矩阵使用ProjectQueryResultVO，columns为metricLabel、unit、项目列p1至p5；业务标签含项目名称。另附真实项目定位映射和比较分组条件至appliedFilters。需要差额时增加相对基准的数值列，利润率差单位为百分点，不是百分比增长率。

## 执行步骤

- [ ] 读取A/B/G快照和指标目录，确定每个指标来源与权限，避免整份比较无条件要求所有菜单。
- [ ] 每个项目重新可信定位，交集校验后按确定顺序取数，记录同一查询统计时刻；不声称跨数据库事务绝对同一历史快照。
- [ ] 组合项目类型/性质、经理、最终合同额、有效目标成本、预算利润/率、实际收支利润/率、成本率、回款与比例、工期。
- [ ] 缺某指标只在该格给未评估/无法计算，不能补0后算差额；有权限问题说明对应来源限制。
- [ ] 工期区分合同、计划、已历和完工实际；未完工不输出最终延期天数，可显示任务逾期事实。
- [ ] 计算金额差额、成本率/利润率百分点差，基准为0不算倍数；亏损按真实符号展示。
- [ ] 实现签名Controller/Tool，后端校验项目个数、指标key和基准属于比较集合，禁止任意字段注入。
- [ ] 编译，写E-handoff供H接入域和多项目上下文，记录一份2项目和3项目响应样例。

## 验收

H接入后重启Boot/Agent。问“比较A和B的合同额、预算利润率、回款与工期”“再加C，以A为基准”“去掉B”“为什么C利润率算不出来”。核对矩阵项目次序、真实ID、差额方向、百分点、缺预算以及未完工字段，明确排序/比较不改变原普通项目查询。
