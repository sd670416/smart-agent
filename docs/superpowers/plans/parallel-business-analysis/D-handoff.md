# D 盈利排名交接

日期2026-11-20。依赖契约analysis-v1。Owner D。状态：代码完成，Boot与Agent主代码及测试源码编译均通过，待H接入及人工验收。

## A依赖核对

已读取A-handoff并检查当前checkout内公共VO、AnalysisMetricCatalog、ProjectAnalysisMetricService及smart-project实现存在。无需切分支或额外合并即可在当前目录编译。未修改A公共契约、A计算、旧利润普通查询及比较行为。

## D文件

Boot新增：
- smart-model/src/main/java/com/smart/model/business/analysis/ranking/ProjectProfitRankingRequestVO.java。
- smart-service/src/main/java/com/smart/service/analysis/ranking/ProjectProfitRankingQueryService.java。
- smart-business/smart-project/src/main/java/com/smart/project/analysis/ranking/ProjectProfitRankingService.java。
- smart-ai/src/main/java/com/smart/ai/controller/AiProjectProfitRankingToolController.java。
- smart-ai/src/main/java/com/smart/ai/controller/AiProjectProfitRankingToolExceptionHandler.java（仅限定本Controller，避免影响其他业务）。

Agent新增src/main/java/com/smart/agent/tool/profit/ranking/下五个文件：ProjectProfitRankingInput、ProjectProfitRankingTool、ProjectProfitRankingBusinessClient、SmartBootProjectProfitRankingBusinessClient、ProjectProfitRankingBusinessClientConfiguration。

未修改路由、PROJECT_PROFIT域Descriptor、ChatOrchestrator、系统提示词、权限工厂、配置、数据库或Web。其他聊天产生的文件未回退或覆盖。

## 接口

Tool：projectProfit.rank。POST /internal/ai/tools/project-profit-ranking。

最小请求：`{}`，默认budgetProfitRate/desc/topN20/page1/pageSize20/CURRENT。

完整请求样例：

```json
{"metric":"actualProfit","direction":"asc","topN":50,"page":1,"pageSize":10,"lossOnly":true,"projectNature":"自营","dateMode":"CURRENT"}
```

项目条件：projectId、projectCode、projectName、organizationId、projectNature、projectType、managerId。均精确筛选，条件AND组合；性质/类型支持字典值或中文标签，输出转换字典中文名称；经理/组织仅真实ID，不接受名称当ID。Agent单项目code/name借现有ProjectReferenceResolver定位，重名返回PROJECT_REFERENCE澄清。后端从可信签名上下文构造scope，客户端不能提交tenant、权限或projectIds扩大范围。

metric允许budgetProfitRate、budgetProfit、actualProfitRate、actualProfit。direction仅asc/desc；topN、pageSize为1至100，page正整数。lossOnly按对应利润金额小于0过滤。未指定lossOnly为false。

只支持CURRENT。其他dateMode及任何dateField/startDate/endDate返回400业务说明，不忽略日期或换成创建日期；日期模式不是仅在提示词中限制。

响应使用ProjectQueryResultVO/Agent ProjectQueryResult。结构示意（示例数值，不是本批UAT实测）：

```json
{
  "mode":"detail","page":1,"pageSize":20,"total":2,"hasNext":false,
  "rows":[
    {"rank":1,"projectId":"example-b","projectName":"B项目","finalContractAmount":5000000,"targetCostAmount":3500000,"budgetProfit":1500000,"budgetProfitRate":30,"rankStatus":"可排名","dataNotes":{}},
    {"rank":2,"projectId":"example-a","projectName":"A项目","finalContractAmount":10000000,"targetCostAmount":8000000,"budgetProfit":2000000,"budgetProfitRate":20,"rankStatus":"可排名","dataNotes":{}}
  ],
  "columns":[{"key":"rank","label":"排名","type":"number"}],
  "appliedFilters":[{"metric":"budgetProfitRate","metricLabel":"预算利润率","direction":"desc","topN":20,"dateMode":"CURRENT","matchingProjectCount":3,"rankableProjectCount":2,"notRankableCount":1,"notRankableReasons":{"未找到审批完成的有效来源记录，暂不能计算":1},"eligibleProjectCount":2,"rankedProjectCount":2,"rankingDescription":"按预算利润率从高到低，最多展示20个项目"}]
}
```

实际columns包含全部业务列；上例columns仅缩写结构，不是准确完整返回体。项目ID为技术定位列，H应告诉模型不在客户表格展示。projectNature/type为字典标签；dataNotes保存辅助列缺失原因。

## 排名与完整性

- 复用A来源：预算需合同/有效目标成本，实际收支需回款/其他收入/付款/其他付款/报销/退款；预算榜不要求实际来源菜单。
- 后端用AnalysisMetricCatalog.permissions(metric)验证整个选定指标权限；权限关闭状态来自可信上下文，沿用A跳过菜单校验但保留租户与可信范围。
- source按A返回的可信全量项目计算；核对返回项目属于scope，拒绝重复ID和异常null响应，不把不完整数据冒充全量。
- 选定排名指标不可计算的项目排除，原因按中文分组计数。实际利润金额可用而收入为0时仍可排金额，利润率则不可排名。真实0预算可参与，缺预算不能补0。
- 金额按BigDecimal排序；利润率使用利润与分母交叉相乘比较，避免A四位展示率造成错误排序/并列，负分母保留正确方向。
- 排序后竞赛排名，同值同名次且跳号，例如1、1、3；再按项目编号、ID稳定显示。同值比较用真实指标精度，不先按客户显示位数舍入。
- 先按完整候选项目计算，过滤lossOnly，再排序排名、截取topN、分页。TopN为行数上限，并列不扩展；翻页保留名次。
- total为TopN后的有效榜单条数。全范围匹配、可排名、不可排名、亏损筛选后数量在appliedFilters，不能把一页行数或TopN当全项目数量。
- summary元信息放appliedFilters，因既有ProjectQueryResultVO无独立summary字段，不修改公共响应契约。未另造summary模式。
- 每轮工具执行重新加载A，无跨轮金额缓存。A单次上限规则仍有效。

## H接入清单

1. 给现有ProjectProfitDomainContributor的toolKeys追加projectProfit.rank，不新建重复PROJECT_PROFIT域。
2. 明确“排名/最高/最低/前N”在利润上下文可用rank；“查询项目列表按合同额排序”继续project.query，不误跳利润。用户单独说排名沿当前域判断，歧义先追问。
3. SystemInstructionCatalog说明默认预算利润率降序前20，并强制引用rankingDescription告知用户当前指标；从appliedFilters读取不可排名数量/原因。
4. 保持metric、direction、topN、pageSize、lossOnly与项目条件的持久化；“下一页”page+1，“按利润金额”依据既定预算/实际上下文切换相应metric，不由默认值重置用户意图。当前无明确预算/实际且要求实际则使用实际，不混用公式。
5. projectNature/type中文由本服务字典转换；managerId、organizationId仅可信真实ID，需经理/组织名称解析时由公共目录定位或追问，不把姓名填ID。
6. 当前工具requiredPermission=null，最终权限由Boot按metric及可信开关检查；H的域可用性过滤仍需兼容权限总开关，不在这里重新硬编码角色。
7. 确认新内部路径纳入既有签名过滤器处理，不能绕过签名。Agent签名canonical与已有工具一致。
8. schema仅type/description/properties/items/required/additionalProperties等支持关键字，本工具未使用enum/minItems/maxItems。仍需真实模型链路人工验证。
9. 400/422映射AGENT_QUERY_VALUE_INVALID并保留限定本Controller的业务说明；403显示利润排名数据权限不足，不说无权查看该项目。

## 编译与验证

本批按用户要求只编译，不运行自动测试，不启动服务，不操作UAT数据库。

Agent：JAVA_HOME=F:/java/jdk_21/jdk-21.0.6，`mvn -o -q -DskipTests test-compile`，日志profit-ranking-D-compile.log。

Boot：JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile`，同名日志在Boot目录。

两条命令均退出0，主代码及测试源码编译通过。未执行测试、未启动Spring、未运行真实模型工具调用。代码完成不代表已接入/人工验收通过。

## 重启与人工验证

待H接入后重启smart-boot与smart-agent，Web/数据库无需变更。D单独交付时公共路由尚未接入，不能保证自然语言已可命中rank。

1. “做个盈利排名”：应说明预算利润率降序前20个。
2. “改按预算利润金额排序”：金额榜与率榜可不同。
3. “实际收支利润最低的50个项目，每页10条”→“下一页”：保留asc、metric、topN、size；下一页名次不中断。
4. “只看亏损项目”：按对应利润负数筛选，不能丢负值。
5. “006项目预算利润率”：按A已知样本应因缺有效预算/合同而不可排名，不算100%。
6. 缺预算权限账号查询预算榜：明确权限；仅预算权限查询预算榜不要求资金菜单。
7. “按去年利润排名”：说明当前仅累计或追问，不能把去年创建的项目拿来当去年利润。
8. 同名项目先确认，切换权限后追问重新检查范围；数据不能仅取历史结果。

下一步H接入D并由用户在UAT验证；D不负责更改其他业务入口或人工验收结论。
