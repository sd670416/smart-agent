# B 产值与付款分析实施计划

**Goal:** 完成场景2，按项目联合查询审批完成产值与甲方回款并显示两类回款比例。
**Architecture:** 实现A定义的ProjectOutputPaymentAnalysisService，复用公共金额指标；业务聚合在Boot完成，Agent一次调用取得结果。
**Tech Stack:** Boot Java8、Agent Java21、既有ProjectQueryResult响应。
**Spec:** [总计划](00-start-here.md)、[业务规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A-handoff.md。

## 依赖与独占文件

A契约和实现已合入才开始。新文件：
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/operation/` 下本任务ServiceImpl、Dao。
- Boot `smart-business/smart-project/src/main/resources/mapper/analysis/ProjectOutputPaymentAnalysisDao.xml`。
- Boot `smart-model/src/main/java/com/smart/model/business/analysis/operation/ProjectOperationQueryRequestVO.java`，请求私有子包，不改A公共VO。
- Boot `smart-ai/src/main/java/com/smart/ai/controller/AiProjectOperationToolController.java`，只处理query；compare由E另建Controller。
- Agent `src/main/java/com/smart/agent/tool/operation/` 下 `ProjectOperationQueryInput`、`ProjectOperationQueryTool`、`ProjectOperationBusinessClient`、`SmartBootProjectOperationBusinessClient`、`ProjectOperationBusinessClientConfiguration`。

不改已有output工具、路由、提示词、权限工厂、A源码或E将新增的比较文件。Controller及内部API签名照既有模式实现，外部不接受可信tenant与权限字段。

## 接口与指标

Tool `projectOperation.query`，POST `/internal/ai/tools/project-operation-query`，返回既有ProjectQueryResultVO。

请求：projectId/projectCode/projectName、mode(summary/detail)、dateMode(CURRENT/PERIOD/AS_OF)、dateField/startDate/endDate、page/pageSize。单项目名称重复需澄清；组织/性质/经理/甲方筛选先转成可信项目子集，所支持字段在B-handoff明确列出，不硬编码猜测。

服务load继承总计划签名。新增metrics：`completedOutputAmount`、`reportedOutputAmount`、`confirmedOutputAmount`均取审批完成产值单的本期产值合计；`contractReceiptRate`=回款登记/最终合同额×100；`outputReceiptRate`=回款登记/确认产值×100；`unreceivedAmount`=确认产值−回款登记。

其他收入单独显示，不进入回款比例。实际完成、上报甲方、系统确认同一个值，页面默认一个清楚的产值列，用户明确分别问时解释同一统计定义，不展示恒为0差额。

## 执行步骤

- [ ] 读取A接口与既有产值/应收SQL，确认粒度；每个项目的产值先SUM本期金额，不SUM累计字段。
- [ ] 实现load与证据定位，来源少权限按状态返回，派生比率缺来源不算0；匹配项目范围为空不得查询全量。
- [ ] 实现query请求验证、字段中文映射、全量summary与列表分页；汇总率按金额重算。
- [ ] 实现Agent输入/签名Client/Tool，schema仅用当前支持关键字；允许值后端校验。
- [ ] 只读日期来源核对；CURRENT默认。日期不支持的返回明确未支持状态交H追问，不能悄悄忽略。
- [ ] 编译Boot业务实现和Agent，写B-handoff：接口JSON示例、权限来源、响应columns、H接入提示和日期支持矩阵。
- [ ] 标记待H接入/待人工验收，不修改公共路由冒称可直接聊天。

## 用户测试及预期

H接入后重启Boot/Agent，无Web/数据库变更。询问“A项目完成了多少产值，上报多少，甲方付了多少？”“合同回款比例和产值回款比例分别多少？”“改查B项目”“只看未收余额大于0的项目”（最后一项只有请求已实现对应筛选时才标支持）。核对实际产值250万、回款100万、合同1000万时，产值回款率40%、合同回款率10%；其他收入不能抬高这两项。
