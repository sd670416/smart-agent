# 项目档案聚合查询工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一次调用即可安全返回当前用户可见项目档案全部分区与明细的 `project.getArchiveDetail` 只读工具。

**Architecture:** `smart-boot` 从登录请求生成带项目范围和分区菜单权限的签名可信上下文；`smart-agent` 验证后保留这些权限，并用 HMAC 签名调用 `smart-boot` 聚合接口。聚合服务在恢复的可信用户上下文中逐分区调用原业务服务，使菜单权限和 MyBatis 数据权限同时生效。

**Tech Stack:** Java 8、Spring Boot、MyBatis/MyBatis-Plus、Java 21、Spring WebFlux/WebClient、Vue 3、Vite、JUnit 5、Mockito。

**Spec:** `docs/superpowers/specs/2026-09-12-project-archive-detail-tool-design.md`

## Global Constraints

- `smart-boot` 必须保持 Java 8 语法，不使用 record、switch 表达式、`List.of`、`Map.of` 或文本块。
- `smart-agent` 使用 Java 21。
- 不修改现有 `project.getOverview`、`project.query` 和项目档案页面接口的行为。
- 内部业务数据不得发送到公网检索服务。
- 业务字段可见，租户、删除、版本、内部用户 ID 等系统字段不得返回模型。
- 查询只复用菜单权限与数据维度；按钮权限继续仅控制增删改审批。
- 每完成一个任务先运行任务级测试并向用户汇报，再进入下一任务。

---

### Task 1: 保留项目档案分区权限

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/security/ProjectArchivePermissionResolver.java`
- Modify: `../smart-boot/smart-ai/src/main/java/com/smart/ai/security/AgentContextFactory.java`
- Modify: `src/main/java/com/smart/agent/tool/ToolContext.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/security/ProjectArchivePermissionResolverTest.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/security/AgentContextFactoryTest.java`
- Test: `src/test/java/com/smart/agent/tool/ToolContextTest.java`

**Interfaces:**
- Produces: `Set<String> ProjectArchivePermissionResolver.resolve(List<MenuEntity> menus)`。
- Produces: `ToolContext(String tenantId, String userId, String identityId, Set<String> roleIds, Set<String> projectIds, Set<String> permissions)`。
- Permission keys: `menu:project:base`, `member`, `contract`, `finance`, `scene`, `cost`, `subcontract`, `bid`, `deposit`, `flow`，统一使用 `menu:project:<section>` 格式。

- [ ] **Step 1: 写失败测试，证明菜单只能映射对应分区**

```java
@Test
public void resolvesOnlyVisibleArchiveSections() {
    List<MenuEntity> menus = Arrays.asList(menu("项目档案", "/archive/bus-project"),
            menu("资金情况", "/finance/payment-registration"));
    Set<String> permissions = resolver.resolve(menus);
    assertTrue(permissions.contains("menu:project:base"));
    assertTrue(permissions.contains("menu:project:finance"));
    assertFalse(permissions.contains("menu:project:cost"));
}
```

- [ ] **Step 2: 运行测试并确认因解析器不存在而失败**

Run: `mvn -pl smart-ai -am -Dtest=ProjectArchivePermissionResolverTest,AgentContextFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 实现集中式菜单映射，并把结果写入签名上下文**

```java
public Set<String> resolve(List<MenuEntity> menus) {
    Set<String> result = new LinkedHashSet<String>();
    for (MenuEntity menu : menus == null ? Collections.<MenuEntity>emptyList() : menus) {
        addMatchedPermissions(result, normalized(menu));
    }
    return Collections.unmodifiableSet(result);
}
```

`AuthenticatedAgentContextFactory` 将解析结果加入现有 `permissions`；`ToolContext.from(AgentUserContext)` 必须复制全部权限，不能再丢弃。

- [ ] **Step 4: 运行两端权限测试**

Run in `smart-boot`: `mvn -pl smart-ai -am -Dtest=ProjectArchivePermissionResolverTest,AgentContextFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run in `agent`: `mvn -Dtest=ToolContextTest,ToolExecutorTest test`

- [ ] **Step 5: 提交任务 1**

```bash
git commit -m "feat: preserve project archive section permissions"
```

### Task 2: 建立可信内部用户上下文

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiTrustedUserContext.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiTrustedUserContextHolder.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiTrustedUserContextInterceptor.java`
- Create: `../smart-boot/smart-mybatis/src/main/java/com/smart/mybatis/context/TrustedDataScopeContext.java`
- Create: `../smart-boot/smart-mybatis/src/main/java/com/smart/mybatis/context/TrustedDataScopeContextHolder.java`
- Modify: `../smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiInternalWebConfig.java`
- Modify: `../smart-boot/smart-mybatis/src/main/java/com/smart/mybatis/handler/DataScopeHandler.java`
- Modify: `src/main/java/com/smart/agent/tool/project/SmartBootProjectBusinessClient.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/internal/AiTrustedUserContextInterceptorTest.java`
- Test: `../smart-boot/smart-mybatis/src/test/java/com/smart/mybatis/handler/DataScopeHandlerTrustedContextTest.java`
- Test: `src/test/java/com/smart/agent/tool/project/SmartBootProjectBusinessClientTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ToolContext.permissions()`。
- Produces: 请求头 `X-Agent-Tenant-Id`、`X-Agent-User-Id`、`X-Agent-Identity-Id`、`X-Agent-Role-Ids`、`X-Agent-Permissions`。
- Produces: `AiTrustedUserContextHolder.require()`，请求完成后必须清除 ThreadLocal。
- Produces: `TrustedDataScopeContextHolder.runWith(context, menuId, supplier)`，为一段业务服务调用提供用户、部门、组织、角色、超级管理员标记和菜单 ID。

- [ ] **Step 1: 写失败测试，覆盖签名校验、上下文恢复和请求后清理**

```java
@Test
public void restoresAndClearsSignedUserContext() throws Exception {
    assertTrue(interceptor.preHandle(requestWithValidSignature(), response, handler));
    assertEquals("user-1", AiTrustedUserContextHolder.require().getUserId());
    interceptor.afterCompletion(request, response, handler, null);
    assertFalse(AiTrustedUserContextHolder.current().isPresent());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl smart-ai -am -Dtest=AiTrustedUserContextInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 将身份头纳入 HMAC canonical string**

```text
timestamp\nmethod\npath\ntenantId\nuserId\nidentityId\ncanonicalRoleIds\ncanonicalPermissions
```

角色和权限分别排序后用逗号连接。拦截器拒绝缺失身份头、过期时间戳、签名不一致和未知权限格式；随后用 `identityId/userId/roleIds` 从 `smart-boot` 数据库加载权威部门、组织、角色和超级管理员属性，不信任客户端提供这些属性。`finally/afterCompletion` 清理两个上下文。

`DataScopeHandler` 优先读取 `TrustedDataScopeContextHolder`，不存在时保持当前 `AuthUtil` 行为。聚合 loader 调用原服务时必须使用该分区对应的真实菜单 ID：

```java
return TrustedDataScopeContextHolder.runWith(context.toDataScope(), menuId,
        new Supplier<ProjectArchiveSection>() {
            @Override public ProjectArchiveSection get() { return loadVisibleRows(project); }
        });
```

- [ ] **Step 4: 运行内部调用契约测试**

Run in `smart-boot`: `mvn -pl smart-ai -am -Dtest=AiTrustedUserContextInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run in `smart-boot`: `mvn -pl smart-mybatis -am -Dtest=DataScopeHandlerTrustedContextTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run in `agent`: `mvn -Dtest=SmartBootProjectBusinessClientTest test`

- [ ] **Step 5: 提交任务 2**

```bash
git commit -m "feat: restore trusted user context for ai tools"
```

### Task 3: 定义聚合 DTO 与分区编排器

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ProjectArchiveDetail.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ProjectArchiveSection.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ProjectArchiveSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ProjectArchiveDetailService.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/project/archive/ProjectArchiveDetailServiceTest.java`

**Interfaces:**
- Produces: `ProjectArchiveDetail load(String projectId)`。
- Produces: `ProjectArchiveSection load(BusProjectEntity project, AiTrustedUserContext context)`。
- Status values: `AVAILABLE`, `EMPTY`, `FAILED`。

- [ ] **Step 1: 写失败测试，覆盖权限过滤与分区故障隔离**

```java
@Test
public void omitsDeniedSectionAndKeepsSuccessfulSectionWhenAnotherFails() {
    when(base.supports(context)).thenReturn(true);
    when(base.load(project, context)).thenReturn(available("base", oneItem()));
    when(finance.supports(context)).thenReturn(false);
    when(cost.supports(context)).thenReturn(true);
    when(cost.load(project, context)).thenThrow(new RuntimeException("database error"));
    ProjectArchiveDetail result = service.load("project-1");
    assertEquals(Arrays.asList("base", "cost"), keys(result.getSections()));
    assertEquals("FAILED", result.getSections().get(1).getStatus());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl smart-ai -am -Dtest=ProjectArchiveDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 实现固定 DTO、按权限加载、异常隔离和分区耗时日志**

`ProjectArchiveDetailService` 必须先验证项目在可信 `projectIds` 中，再顺序调用 loader；无权限 loader 直接跳过，不产生空分区和数量。每个 loader 从 `ProjectArchivePermissionResolver` 获取该角色对应的真实菜单 ID，并在 `TrustedDataScopeContextHolder.runWith` 内调用业务服务。

- [ ] **Step 4: 运行聚合骨架测试并提交**

Run: `mvn -pl smart-ai -am -Dtest=ProjectArchiveDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

```bash
git commit -m "feat: add permission-aware project archive aggregator"
```

### Task 4: 接入基础信息、成员、合同与资金

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/BaseProjectSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ProjectMemberSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/ContractSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/FinanceSectionLoader.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/project/archive/CoreProjectSectionLoaderTest.java`

**Interfaces:**
- Consumes: `BusProjectService.get(projectId)`、项目档案现有合同与资金服务。
- Produces: `base`、`member`、`contract`、`finance` 四个分区。

- [ ] **Step 1: 写失败测试，使用与项目档案页面相同的服务返回值，断言中文字典和完整记录**

```java
assertEquals("联营", baseItems.get(0).get("项目性质"));
assertEquals("天津市 / 河西区", baseItems.get(0).get("办公地址"));
assertEquals(2, contractSection.getItems().size());
assertFalse(contractSection.getItems().get(0).containsKey("tenantId"));
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl smart-ai -am -Dtest=CoreProjectSectionLoaderTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 实现四个 loader**

基础信息固定输出业务字段中文键；成员只输出姓名、岗位、部门和项目角色；合同与资金调用档案页面当前使用的 archive 服务方法，并将所有记录映射到白名单 DTO。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -pl smart-ai -am -Dtest=CoreProjectSectionLoaderTest,ProjectArchiveDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

```bash
git commit -m "feat: aggregate project core archive sections"
```

### Task 5: 接入施工、成本、分包、招投标与保证金

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/SceneSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/CostSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/SubcontractSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/BidSectionLoader.java`
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/DepositSectionLoader.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/project/archive/BusinessProjectSectionLoaderTest.java`

**Interfaces:**
- Produces: `scene`、`cost`、`subcontract`、`bid`、`deposit` 五个分区。

- [ ] **Step 1: 写失败测试，逐分区断言完整条数、中文值及系统字段剔除**

```java
assertEquals(3, scene.load(project, context).getItems().size());
assertEquals("审批通过", bid.load(project, context).getItems().get(0).get("审批状态"));
assertFalse(deposit.load(project, context).getItems().get(0).containsKey("isDeleted"));
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl smart-ai -am -Dtest=BusinessProjectSectionLoaderTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 实现五个 loader 并复用原档案服务**

所有查询带目标 `projectId`，不自行关闭数据权限；返回集合不按 5、20 等页面大小截断。金额使用 `BigDecimal`，时间使用原业务时间值，空值统一保留为空供模型显示“—”。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -pl smart-ai -am -Dtest=BusinessProjectSectionLoaderTest,ProjectArchiveDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

```bash
git commit -m "feat: aggregate project business archive sections"
```

### Task 6: 接入审批流程与内部聚合接口

**Files:**
- Create: `../smart-boot/smart-ai/src/main/java/com/smart/ai/project/archive/FlowSectionLoader.java`
- Modify: `../smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiProjectToolController.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/project/archive/FlowSectionLoaderTest.java`
- Test: `../smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiProjectArchiveToolControllerTest.java`

**Interfaces:**
- Produces: `POST /internal/ai/tools/project-archive-detail`。
- Request: `{ "projectId": "..." }`。
- Response: Task 3 的 `ProjectArchiveDetail`。

- [ ] **Step 1: 写失败测试，断言流程字段和无权限项目被拒绝**

```java
assertEquals(Arrays.asList("节点名称", "处理人", "状态", "开始时间", "完成时间", "审批意见"),
        new ArrayList<String>(flowItem.keySet()));
assertThrows(SecurityException.class, () -> service.load("denied-project"));
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl smart-ai -am -Dtest=FlowSectionLoaderTest,AiProjectArchiveToolControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: 实现流程 loader、Controller 路由和固定错误响应**

项目不存在返回“未找到可访问的项目”；分区异常保留 `FAILED`；Controller 不返回堆栈、类名或 SQL。

- [ ] **Step 4: 运行 smart-ai 测试和 Java 8 编译**

Run: `mvn -pl smart-ai -am -Dtest=FlowSectionLoaderTest,AiProjectArchiveToolControllerTest,ProjectArchiveDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run: `mvn -pl smart-ai -am -DskipTests package`

- [ ] **Step 5: 提交任务 6**

```bash
git commit -m "feat: expose complete project archive detail"
```

### Task 7: 新增 smart-agent 工具与容量保护

**Files:**
- Create: `src/main/java/com/smart/agent/tool/project/ProjectArchiveDetailInput.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectArchiveDetailResult.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectArchiveDetailTool.java`
- Modify: `src/main/java/com/smart/agent/tool/project/ProjectBusinessClient.java`
- Modify: `src/main/java/com/smart/agent/tool/project/SmartBootProjectBusinessClient.java`
- Modify: `src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`
- Test: `src/test/java/com/smart/agent/tool/project/ProjectArchiveDetailToolTest.java`
- Test: `src/test/java/com/smart/agent/tool/project/SmartBootProjectBusinessClientTest.java`
- Test: `src/test/java/com/smart/agent/model/SystemInstructionCatalogTest.java`

**Interfaces:**
- Produces: 工具键 `project.getArchiveDetail`。
- Produces: `ProjectArchiveDetailResult ProjectBusinessClient.getArchiveDetail(ToolContext, String projectId)`。

- [ ] **Step 1: 写失败测试，覆盖标识解析、权限、中文提示与超限**

```java
ProjectArchiveDetailResult result = tool.execute(
        new ProjectArchiveDetailInput(null, "A001", null), context);
verify(client).getArchiveDetail(context, "project-1");
assertThat(tool.description()).contains("完整项目档案");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=ProjectArchiveDetailToolTest,SmartBootProjectBusinessClientTest,SystemInstructionCatalogTest test`

- [ ] **Step 3: 实现工具、客户端协议、模型使用规则和响应体上限**

模型提示明确：“项目详情/完整档案”使用新工具；成功后直接分区回答，不重复调用。超过配置的安全响应字节数时抛出 `AGENT_PROJECT_ARCHIVE_TOO_LARGE`，用户文本为“该项目档案数据量较大，请指定要查看的部分”。

- [ ] **Step 4: 运行 agent 回归测试和打包**

Run: `mvn -Dtest=ProjectArchiveDetailToolTest,SmartBootProjectBusinessClientTest,SystemInstructionCatalogTest,ToolExecutorTest test`

Run: `mvn -DskipTests package`

- [ ] **Step 5: 提交任务 7**

```bash
git commit -m "feat: add complete project archive detail tool"
```

### Task 8: 端到端权限与聊天展示验证

**Files:**
- Modify: `src/test/java/com/smart/agent/chat/ChatControllerIT.java`
- Modify: `../smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiProjectArchiveToolControllerTest.java`
- Modify: `README.md`
- Modify: `../smart-boot/README.md`

**Interfaces:**
- Consumes: Tasks 1-7 的完整链路。
- Produces: 可重复执行的验收证据和部署说明。

- [ ] **Step 1: 写端到端失败测试**

模拟“查看 A001 项目的完整详情”，断言只有一次 `project.getArchiveDetail` 工具调用，最终中文消息包含允许分区，不包含被拒绝分区、系统字段或原始字典编码。

- [ ] **Step 2: 运行端到端测试确认失败；若失败点是工具未注册，则仅在 ToolRegistry 配置中注册 `ProjectArchiveDetailTool`，若失败点是模型未选择工具，则仅补充 SystemInstructionCatalog 的详情路由规则**

Run in `agent`: `mvn -Dtest=ChatControllerIT#streamsCompleteProjectArchiveWithAllowedSections test`

- [ ] **Step 3: 执行完整权限矩阵测试**

Run in `smart-boot`: `mvn -pl smart-ai -am -Dtest=ProjectArchivePermissionResolverTest,AiTrustedUserContextInterceptorTest,ProjectArchiveDetailServiceTest,CoreProjectSectionLoaderTest,BusinessProjectSectionLoaderTest,FlowSectionLoaderTest,AiProjectArchiveToolControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run in `agent`: `mvn -Dtest=ProjectArchiveDetailToolTest,SmartBootProjectBusinessClientTest,SystemInstructionCatalogTest,ChatControllerIT#streamsCompleteProjectArchiveWithAllowedSections test`

- [ ] **Step 4: 执行两端生产构建**

Run in `smart-boot`: `mvn -DskipTests package`

Run in `agent`: `mvn -DskipTests package`

- [ ] **Step 5: 手工验收三个角色**

使用超级管理员、完整项目档案角色、仅基础信息角色分别询问同一项目完整详情；对比 `/archive/bus-project` 页面可见页签和明细，确认聊天结果不扩大权限。

- [ ] **Step 6: 更新文档进度并提交**

将设计文档阶段 2-8 更新为“已完成”，记录测试命令和结果。

```bash
git commit -m "test: verify project archive detail permissions"
```
