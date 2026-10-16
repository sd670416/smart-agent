# 工程产值独立查询实施报告

## 当前状态

2026-10-16 最新状态：**待验收**，用户决定先继续后续任务。以下为开发及自动化验证记录，不代表人工验收完成。

实现与最终回归已完成：Agent全量391项通过；后端工程产值11项及原施工合同7项通过。独立只读审查由主任务继续完成。

沿用用户确认的当前检出，不切分支、不提交，不启动服务，不连接或写入数据库，不修改smart-web。既有问题记录及其他脏改动保留。

## 实现

- 独立工具projectOutput.query / projectOutput.clarifyDate、PROJECT_OUTPUT域、内部接口/internal/ai/tools/project-output-query。
- menu:project与签名可信可见项目交集；空范围阻断查询；单据及项目未删除、审批完成2，排除项目状态1/3/10。
- 明细保留本期工程量、本期完成产值、累计完成产值；summary只COUNT及SUM(current_period_output_value)，不合计累计值；空范围summary返回BigDecimal.ZERO。
- 仅projectCreateDate/createDate，含起止日、次日零点排他上界；日期不明时可点击澄清并恢复原问题；相对日期先取system.current_time。
- 项目精确定位、重名确认、原参数恢复、canonical项目ID快照、当前项目指代、每轮实时查询，复用既有Resolver和公共编排小扩展。
- 单独产值且没有工程产值上下文时文字追问工程/劳务/专包；劳务/专包查询明确提示尚未接入；显式流程查询优先审批域。无新假工具。
- 系统说明和工具描述要求说明“累计结算金额”来自工程产值本期金额合计，不能混成合同最终结算；本次无比较或按产值单ID下钻。
- 新接口单独403/400异常映射，包含日期解析错误；不改变原项目/合同接口异常策略。

## 本次文件

以下路径相对F:/project/gongcheng。

Agent新增：

- agent/src/main/java/com/smart/agent/routing/ProjectOutputDomainContributor.java
- agent/src/main/java/com/smart/agent/tool/output/ProjectOutputBusinessClient.java
- agent/src/main/java/com/smart/agent/tool/output/ProjectOutputBusinessClientConfiguration.java
- agent/src/main/java/com/smart/agent/tool/output/ProjectOutputClarifyDateTool.java
- agent/src/main/java/com/smart/agent/tool/output/ProjectOutputQueryInput.java
- agent/src/main/java/com/smart/agent/tool/output/ProjectOutputQueryTool.java
- agent/src/main/java/com/smart/agent/tool/output/SmartBootProjectOutputBusinessClient.java
- agent/src/test/java/com/smart/agent/chat/ProjectOutputChatTest.java
- agent/src/test/java/com/smart/agent/routing/ProjectOutputRoutingTest.java
- agent/src/test/java/com/smart/agent/tool/output/ProjectOutputQueryInputTest.java
- agent/src/test/java/com/smart/agent/tool/output/SmartBootProjectOutputBusinessClientTest.java

Agent修改：

- agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java
- agent/src/main/java/com/smart/agent/model/SystemInstructionCatalog.java
- agent/src/main/java/com/smart/agent/routing/QueryIntentResolver.java
- agent/src/main/java/com/smart/agent/tool/ToolExecutor.java
- agent/src/main/java/com/smart/agent/question/QuestionRecordService.java（既有未提交文件，本次仅新增projectOutput工具prefix到PROJECT_OUTPUT推断）
- agent/src/test/java/com/smart/agent/model/ModelGatewayContractTest.java（保留原问题记录相关测试修改）
- agent/src/test/java/com/smart/agent/tool/ToolExecutorTest.java

smart-boot新增：

- smart-boot/smart-model/src/main/java/com/smart/model/business/pm/ProjectOutputQueryRequestVO.java
- smart-boot/smart-service/src/main/java/com/smart/service/pm/ProjectOutputQueryService.java
- smart-boot/smart-business/smart-pm/src/main/java/com/smart/pm/service/ProjectOutputAiQueryService.java
- smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiProjectOutputToolController.java
- smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiProjectOutputToolExceptionHandler.java
- smart-boot/smart-business/smart-pm/src/test/java/com/smart/pm/service/ProjectOutputAiQueryServiceTest.java
- smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiProjectOutputToolControllerTest.java

smart-boot修改：

- smart-boot/smart-business/smart-pm/src/main/java/com/smart/pm/dao/BusPmOutputReportDao.java
- smart-boot/smart-business/smart-pm/src/main/resources/mapper/BusPmOutputReportDao.xml

文档：本报告；计划文件由主任务维护。除上述交叠位置外，本次未修改既有问题记录功能文件、配置、数据库迁移、ChatConfiguration、ConversationQueryController、AgentRun/AgentRunService或PersistenceMySqlIT。

## RED与GREEN证据

所有Maven命令严格串行。

1. Agent cwd，mvn -o '-Dtest=ProjectOutputRoutingTest' test：RED，1 test/1 failure/0 errors；原“经营看板的累计结算金额”expected PROJECT_OUTPUT but was BOARD。该测试在独立域尚不存在时以反射可选加载，完成后已简化为普通构造。
2. Agent cwd，mvn -o '-Dtest=ProjectOutput*Test,SmartBootProjectOutputBusinessClientTest' test：新增完整输入/客户端/chat测试先行，缺少ProjectOutput类编译失败，随后才新增生产文件。
3. smart-boot cwd，mvn -o -pl smart-ai,smart-business/smart-pm -am '-Dtest=ProjectOutputAiQueryServiceTest,AiProjectOutputToolControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test：新增后端测试先行，缺少ProjectOutputQueryRequestVO/ProjectOutputQueryService编译失败，随后才新增后端生产文件。
4. Agent工具初步实现后定向执行：已观察项目选择恢复失败（Clarification selection is invalid），修复公共编排接入；该轮因测试model故意抛AssertionError导致Flux不终止而手动中断，未作为完整通过证据。新类型阻断测试改为抛可终止的IllegalStateException。
5. Agent cwd，mvn -o '-Dtest=ProjectOutput*Test,SmartBootProjectOutputBusinessClientTest,ModelGatewayContractTest,ConstructionContractChatTest,QueryIntentResolverTest' test：GREEN，89 tests/0 failures/0 errors。覆盖工程产值chat19项、原施工合同chat17项、provider schema30项及路由/输入/签名客户端。之后新增workflow/追问边界，最终全量已通过。
6. “劳务产值待办”回归先行，mvn -o '-Dtest=ProjectOutputRoutingTest' test：RED，2 tests/1 error（无APPROVAL候选）；随后将工程产值类型阻断限定为非显式workflow，并补chat三种产值待办回归。最终全量已通过。
7. 新接口HTTP403/400测试先行，后端定向命令：RED，缺少AiProjectOutputToolExceptionHandler编译失败；随后实现新接口专用advice。含新旧四类测试的GREEN命令已通过。

最终后端命令（2026-10-14 16:10完成，BUILD SUCCESS）：

```powershell
mvn -o -pl smart-ai,smart-business/smart-pm -am '-Dtest=ProjectOutputAiQueryServiceTest,AiProjectOutputToolControllerTest,ConstructionContractAiQueryServiceTest,AiConstructionContractToolControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

后端实际结果：AiProjectOutputToolControllerTest 6项，ProjectOutputAiQueryServiceTest 5项，AiConstructionContractToolControllerTest 4项，ConstructionContractAiQueryServiceTest 3项；合计18项、0 failures、0 errors。覆盖可信范围交集、无菜单/无签名上下文拒绝、HTTP403/400、空范围精确零、金额不损失精度、明细分页与清理、审批/删除/项目状态条件及日期上界。

最终Agent全量命令（2026-10-14 16:14完成，BUILD SUCCESS）：

```powershell
mvn -o test
```

Agent实际结果：391 tests、0 failures、0 errors、0 skipped；baseline359项，本次新增32项。工程产值chat最终21项，routing4项、input4项、签名client2项，新测试类合计31项；provider增加1项，ToolExecutor在原测试内增加断言。

Agent和smart-boot均执行git diff --check，退出码0，无空白错误；仅已有LF/CRLF提示。

说明：日志中存在仓库原有unchecked/deprecated编译提示、负向测试预期异常堆栈和boot启动脚本“Access is denied.”首行，均未导致上述命令失败。没有启动业务应用或实际数据库。

## 真实未验证项

- 未启动Agent/smart-boot/smart-web，未执行真实模型、浏览器人工操作或部署验收。
- 未查询任何实际数据库；SQL金额来源、审批/删除/范围/日期条件与分页由离线服务测试和代码审查验证，真实数据库SUM结果、租户拦截器执行、执行计划与性能仍需运行环境验证。
- 签名请求与精确金额反序列化通过本地随机端口HTTP fixture验证，未连接真实smart-boot网关。
- 日期澄清、项目重名确认、追问恢复使用离线model fixture，不代表真实大模型完全遵循说明；正式环境需按计划验收话术人工复测。
- 离线测试不替代正式环境验收；主任务继续完成独立只读审查。
