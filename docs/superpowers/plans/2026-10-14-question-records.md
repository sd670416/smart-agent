# 问题记录实施计划

> **For agentic workers:** Use superpowers:executing-plans or superpowers:subagent-driven-development. Preserve current branches and user changes; do not commit automatically.

**Goal:** 按已确认设计实现问题记录、反馈和管理页面，不增加跟进流程。
**Architecture:** Agent 持久化问题快照并校验租户/归属，boot 按现有可信上下文转发并校验查看权限，web 展示列表详情和消息反馈。
**Tech Stack:** Java 21 / Spring Boot / JPA / Flyway；业务网关 Java 8；Vue 3 / Ant Design Vue。
**Spec:** `agent/docs/superpowers/specs/2026-10-14-question-records-design.md`

## Global Constraints

- 不修改业务查询权限，不限制 system，不自动授予角色权限，不切换现有分支，不提交。
- 只记录不跟进。无数据、权限不足、取消、澄清不自动收集；最终失败才收集。
- tenantId 来自可信上下文；手动反馈只允许本人的真实消息与运行关联。
- 同 run 记录幂等，自动/模型/手动来源可补充，收集失败不使聊天失败。
- 未支持标记使用结构化工具，不做回答关键词扫描或额外评价模型请求。

## 接口协议

- `GET /agent/question-records/page`，网关同路径前缀 `/ai`。参数 `keyword,userId,domain,source,startDate,endDate,page,size`；page从0，size默认20，最大100；日期yyyy-MM-dd，结束日包含当天，按Asia/Shanghai解释。
- `GET /agent/question-records/{id}`，列表和详情必须持有 `aiQuestion:menu`。
- `POST /agent/question-records/feedback`，body `{conversationId,runId,messageId}`；从服务端读取问题/答案，不能接收客户端自报内容，登录用户可报告自己的消息。
- 列表 `{items,total,page,size}`；项 `{id,userId,conversationId,runId,traceId,question,domain,sources,reasonCode,reason,createdAt}`。
- 详情在列表项基础增加 `answer,context,steps`；context项 `{role,content}`，steps项 `{type,input,output}`。长度和数量有界且脱敏。
- source允许 `SYSTEM,MODEL,USER`；domain允许为空，其他业务域代码沿用现有路由。

## Task 1: Agent 收集及管理接口

文件范围：`agent/src/main/java/com/smart/agent/question/`、Flyway `V15__create_question_records.sql`、必要的 chat/run 接入与测试。

- [x] 分类、归属、租户及安全快照测试；快照脱敏和模型schema有真实失败后修复证据。MySQL幂等测试已编写，实际运行待数据库验证。
- [x] 独立组件持久化问题记录与快照，菜单权限双端校验，反馈验证真实运行/消息关联。
- [x] 聊天最终失败接入自动收集；结构化未支持标记接入允许工具列表；正常回答不收集。
- [x] 定向测试、全量单测和只读审查；详见同目录question-core-report。

## Task 2: 网关、菜单及前端

文件范围：boot `smart-ai` controller/authorizer/context；web `api/ai`、`views/ai/question-record` 和现有聊天操作区；`smart-boot/db/ai-question-record-menu.sql`。

- [x] 网关先写权限及参数转发测试，再实现协议端点；只有查看接口需菜单权限。16项测试通过。
- [x] 菜单脚本可重复执行；已在localhost:3306/gongcheng执行两次并验证，没有修改角色授权。
- [x] 列表、筛选、分页、详情及加载/空/错误状态；消息“未解决”操作可重试并避免重复提交。
- [x] 新增前端定向7项通过；构建通过。最终构建及结果见下方。真实浏览器/联调按用户要求由用户启动验收。

## Task 3: 联合验收与记录

- [x] 审查跨层协议、权限隔离、无额外跟进功能；修复历史反馈conversationId缺失、403误成功、单引号多行敏感值脱敏问题。
- [x] 补录验证结果、菜单登记情况、重启和测试话术。

## 进度

- 2026-10-14：用户已确认设计及开发；实施中。使用现有多仓库检出，不自动创建分支或提交，以便用户本地连续测试。
- 2026-10-14：代码开发完成，用户要求不本地启动项目，由用户启动联调；临时前端进程已停止。没有自动运行Agent或Boot。

## 本地菜单

- 菜单：问题记录，`/ai/question-record`，ID `2102000000000000101`，父AI菜单 `2100074915838468097`。
- 查看权限：`aiQuestion:menu`，按钮ID `2102000000000000111`。
- 注册脚本：`smart-boot/db/ai-question-record-menu.sql`。只登记，不授权，不删除既有记录；其他环境须在对应业务库执行脚本。

## 用户启动与测试

1. 重启smart-agent与smart-boot，更新smart-web前端。Agent正常启动时由Flyway执行V15；不要手动重复执行V15。
2. 在原菜单权限页面为测试角色分配“问题记录”查看菜单，重新登录。无该菜单账号不可读管理列表/详情，但可反馈自己的消息。
3. 进入智能助手，完成一轮正常回答，点击回复旁“未解决”，应提示已记录；重复点击或刷新后再反馈，同run仅一条记录。
4. 到AI→问题记录，按问题关键词、用户ID、业务模块、来源或日期筛选；打开详情检查问题、答案、上下文和各ID。
5. 可控地制造一次最终查询/模型失败，应新增运行异常记录。正常空数据、无权限、取消、等待澄清不自动记录。
6. 模型调用结构化未支持能力工具并结束回复时，记录能力未支持来源；仅自然语言写“不支持”不作为可靠自动判断。现有实时查询工具约束不变。
7. 历史消息仅在有真实run/message关联时显示反馈按钮；升级前没有关联ID的旧消息不猜测匹配。

## 验证边界

- Agent全量359项单测通过，0失败/0错误；网关定向16项通过；前端新增定向测试7项通过。
- 最终 `pnpm exec vite build --mode localhost` exit 0，4m31s；存在既有大包与动态/静态混用导入警告，不影响构建通过。
- MySQL持久化集成测试因无Docker跳过；V15尚未在实际Agent数据库迁移。真实页面与跨服务联调由用户启动后验证。
- 完整历史消息旧测试存在元数据对象深比较失败，未借本功能修改其既有断言；新增真实消息映射及消息投影回归已通过。
- 收集采用独立事务，失败不让聊天失败；快照只保存有限且脱敏的内容，不复制完整模型请求和原始debug数据。
