# B 产值与付款分析交付说明

日期2026-11-20，依赖analysis-v1。A交接与实际公共接口已核对；B实现已在当前checkout，待H接入与用户人工验收。

## 文件所有权及交付

Boot新增文件（根目录smart-boot）：
- smart-model/src/main/java/com/smart/model/business/analysis/operation/ProjectOperationQueryRequestVO.java。
- smart-service/src/main/java/com/smart/service/analysis/operation/ProjectOperationQueryService.java。
- smart-business/smart-project/src/main/java/com/smart/project/analysis/operation/ProjectOutputPaymentAnalysisServiceImpl.java。
- 同包ProjectOutputPaymentAnalysisDao.java、ProjectOperationMapperConfiguration.java。
- smart-business/smart-project/src/main/resources/mapper/ProjectOutputPaymentAnalysisDao.xml。
- smart-ai/src/main/java/com/smart/ai/controller/AiProjectOperationToolController.java、AiProjectOperationToolExceptionHandler.java。

Agent新增文件（根目录agent）：
- src/main/java/com/smart/agent/tool/operation/ProjectOperationQueryInput.java。
- 同包ProjectOperationQueryTool.java、ProjectOperationBusinessClient.java、SmartBootProjectOperationBusinessClient.java、ProjectOperationBusinessClientConfiguration.java。

未改A、C、D、E、F、G文件，也未修改公共路由、提示词、上下文、鉴权映射、profile、pom或数据库。未切分支、提交、部署、启动服务。Mapper使用根目录mapper，B配置显式MapperScan。

## 固定接口

Tool `projectOperation.query`，POST `/internal/ai/tools/project-operation-query`，响应Boot ProjectQueryResultVO、Agent ProjectQueryResult。

实现公共服务ProjectOutputPaymentAnalysisService.load(AnalysisScopeVO, AnalysisPeriodVO)，供E/F直接使用。另实现B私有ProjectOperationQueryService.query(scope,request)，用于请求筛选、渲染及分页。

HTTP字段：projectIds（可选集合）、projectId/projectCode/projectName、organizationId、projectNature、managerId、firstPartyId、mode、dateMode、dateField/startDate/endDate、page/pageSize。可信tenant、权限、开关从签名上下文构造，不从客户输入绑定。

Agent输入不含projectIds数组，默认当前可信全部项目；单项目ID/编号/名称先精确解析，重名返回澄清。组织/性质/经理/甲方仅支持真实ID/真实字典值，不含名称定位工具，不能猜ID。HTTP项目集合只与可信集合求交集，空集合不是全量。

mode：detail（默认项目列表）、summary（全量合计，固定page1/size1）、evidence（来源单据分页）。每页1至100，默认20；汇总率从全量金额重算后才分页。

甲方条件用于按有效施工合同选择项目，不将其他收入当甲方回款。不支持金额筛选DSL、回款率排序及未收余额大于0筛选，H不能声明已支持。

## 指标与权限

| 指标 | 实现规则 |
| --- | --- |
| finalContractAmount | A的有效一对一施工合同最终额 |
| completedOutputAmount | 仅审批完成产值单本期金额SUM，不SUM累计值 |
| reportedOutputAmount、confirmedOutputAmount | 与完成产值同值，内部及汇总响应保留别名 |
| paymentReceivedAmount | A的审批完成回款登记金额 |
| otherIncomeAmount | A的审批完成其他收入，单列 |
| contractReceiptRate | 回款登记/最终合同额×100 |
| outputReceiptRate | 回款登记/完成产值×100 |
| unreceivedAmount | 完成产值−回款登记，允许负数 |

金额CNY，比例PERCENT且已经乘100，保留4位小数。客户表格默认一个完成产值列，避免三个同值重复；用户明确提问上报/确认时说明同一值，不虚构甲方外部确认，不展示恒为0申报差额。

继承A状态；每个指标在rows有`Status`与`Reason`后缀。没有金融流水为明确0；没有有效合同、源单金额缺失、无来源权限不能当0。比率分母0返回null及原因。任何项目必要来源缺失时该指标全量汇总null并说明不完整，不用部分项目金额冒充总额。

menu:project覆盖原项目/产值/施工合同范围；回款额需menu:finance:payment-registration，其他收入需menu:finance:other-income-registration。缺其他收入权限不影响已授权回款比率。权限开关来自可信上下文，关闭时依旧保留租户、可信项目、审批与删除过滤。

项目/来源仅审批完成，项目状态排除1/3/10。B产值来源最多10000，取10001检测超限，拒绝截断统计。项目/来源金额先独立汇总，不多对多连接重复计算。

## 日期和单据边界

CURRENT默认且完整支持；CURRENT携带日期条件报400，避免静默忽略。

PERIOD格式要求dateField及起止日；AS_OF要求dateField与结束日且不带开始日。合法非当前请求所有金额返回UNSUPPORTED/null，不查询当前产值或金额代替历史。appliedFilters含dateSupported=false与dateNotice，无匹配项目也须说明限制，不能解释成该期间为0。

产值没有核实到独立施工发生日期，证据日期仅登记时间；回款证据到账日期由A提供。日期合法不代表该日期模式已实现。

evidence返回A合同/回款/其他收入来源与B产值来源的真实标识、编号、类型和日期，产值businessType=PROJECT_OUTPUT。它是定位清单而非完整业务表单；H需要将真实单据ID交原业务详情工具并重新验权。不能把证据列表描述成已开发全部下钻表单。

## 请求响应示例

Agent请求：
```json
{"projectCode":"004","mode":"detail","dateMode":"CURRENT","page":1,"pageSize":20}
```

HTTP请求需签名，并使用实际有权项目ID：
```json
{"projectId":"实际项目ID","mode":"summary","dateMode":"CURRENT"}
```

下例仅为契约与公式示例，并非已调用UAT接口返回；省略部分列与状态：
```json
{"mode":"detail","page":1,"pageSize":20,"total":1,"hasNext":false,
 "columns":[{"key":"projectName","label":"项目名称","type":"string"},
 {"key":"completedOutputAmount","label":"完成产值（同上报甲方产值）（元）","type":"number"},
 {"key":"contractReceiptRate","label":"合同回款比例（%）","type":"number"},
 {"key":"outputReceiptRate","label":"产值回款比例（%）","type":"number"}],
 "rows":[{"projectId":"实际项目ID","projectName":"示例项目",
 "finalContractAmount":10000000,"completedOutputAmount":2500000,
 "reportedOutputAmount":2500000,"confirmedOutputAmount":2500000,
 "paymentReceivedAmount":1000000,"otherIncomeAmount":200000,
 "contractReceiptRate":10.0000,"outputReceiptRate":40.0000,"unreceivedAmount":1500000,
 "completedOutputAmountStatus":"OK","outputReceiptRateStatus":"OK"}]}
```

## H接入清单

1. PROJECT_OPERATION唯一域注册projectOperation.query；E交付后同域加compare，B不创建Contributor。
2. 产值＋回款/付款比例组合进入本域，原产值单/回款单/项目普通查询保持原路径。
3. 每轮实时查询，恢复真实项目ID、已确认筛选和日期。非CURRENT先说明未支持，不自动替换为CURRENT或项目创建日期。
4. 无名称定位能力时不可猜组织/经理/甲方ID；无权/缺金额原因按业务中文展示，技术字段不直接给客户。
5. 单据证据由原工具继续详情定位，不宣称本批已实现所有单据完整表单。
6. 检查新内部URL签名拦截器覆盖、bean注册、模型工具允许列表与澄清恢复。B不修改这些公共接入点。
7. 工具schema静态解析通过，仅type/description/properties/items/additionalProperties，没有不支持的enum/minItems/maxItems。

## 编译与验收

Agent命令：JAVA_HOME=F:/java/jdk_21/jdk-21.0.6，mvn -o -q -DskipTests test-compile；日志analysis-B-agent-compile.log，退出0。

Boot最终编译通过，退出0：JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile`；日志analysis-B-boot-compile.log。早先共享target缺既有基础类失败，清理构建产物后重编并取得最终成功结果。没有因此修改公共pom或原业务源码。

B Mapper XML与工具schema静态解析通过；未执行SQL/模型运行验证、自动测试或UAT数据库修改，不启动服务。

H接入后重启Boot与Agent；Web/数据库不变。问题：“004项目完成多少产值、上报多少、甲方回款多少？”“合同回款比例和产值回款比例分别多少？”“改查254101”“汇总所有可见项目”“看来源单据”“只看今年”。核对审批中/未通过不算、其他收入不进回款率、缺权限/缺金额不补0、期间未支持不拿当前累计代替。

下一步H接入B并由用户UAT验收；E/F确认当前checkout含B实现后可复用公共load。
