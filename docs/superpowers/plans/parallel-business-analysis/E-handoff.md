# E 多项目经营横向比较交接

日期2026-11-20，Owner E。依赖契约analysis-v1。

## 状态

| 项目 | 状态 |
| --- | --- |
| A/B/G交接及实际服务接口/实现 | 已核对，当前checkout已具备 |
| 多项目比较服务及内部签名HTTP接口 | 已实现 |
| Agent比较输入、工具、签名Client及配置 | 已实现 |
| 中文项目属性、指标矩阵、基准差额、百分点 | 已实现 |
| Agent主代码及测试源码编译 | 退出0，未执行自动测试 |
| Boot主代码及测试源码编译，覆盖smart-project实现与smart-ai | 退出0，未执行自动测试 |
| H公共域、路由、模型提示和多项目追问接入 | 待H处理，本任务未修改公共文件 |
| UAT真实模型、签名端到端与业务人工验收 | 待H接入后由用户验证 |

未启动服务、查询或修改数据库；未改profile、pom、分支、A/B/G实现及公共接入代码。目录内其他任务的既有改动保留。未提交或合并。

## 实际文件

Boot：

- `smart-model/src/main/java/com/smart/model/business/analysis/comparison/ProjectOperationCompareRequestVO.java`。
- `smart-service/src/main/java/com/smart/service/analysis/comparison/ProjectOperationCompareService.java`。
- `smart-business/smart-project/src/main/java/com/smart/project/analysis/comparison/ProjectOperationComparisonService.java`。
- `smart-ai/src/main/java/com/smart/ai/controller/AiProjectOperationCompareToolController.java`。
- `smart-ai/src/main/java/com/smart/ai/controller/AiProjectOperationCompareToolExceptionHandler.java`。

Agent包 `src/main/java/com/smart/agent/tool/operationcompare/`：

- ProjectOperationCompareInput。
- ProjectOperationCompareTool。
- ProjectOperationCompareBusinessClient。
- SmartBootProjectOperationCompareBusinessClient。
- ProjectOperationCompareBusinessClientConfiguration。

不新建重复PROJECT_OPERATION域Contributor，继续由H将比较工具并入B的域。

## 固定接口

Tool：`projectOperation.compare`；POST `/internal/ai/tools/project-operation-compare`。

Boot服务：

```java
ProjectQueryResultVO ProjectOperationCompareService.compare(
    AnalysisScopeVO scope, ProjectOperationCompareRequestVO request);
```

Controller从AiTrustedUserContextHolder构造可信租户、可见项目集合、权限及权限开关；用户请求没有tenantId或可信scope。服务严格逐项检查项目ID在可信范围内，拒绝越权或重复，不静默丢弃项目。A进一步验证项目审批完成、非关闭/立项中/立项未通过。请求中附带编号或名称时，必须与重新读取的真实项目一致。

Agent工具使用现有ProjectReferenceResolver逐个按ID/精确编号/精确名称定位，保留用户顺序，同名返回ClarificationPrompt而不选择第一条。项目个数解析前后均限制2至5个。签名HTTP Client再次检查真实ID属于可信项目集合，并按既有HMAC头和路径签名。

基准默认第一个已确认项目，可通过baselineProjectId选择列表中其他项目。其值必须是真实ID，不能填名称或序号；H需把“以B为基准”解析为列表中已确认的真实ID。

### 请求示例

以下项目ID/数值都是说明契约的示例，不是E已调用UAT运行得到的响应。

2项目：

```json
{
  "projects":[{"projectId":"project-a"},{"projectId":"project-b"}],
  "metricKeys":["finalContractAmount","targetCostAmount","budgetProfitRate","elapsedConstructionDays"],
  "dateMode":"CURRENT"
}
```

3项目，以B为基准：

```json
{
  "projects":[{"projectId":"project-a"},{"projectId":"project-b"},{"projectId":"project-c"}],
  "metricKeys":["finalContractAmount","budgetProfitRate","contractReceiptRate"],
  "baselineProjectId":"project-b",
  "dateMode":"CURRENT"
}
```

默认metricKeys：项目类型/性质/经理、最终合同额、有效目标成本、预算利润/率、实际收支利润/率、成本率、甲方回款、两种回款比例、合同工期、计划工期、已施工天数、完工实际工期。可选择1至30项，未知key拒绝。

额外支持A全部16项基础指标、B的completedOutputAmount/reportedOutputAmount/confirmedOutputAmount/unreceivedAmount，以及G的progressPercent/actualStartDate/actualEndDate。

日期第一版CURRENT。带PERIOD/AS_OF或任何日期条件明确拒绝并提示暂不支持，不能以CURRENT自动替换；不支持同比/历史快照。没有对A/B/G尚不支持的能力编造实现。

## 响应结构与显示规则

返回既有ProjectQueryResultVO / Agent ProjectQueryResult，mode=aggregate、page=1、hasNext=false，rows是指标行，不是项目列表。total是指标行数，不是项目数量；项目数量应取appliedFilters[0].projects.size。

columns：metricLabel（比较指标）、unit（单位）、differenceUnit（差额单位）、p1至p5（项目名称及编号），非基准项目另有pNDifference（相对基准差额）。每个项目列可能含数值、日期或无法计算原因，因此column.type=string；JSON数值仍保持BigDecimal，不能把列类型理解为要求模型重算金额。

2项目响应节选示意：

```json
{
  "mode":"aggregate","page":1,"pageSize":2,"total":2,"hasNext":false,
  "columns":[
    {"key":"metricLabel","label":"比较指标","type":"string"},
    {"key":"unit","label":"单位","type":"string"},
    {"key":"differenceUnit","label":"差额单位","type":"string"},
    {"key":"p1","label":"A项目（A001）","type":"string"},
    {"key":"p2","label":"B项目（B001）","type":"string"},
    {"key":"p2Difference","label":"B项目（B001）相对基准差额","type":"string"}
  ],
  "rows":[
    {"metricLabel":"施工合同最终金额","unit":"元","differenceUnit":"元","p1":10000000,"p2":5000000,"p2Difference":-5000000},
    {"metricLabel":"预算利润率","unit":"百分比","differenceUnit":"百分点","p1":20,"p2":30,"p2Difference":10}
  ],
  "appliedFilters":[{"baselineProjectId":"project-a","baselineProjectName":"A项目","dateMode":"CURRENT",
    "projects":[{"column":"p1","projectId":"project-a","projectCode":"A001","projectName":"A项目"},
                {"column":"p2","projectId":"project-b","projectCode":"B001","projectName":"B项目"}]}]
}
```

3项目以B为基准的行示意：

```json
{"metricLabel":"预算利润率","unit":"百分比","differenceUnit":"百分点",
 "p1":20,"p1Difference":-10,"p2":30,
 "p3":"未获得目标成本来源查看权限","p3Status":"NOT_AUTHORIZED",
 "p3Reason":"未获得目标成本来源查看权限","p3Difference":null}
```

实际响应还含每个数值格的pNStatus/pNReason，以及appliedFilters内完整项目映射、metricKeys、asOf、sourceAsOf及差额方向说明。以上仅为形状示例，数值不得当作实际查询结果。

金额差额=当前项目−基准项目；比例值已乘100，差额是百分点，不计算增长率或倍数。有缺失/无权限/不适用时在该格返回具体原因，相对差额为空，不补0。真实金融无记录但A明确提供0可作为有效0计算。缺少合同/预算时不能算100%利润率。

项目类型/性质使用DictService按bus_project_type/bus_project_nature转中文；字典缺项显示业务名称缺失，不输出编码冒充名称。未完工只显示已施工天数，完工实际工期按G状态；实际完工日期额外要求G的completedProject确认完成。

只在请求包含对应指标时调用B/G；单查预算指标不会无条件调用现场风险或要求全部资金权限。A自身按每项来源权限返回状态；整个项目菜单无权仍拒绝。查询权限总开关沿用可信scope，不接受外部false绕过。

比较服务为只读REPEATABLE_READ事务。各来源保留自己的asOf并在sourceAsOf列出，appliedFilters.asOf表示本次结果生成时刻；不宣称跨数据库历史快照或完全相同的来源事件时间。

## H需要接入的项目

1. ProjectOperationDomainContributor目前只列projectOperation.query；追加projectOperation.compare，不能另建相同CODE。业务名称可调整为项目经营分析而不仅产值回款。
2. QueryIntentResolver识别显式“比较A/B经营”“横向对比”等跨指标请求到PROJECT_OPERATION；原普通利润两组比较、产值单比较保持原入口，不以“比较”二字全局抢路由。
3. ChatOrchestrator当前特殊实时/上下文分支仅列projectOperation.query（rg该key可定位）。补充compare的有效参数和结果保存、每轮实时调用、错误主题“项目经营比较”。
4. 多项目上下文保存有序projects和baselineProjectId，来源为本轮经工具确认的真实ID。响应appliedFilters.projects可用作映射，但rows是指标，不能把“第2条指标”当第2个项目；“第二个项目”按有序projects解释。
5. 增加/删除项目以及更换基准后传完整projects重查；删除基准时追问或按用户已明确要求选择新基准，不悄悄用剩余任意项目。
6. 同名澄清：工具使用PROJECT_REFERENCE与project:<id>，提示文字含“第N个比较项目”。H必须保留原始完整输入和待确认项目下标，仅替换该元素，然后重新执行；不能使用B单项目的root projectId处理覆盖整组。尚未完成此公共链路前，同名选择与增删追问不标已验收。
7. 默认基准首项目必须告知用户；展示矩阵按columns标签，差额注明方向，率差标百分点；不暴露key/表名。当前无日期支持，应说明并追问是否改查当前累计，不自动改条件。
8. 新内部路径沿用/internal/ai/tools签名规则；H检查公共白名单/权限映射是否覆盖此路径，并检查Tool注册与schema的实际模型适配。工具schema仅用当前支持关键字，没有enum/minItems/maxItems。

本任务没有直接修改H的公共文件，也没有通过跨聊天消息派任务；H可读取本交接执行统一接入。

## 编译记录

Agent，JAVA_HOME=F:/java/jdk_21/jdk-21.0.6：

```text
mvn -o -q -DskipTests test-compile
```

退出0，日志agent/project-comparison-E-agent-compile.log。

Boot，JAVA_HOME=F:/java/jdk/1.8：

```text
mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile
```

退出0，日志smart-boot/project-comparison-E-boot-compile.log。均编译主代码及测试源码，没有执行测试、启动Spring、调用模型或UAT接口，不能据此宣称业务验收通过。

## 人工验证与下一步

H接入后重启smart-boot和smart-agent，Web与数据库无需更新。

1. “比较004和254101项目的合同额、目标成本、预算利润率、甲方回款和工期”：检查调用projectOperation.compare，顺序不变，默认004为基准并明确说明。
2. “再加XM260130001，以254101为基准”：H保留三项目真实ID，所有差额按各项目减254101，率差是百分点。
3. “去掉004”：保留正确剩余项目和原有效基准，重新调用；不足2个时要求补项目而非做假比较。
4. 比较有缺预算/合同的项目，相关格显示原因，差额空；不能把成本缺失当0算100%利润。
5. 少财务来源权限账号只选预算指标，已授权部分正常；无权格不泄露原始金额或补0。
6. 同名项目先确认准确编号，验证只替换目标元素；无权项目/重复项目/基准不在列表均拒绝。
7. “比较去年经营”应明确当前不支持期间/历史比较；“改查我的待办”回原审批域，不能被比较域锁定。

下一步由H接入E公共链路，再交用户UAT人工验证。E代码完成与编译通过不等于H接入完成或业务验收通过。
