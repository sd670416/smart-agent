# B 产值与付款分析交接

日期2026-11-20，公共契约analysis-v1，Owner B。

## 状态

| 项目 | 进度 |
| --- | --- |
| A交接与实际公共接口/实现 | 已核对，可复用 |
| B产值与回款分析服务、内部HTTP接口 | 已实现 |
| Agent projectOperation.query输入、工具、签名Client、配置 | 已实现 |
| 项目列表、全量汇总、两种回款比例 | 已实现 |
| 来源单据定位清单 | 已实现；不代表完整表单详情 |
| 主代码与测试源码编译 | Boot和Agent均退出0；未执行测试 |
| 公共路由、上下文、提示词接入 | 待H处理 |
| 期间发生额、历史截至日金额 | 暂不支持，返回明确UNSUPPORTED而非当前金额 |
| 真实模型、账号权限、UAT页面对账 | 待H接入后由用户人工验收 |

完整文件清单、请求/响应样例、权限与日期矩阵、服务契约、H接入清单、编译命令和人工测试问题见[B交付说明](B-delivery.md)。

## 固定契约

- 实现A的ProjectOutputPaymentAnalysisService.load(AnalysisScopeVO, AnalysisPeriodVO)，返回List<ProjectAnalysisSnapshotVO>。
- 新增ProjectOperationQueryService.query(scope, request)，返回既有ProjectQueryResultVO。
- Tool：projectOperation.query。
- POST：/internal/ai/tools/project-operation-query。
- 业务域：PROJECT_OPERATION，由H唯一注册；E后续compare与B同域。
- completedOutputAmount、reportedOutputAmount、confirmedOutputAmount取同一审批完成本期产值合计。
- 甲方回款仅回款登记，其他收入单列；contractReceiptRate和outputReceiptRate分别除最终合同额、确认产值，已乘100。
- summary全量合计后重算率，detail最后分页；缺关键来源不补0，无权来源不泄漏证据。

## 验证及后续

Agent：analysis-B-agent-compile.log，`mvn -o -q -DskipTests test-compile`退出0。

Boot：analysis-B-boot-compile.log，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile`退出0。B Mapper XML与工具schema静态解析通过，不等于SQL/模型运行验证。

不启动服务、不修改数据库、运行配置、分支或公共接入源码。未覆盖其他聊天新增文件。

H接入后重启Boot和Agent，Web/数据库无需更新。测试：“004项目完成多少产值、上报多少、甲方回款多少？”“两种回款比例分别多少？”“改查254101”“汇总所有可见项目”“查看来源单据”。日期追问应明确当前仅累计。

下一步H接入B，E/F可复用B公共load；人工验收由用户确认，不将编译通过标记成验收通过。
