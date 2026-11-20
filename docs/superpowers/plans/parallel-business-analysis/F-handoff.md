# F 资金与成本风险交接

日期2026-11-20，公共契约analysis-v1，Owner F。

## 进度

| 工作 | 状态 |
| --- | --- |
| 核对A/B/C接口及已存在G工期实现 | 完成，复用当前checkout实现 |
| 五维FinancialProjectRiskProvider | 已实现 |
| 一级科目有效预算与净支出事实 | 已实现，复用有效预算XML片段 |
| 默认规则、配置解析、版本、边界校验 | 已实现，只读配置，无数据库写入 |
| 风险/未触发/未评估分开返回 | 已实现，已知命中与未评估可同时存在 |
| 主代码及测试源码编译 | 首次及补充工期证据后的最终编译均退出0 |
| 真实Spring注入、SQL/UAT页面、模型调用 | 未运行，待人工验收 |
| 公共工具文案与金融维度路由 | H接入，F未修改公共文件 |

没有切分支、提交、合并、启动应用、写数据库或修改profile/pom。保留其他聊天修改。

## 实际文件及模块

源码包：`smart-boot/smart-business/smart-project/src/main/java/com/smart/project/analysis/risk/financial/`。

- FinancialProjectRiskProvider.java：实现既有ProjectRiskProvider，编排事实并判断五维风险。
- FinancialRiskAssessment.java：收集业务事实、阈值、证据、风险与未评估结果，去重证据。
- FinancialRiskRules.java：版本与默认阈值；只读sys_config，配置错误明确失败。
- FinancialProjectRiskDao.java：配置、科目预算、科目支出只读方法。
- FinancialRiskMapperConfiguration.java：仅注册F包的@Mapper接口。
- XML：`smart-business/smart-project/src/main/resources/mapper/FinancialProjectRiskDao.xml`。放既有根扫描目录，不放未被扫描的mapper/analysis目录。

未创建HTTP接口、Agent工具、页面或公共类型；调用既有A/B/C/G服务契约。

## 服务契约与依赖

```java
Set<String> dimensions(); // COST, PAYMENT, RECEIVABLE, INVOICE, SUPPLIER_INVOICE
List<AnalysisRiskVO> evaluate(AnalysisScopeVO scope, AnalysisPeriodVO period);
```

构造依赖ProjectAnalysisMetricService(A)、ProjectOutputPaymentAnalysisService(B)、PartnerAccountAnalysisService(C)、ProjectSiteAnalysisService(G)、F Dao及FinancialRiskRules。当前G已在checkout，只有回款比例低于高风险阈值且存在未收余额的项目才加载G的工期事实，不增加公共契约。

来源输入必须是内部验签后的可信scope；F复制租户/项目/权限/权限开关，不修改传入集合。先用A收窄真实有效项目，然后下游来源只在这些项目内查询。空项目集合返回空；源项目越界、同项目指标重复或同合同账款重复会明确拒绝，不用部分/重复数据评估。

A/B/C/G各自延续租户、删除、审批完成和项目状态过滤；F新增SQL同样限定tenant、审批完成和未删除；项目报销按明细project_id归属。普通金额不读取审批中或未通过业务单。

只支持CURRENT。合法非当前period先由A校验格式，然后五维返回NOT_EVALUATED，不用当前数字重建历史。CURRENT不能夹带日期条件，继承A校验。

## 规则与真实计算

| 维度 | 规则 | 计算与边界 |
| --- | --- | --- |
| COST | FIN_COST_科目编码_等级 | 科目累计净支出>有效预算高；未超且≥90%中；≥75%且<90%低 |
| PAYMENT | FIN_PAYMENT_UNPAID | 相同可见供应合同范围未付净额/结算净额：≥50%高，≥30%且<50%中，>0且<30%低 |
| RECEIVABLE | FIN_RECEIVABLE_LOW_AFTER_HALF | 确认产值有未收余额、回款比例<60%且时间进度>50%为高 |
| RECEIVABLE | FIN_RECEIVABLE_RATIO | 有未收余额，回款比例≥60%且<85%中，≥85%低 |
| INVOICE | FIN_INVOICE_UNRECEIVED | 审批完成开票申请减甲方回款的正差额/最终合同额≥30%高 |
| INVOICE | FIN_INVOICE_COVERAGE | 开票申请/确认产值<80%中，≥80%且<95%低，高风险优先 |
| SUPPLIER_INVOICE | FIN_SUPPLIER_INVOICE_UNCOVERED | 分合同计算max(净付款−净收票,0)，再按项目合计，>0高；不同供应合同的多收票不能覆盖彼此缺口 |
| SUPPLIER_INVOICE | FIN_SUPPLIER_INVOICE_COVERAGE | 有效净收票/有效结算净额<80%中，≥80%且<95%低，高风险优先 |

比例判断对金额使用交叉乘法，避免先四舍五入后跨阈值；展示比例保留4位。工期时间比例使用G真实返回值。项目总体等级由H现有总览取最高，科目风险逐条保留证据。

一级科目映射与现预算页面一致：1材料、2劳务、3机械、4专包、5其他。预算来自BudgetCostAiDao.projectRows的已审批编制与有效变更；付款=审批完成付款申请明细(contract_type)、其他付款明细(payment_type)、项目报销明细(reimburse_type)，材料退款type=2且purchase_type=1从材料支出减一次。缺金额、未知科目、没有有效预算、预算负值不按0计算。

供应商结算/付款/收票直接使用C的supplierSettlementAmount、supplierPaidAmount、supplierUnpaidAmount、supplierInvoiceAmount；这些已经做材料退款净额处理，F不再减退款。缺少任一合同类型权限的C占位状态会让项目完整金额评估变为未评估，不把有权限部分冒充全部。有效结算分母≤0不以合同额替代。

甲方回款仅B的paymentReceivedAmount，其他收入不进入回款与开票差额。产值使用B的confirmedOutputAmount，与用户确认的完成/上报统计规则一致。

## 明确的未评估条件

- 没有结构化的可靠应收到期节点，不实现“已到期且超过90天无回款”的判断，也不从合同付款条款自由文本猜日期。因此有未收余额时，已有回款比例规则可以命中，同时另返回FIN_RECEIVABLE_INCOMPLETE说明长期未回款规则未评估。没有用项目创建日替代。
- 确认产值≤0不能算产值回款/开票覆盖；最终合同额≤0不能算申请未回款占合同额；结算净额≤0不能算结算未付/收票覆盖。这些条件明确未评估，不给虚假的0比例。
- 付款风险只称未付压力，不认定到期拖欠。开票只称申请与回款差额，不断言实际已开票或税损。无票支出按汇总缺口提示，不断言逐笔缺票或税务违规。
- 不接审批中付款停滞15天信号，避免将审批中业务混进用户要求的完成金额；该信号需以后按独立审批权限接入。
- 数据完整且未命中返回EVALUATED/NONE；缺来源/权限返回NOT_EVALUATED且level=null；已确定命中的独立规则不因其他规则缺资料而丢弃。

## 来源权限

成本完整评估需要menu:project、menu:budget:cost、menu:finance:payment-request、menu:finance:other-payment-request、menu:finance:project-reimburse、menu:material:refund。缺任一来源不执行F科目SQL，不算部分成本风险。

回款依赖B的项目、产值与回款登记来源及G工期权限；开票另依赖A的menu:finance:invoice-application。供应商依赖C的四合同、相应结算、付款、收票及材料退款权限，遵循C原始状态。权限开关关闭时跳过菜单判断，但保留可信租户、项目、审批和删除边界。

没有新增权限key，H已接A/C/G的真实来源菜单映射可复用，仍需人工账号验证。

## 配置

只读sys_config：config_key=`ai_project_financial_risk_rules`，按tenant_id隔离，最多读取2条检测重复。没有该key时使用FIN-20261120-v1默认；存在但空值/非法JSON/未知字段/版本非法/阈值顺序错误会明确失败，不降级无风险。未创建或修改该配置行。

完整配置样例（文档示例，未写库）：

```json
{
  "version": "FIN-20261120-v1",
  "costMediumPercent": 90,
  "costLowPercent": 75,
  "unpaidHighPercent": 50,
  "unpaidMediumPercent": 30,
  "receiptHighPercent": 60,
  "receiptMediumPercent": 85,
  "receiptTimeProgressPercent": 50,
  "receiptSilenceDays": 90,
  "invoiceUnreceivedHighPercent": 30,
  "invoiceMediumPercent": 80,
  "invoiceLowPercent": 95,
  "supplierInvoiceMediumPercent": 80,
  "supplierInvoiceLowPercent": 95
}
```

version必填，其他字段缺省沿用默认。百分比>0且≤100，低中高相关阈值严格递增；receiptSilenceDays正整数且≤3650，目前只记录长期条件所需阈值，尚无到期来源不能据此宣称该条件已评估。超预算与付款收票正缺口是金额差>0的固定事实定义。

## 响应示例与证据

Provider返回已有AnalysisRiskVO。以下为规则结构示例，不声称运行应用取到此记录：

```json
{
  "projectId": "真实项目ID",
  "dimension": "COST",
  "ruleCode": "FIN_COST_1_HIGH",
  "ruleVersion": "FIN-20261120-v1",
  "level": "HIGH",
  "evaluationStatus": "EVALUATED",
  "reason": "材料费累计净支出超过有效预算",
  "facts": {
    "subjectBudgetAmount": {"label":"材料费有效预算","value":1000000,"unit":"CNY","status":"OK"},
    "subjectPaidAmount": {"label":"材料费累计净支出","value":1050000,"unit":"CNY","status":"OK"}
  },
  "evidence": [{"businessType":"PAYMENT_REQUEST","recordId":"实际来源主单ID","recordNo":"实际单据编号"}]
}
```

事实与阈值放facts，证据按businessType+recordId去重，仅包含本维度已授权来源。成本保留编制/变更主单及对应科目付款主单；回款高风险补充G计划/反馈依据。F不会打开完整表单或执行业务写操作。

## 给H的接入说明

1. 当前H的ProjectRiskQueryServiceImpl已经注入List<ProjectRiskProvider>并按dimensions过滤，F作为@Component会被自动发现；不需要复制五维公式或再建Controller。
2. `ProjectRiskQueryTool.description()`仍写“当前已接入进度、质量、安全”，请H更新为八维已接入，同时保留缺数据/到期条件未评估说明。F未越权修改此文件。
3. 提问“成本/回款/付款/开票/收票风险”应路由projectRisk.query并传对应dimensions；普通付款单、开票申请或利润查询保持原域，不能被风险词过度匹配。
4. 返回有风险行及NOT_EVALUATED时两者同时说明，特别是回款长期未回款条件不能假装已支持。只看HIGH后的项目计数不能说明全项目已全面评估。
5. 列表/证据用已有真实单据ID重新验权下钻，不将ruleCode、来源表/字段输出业务答复；用户能看到中文数值、阈值和原因即可。

## 验证与人工测试

Boot日志：`smart-boot/financial-risk-F-compile.log`。

```powershell
$env:JAVA_HOME='F:/java/jdk/1.8'
mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile
```

初次及补充工期证据后的最终编译均退出0；F所有类和Mapper已复制到target/classes。XML结构解析通过。按用户约定未运行自动测试、未启动Spring、未执行F新增SQL对UAT取数、未调用模型；编译通过不表示已完成运行或业务验收。

本批仅修改Boot。已安装H工具时重启smart-boot即可加载Provider；H更新Agent工具描述或路由后，另需重启smart-agent。Web、数据库无需更新。

人工问题：
1. “哪些项目有成本风险？”预算100万、同科目净支出105万/95万/80万分别高/中/低；无预算未评估，退款只减一次。
2. “哪些项目有付款风险？”有效结算100万、未付50万/30万/20万分别高/中/低；无结算不能拿合同额做分母。
3. “A项目为什么有回款风险？”确认产值250万、回款100万=40%，工期>50%才命中低回款高风险；缺工期不猜测，缺到期节点明确长期条件未评估。
4. “只看开票风险。”“查看收票风险依据。”前者用审批完成申请金额；后者核对供应商合同净额，退款不重复调整，跨合同多收票不抵其他合同缺口。
5. “只看高风险。”“汇总所有可见项目。”全量评估再筛选分页，风险项目数去重，未知不当无风险。
6. 换少来源权限账号或收窄可见项目后重问，应重新按可信范围查询；权限总开关关闭仍不得跨租户或返回不在可信范围的项目。

下一步H接入五维文案/路由，用户在UAT验收；若运行发现真实字段或来源异常，修复F专属实现，不覆盖A/B/C/G或公共路由。
