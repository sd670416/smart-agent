# A 公共指标基础实施计划

**Goal:** 固定B至H共用的强类型契约、可信范围转换和经营基础指标，核对UAT真实来源。
**Architecture:** smart-model定义VO，smart-service定义接口，smart-project提供基础指标实现。通过既有服务接口或只读Mapper复用业务来源，不增加业务模块之间的循环依赖。
**Tech Stack:** Boot Java8/MyBatis/BigDecimal。
**Spec:** [业务方案](../2026-11-20-business-analysis-scenarios-proposal.md)、[总计划](00-start-here.md)。

2026-11-20执行完成：公共契约analysis-v1及当前累计基础指标已实现、编译通过，UAT只读SQL已核对。实际交接与实现边界见[A-handoff.md](A-handoff.md)，不代表业务聊天或人工验收已完成。

## 范围与所有权

只有A能创建/修改公共VO和服务契约。先完成并交接，再启动依赖任务。公共接口一次定稿，后续新增字段按兼容方式追加，由A或用户指定的统一维护者执行。

创建完整包路径：
- `smart-boot/smart-model/src/main/java/com/smart/model/business/analysis/`：总计划列出的七个公共VO，以及表达指标状态/日期模式的类型。
- `smart-boot/smart-service/src/main/java/com/smart/service/analysis/`：总计划五个公共服务接口。这里只定义接口，不放Spring实现和占位返回。
- `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/analysis/core/ProjectAnalysisMetricServiceImpl.java`。
- `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/analysis/core/ProjectAnalysisMetricDao.java`。
- `smart-boot/smart-business/smart-project/src/main/resources/mapper/ProjectAnalysisMetricDao.xml`；核对现有扫描为mapper/*.xml，实际使用根目录，未改公共配置。
- `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/analysis/core/ProjectAnalysisMapperConfiguration.java`：注册A独占包内的Mapper。

不修改原利润/预算实现、公共路由或profile。所需既有来源改动交H处理，优先通过只读SQL片段/服务接口复用，不复制出第二套不一致公式。

## 固定字段契约

AnalysisScopeVO：`String tenantId; Set<String> projectIds; Set<String> permissions; boolean queryPermissionsEnforced`。权限状态从可信上下文取，不接受普通客户端声明。

AnalysisPeriodVO：`String mode; String dateField; String startDate; String endDate`，mode只能CURRENT/PERIOD/AS_OF；Asia/Shanghai，日期结束包含当天，SQL用次日零点排他上限。

AnalysisMetricVO：`String key,label,unit,status,reason; BigDecimal value; Long sampleCount`。状态OK/NO_RECORD/MISSING_SOURCE/NOT_AUTHORIZED/NOT_APPLICABLE/UNSUPPORTED；没有业务单据不一定是数据缺失，原始关键金额为空也不等于0。OK允许真实0，分母无法计算时不能返回0比率。

AnalysisEvidenceVO：`String businessType,recordId,recordNo,businessDate,label`。

ProjectAnalysisSnapshotVO：`String projectId,projectCode,projectName,projectType,projectNature,organizationId,managerId,managerName,supplierId,contractType,contractId; Map<String,AnalysisMetricVO> metrics; List<AnalysisEvidenceVO> evidence; Date asOf`。供应商字段仅C账款快照使用，不作为项目身份。

SiteAnalysisSnapshotVO：`String projectId,actualStartDate,actualEndDate,latestProgressDate,latestQualityCheckDate,latestSafetyCheckDate; Map<String,AnalysisMetricVO> metrics; List<AnalysisEvidenceVO> evidence; Date asOf`。日期字符串统一ISO日期或明确精度的日期时间；未发生/不可确认时为空，由相关指标status/reason说明，不填造日期。

AnalysisRiskVO：`String projectId,dimension,ruleCode,ruleVersion,level,evaluationStatus,reason,suggestion; Map<String,AnalysisMetricVO> facts; List<AnalysisEvidenceVO> evidence; Date asOf`。等级HIGH/MEDIUM/LOW/NONE；状态EVALUATED/NOT_EVALUATED。未评估level为空，不用NONE。

字段使用Java8兼容POJO，遵循本项目Lombok惯例。接口签名严格采用总计划，公共基础仅实现ProjectAnalysisMetricService，其余接口由B/C/G/F分别实现。

## 基础指标目录

| key | 单位 | 定义 |
| --- | --- | --- |
| finalContractAmount | CNY | 一对一有效施工合同最终金额；重复有效合同为数据异常 |
| targetCostAmount | CNY | 审批完成编制与变更合并后的有效目标成本 |
| paymentReceivedAmount | CNY | 审批完成回款登记 |
| otherIncomeAmount | CNY | 审批完成其他收入 |
| paymentAmount | CNY | 审批完成付款申请金额 |
| otherPaymentAmount | CNY | 审批完成其他付款 |
| reimburseAmount | CNY | 审批完成项目报销 |
| refundAmount | CNY | 同一来源范围内材料退款调整，不重复扣除 |
| netPaymentAmount | CNY | 付款申请＋其他付款＋项目报销−材料退款 |
| totalIncomeAmount | CNY | 回款登记＋其他收入 |
| budgetProfit | CNY | 最终合同额−有效目标成本 |
| budgetProfitRate | PERCENT | 预算利润/最终合同额×100 |
| actualProfit | CNY | 总收入−净付款，业务收支估算 |
| actualProfitRate | PERCENT | 实际收支利润/总收入×100 |
| costRate | PERCENT | 有效目标成本/最终合同额×100 |
| invoiceApplicationAmount | CNY | 审批完成开票申请金额，不称实际开票 |

未请求指标不取数；计算派生指标先检查全部来源状态及权限。不要因为缺预算而计算100%利润率。聚合比率用总分子/总分母。

## 执行步骤

- [x] 读取原有ProjectProfitAiQueryService、ProjectFundingDao.xml、BudgetCostAiDao.xml、施工合同查询及权限实现，确认真实字段。
- [x] 只读核对UAT配置及样本：合同重复、预算变更、回款/其他收入、付款退款、无预算项目；数据库连接权限受限时按工具要求申请，不输出密码。
- [x] 创建公共VO、目录状态和五个接口；将确切目录与权限key清单写A-handoff。
- [x] 实现基础指标load；强制租户/项目/审批/删除条件，先按来源汇总，再按项目关联。所有分组与分页上限检查明确。
- [x] 日期CURRENT完整支持。PERIOD/AS_OF逐指标核对真实业务日期与历史限制，不能实现的返回UNSUPPORTED并给中文原因；不可套项目创建日期或当前余额。
- [x] 在A-handoff写每项来源和证据定位方式，明确期间和历史模式尚未实现、运行账号权限待接入后人工验收，不编造数据库结果。
- [x] 编译覆盖smart-project和smart-ai依赖，按总计划报告编译结果；不启动服务、不写UAT。
- [x] 发布契约v1交接，提醒用户先合入A再开B/C/D/G。接口没有实现的功能不得部署或称可用。

## 交付与验收

产物是公共契约、基础指标实现、`A-handoff.md`及样本对账结论；A本身不增加客户聊天入口。H/B接入后对照同一项目的合同、目标成本、资金和利润页面验证。基础数据缺失要能与真实0区分。下一步B/C/D/G可并行。
