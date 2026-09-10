# 项目自然语言语义查询实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加 `project.query` 通用只读查询能力，让模型按项目业务字段自由组合筛选、排序、分组和聚合，同时严格继承现有菜单权限、可信项目范围及 MyBatis 权限链路。

**Architecture:** `smart-agent` 将自然语言转换为结构化 DSL，并通过工具 Schema 与本地校验阻止未知参数；`smart-boot` 的 `smart-ai` 端点接收带可信项目范围的请求，调用原 `BusProjectService` 和 `BusProjectDao` 在数据库侧执行。字段名、操作符、排序和聚合均由服务端注册表映射，模型不能提交 SQL 或数据库列名。

**Tech Stack:** Java 21 / Spring Boot 3 / LangChain4j / Jackson（smart-agent）；Java 8 / Spring Boot 2.7 / MyBatis-Plus / PageHelper / JUnit 4（smart-boot）。

**Spec:** `docs/superpowers/specs/2026-09-10-project-semantic-query-design.md`

## Global Constraints

- 第一版只实现项目资源，不扩展合同、材料、财务资源。
- smart-boot 必须通过 Java 8 编译，不使用 record、模式匹配、`List.of`、文本块或新版 switch 表达式。
- 只开放项目业务字段；租户、删除标记、流程实例、内部关联 ID、成员 ID、预留字段等系统字段不开放。
- 权限只复用菜单、按钮和数据维度，不新增字段级权限。
- 模型不得提交 SQL、数据库表名、数据库列名、tenantId、userId 或 projectIds。
- 默认 20 条，单次最多 100 条，分组最多 50 组；工具结果上限保持 64KB。
- 保留 `project.listAccessible`、`project.getOverview`、`project.getContracts` 的现有行为。
- 每个任务完成专项测试后暂停，交由用户验证再进入下一任务。

---

### Task 1: 扩展 Agent 嵌套工具 Schema

**Files:**
- Modify: `agent/src/main/java/com/smart/agent/model/OpenAiCompatibleModelGateway.java`
- Modify: `agent/src/test/java/com/smart/agent/model/ModelGatewayContractTest.java`

**Interfaces:**
- Consumes: `ModelRequest.AllowedToolSpecification.argumentsSchemaJson()`。
- Produces: 支持 object、array、items、properties、required、description、additionalProperties 的 `ToolSpecification` 和同源参数校验。

- [ ] **Step 1: 写嵌套 Schema 失败测试**

在 `ModelGatewayContractTest` 增加真实嵌套条件测试：

```java
@Test
void openAiGatewayAcceptsNestedProjectQuerySchemaAndArguments() {
    String schema = "{\"type\":\"object\",\"properties\":{" +
            "\"filter\":{\"type\":\"object\",\"properties\":{" +
            "\"logic\":{\"type\":\"string\"}," +
            "\"conditions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{" +
            "\"field\":{\"type\":\"string\"},\"operator\":{\"type\":\"string\"},\"value\":{}}," +
            "\"required\":[\"field\",\"operator\"],\"additionalProperties\":false}}}," +
            "\"additionalProperties\":false}},\"additionalProperties\":false}";
    // 模型返回合法嵌套参数时，应产生 ToolRequested，而不是 MODEL_TOOL_SCHEMA_INVALID。
}
```

测试同时覆盖：未知嵌套字段、错误数组元素类型、缺少 required 字段均返回 `MODEL_TOOL_ARGUMENTS_INVALID`。

- [ ] **Step 2: 运行测试并确认红灯**

Run: `mvn -Dtest=ModelGatewayContractTest#openAiGatewayAcceptsNestedProjectQuerySchemaAndArguments test`

Expected: FAIL，事件为 `MODEL_TOOL_SCHEMA_INVALID`，证明当前解析器不支持 object/array。

- [ ] **Step 3: 用递归节点替换扁平 Schema 结构**

在 `OpenAiCompatibleModelGateway` 中将 `SupportedToolSchema.propertyTypes` 替换为递归结构：

```java
private record SupportedSchemaNode(
        String type,
        Map<String, SupportedSchemaNode> properties,
        Set<String> required,
        SupportedSchemaNode items,
        boolean additionalProperties) {
    boolean accepts(JsonNode value, int depth) {
        if (depth > 5 || !matchesDeclaredType(value)) return false;
        if ("array".equals(type)) {
            for (JsonNode item : value) if (!items.accepts(item, depth + 1)) return false;
        }
        if ("object".equals(type)) {
            for (String name : required) if (!value.has(name)) return false;
            return acceptsProperties(value, depth + 1);
        }
        return true;
    }
}
```

`description` 只用于生成模型 Schema，不参与类型判断；空 Schema `{}` 代表 JSON 任意值，仅允许用于 condition.value。根节点仍必须为 object。

- [ ] **Step 4: 运行 Schema 契约测试**

Run: `mvn -Dtest=ModelGatewayContractTest test`

Expected: PASS，原简单工具 Schema 和新嵌套 Schema 均可用。

- [ ] **Step 5: 提交 Task 1**

```bash
git add src/main/java/com/smart/agent/model/OpenAiCompatibleModelGateway.java src/test/java/com/smart/agent/model/ModelGatewayContractTest.java
git commit -m "feat: support nested tool schemas"
```

---

### Task 2: 定义跨模块项目查询协议

**Files:**
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectQueryRequestVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectFilterGroupVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectConditionVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectOrderVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectAggregationVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectQueryResultVO.java`
- Create: `smart-boot/smart-model/src/main/java/com/smart/model/business/project/query/ProjectQueryColumnVO.java`
- Create: `smart-boot/smart-model/src/test/java/com/smart/model/business/project/query/ProjectQueryRequestVOTest.java`

**Interfaces:**
- Produces: Java 8 DTO 协议，供 smart-ai、smart-service、smart-project 共同使用。
- Request fields: `select`, `filter`, `groupBy`, `aggregations`, `orderBy`, `page`, `pageSize`, `projectIds`。
- Result fields: `mode`, `page`, `pageSize`, `total`, `hasNext`, `columns`, `rows`, `appliedFilters`。

- [ ] **Step 1: 写 DTO 默认值和上限测试**

```java
@Test
public void normalizeAppliesQueryLimits() {
    ProjectQueryRequestVO request = new ProjectQueryRequestVO();
    request.setPage(0);
    request.setPageSize(500);
    request.normalize();
    assertEquals(Integer.valueOf(1), request.getPage());
    assertEquals(Integer.valueOf(100), request.getPageSize());
}
```

另测 projectIds 不从模型 JSON 暴露：Agent 请求 DTO 与内部服务请求 DTO 分离，只有 smart-boot 共用 DTO 包含 `projectIds`。

- [ ] **Step 2: 运行协议测试并确认红灯**

Run: `mvn -pl smart-model -am -Dtest=ProjectQueryRequestVOTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，查询 DTO 尚不存在。

- [ ] **Step 3: 实现 Java 8 DTO**

所有 DTO 使用普通 class、无参构造器、getter/setter。`normalize()` 只处理分页默认值；字段合法性由后续 Validator 负责，避免 DTO 混入业务规则。

- [ ] **Step 4: 运行协议测试及 Java 8 编译**

Run: `mvn -pl smart-model -am -Dtest=ProjectQueryRequestVOTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 5: 提交 Task 2**

```bash
git add smart-model/src/main/java/com/smart/model/business/project/query smart-model/src/test/java/com/smart/model/business/project/query
git commit -m "feat: define project query protocol"
```

---

### Task 3: 注册项目业务字段并校验查询计划

**Files:**
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/project/query/AiProjectQueryField.java`
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/project/query/AiProjectQueryFieldRegistry.java`
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/project/query/AiProjectQueryValidator.java`
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/project/query/AiProjectQueryException.java`
- Create: `smart-boot/smart-ai/src/test/java/com/smart/ai/project/query/AiProjectQueryValidatorTest.java`

**Interfaces:**
- Consumes: `ProjectQueryRequestVO`。
- Produces: 已验证并完成字段、操作符、字典和安全 SQL 标识映射的查询计划。

- [ ] **Step 1: 写业务字段和系统字段边界测试**

```java
@Test
public void registryContainsBusinessFieldsButRejectsSystemFields() {
    assertNotNull(registry.require("projectName"));
    assertNotNull(registry.require("projectBudget"));
    assertNotNull(registry.require("createDate"));
    assertQueryError("tenantId");
    assertQueryError("isDeleted");
    assertQueryError("processInstanceId");
    assertQueryError("reserve1");
}
```

同时覆盖项目实体中全部已确认业务字段，避免遗漏后由模型退化为 JSON 或文字猜测。

- [ ] **Step 2: 写操作符、嵌套和限额测试**

覆盖 30 个条件、深度 3、5 个排序、5 个分组、10 个聚合边界；文本、数字、日期、字典分别验证合法及非法运算。

- [ ] **Step 3: 运行 Validator 测试并确认红灯**

Run: `mvn -pl smart-ai -am -Dtest=AiProjectQueryValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，字段注册表和校验器尚不存在。

- [ ] **Step 4: 实现字段注册表和校验器**

字段定义只接受服务端常量：

```java
register(new AiProjectQueryField(
        "projectStatus", "项目状态", "project_status", FieldType.DICTIONARY,
        "bus_project_status", operators(EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL)));
```

字段注册表包含全部项目业务字段及自然语言展示名；`createDate`、`updateDate` 只作为“创建时间”“更新时间”。字典值使用 `AiDictionaryResolver` 转换。错误信息使用中文业务描述，不返回数据库列名。

- [ ] **Step 5: 运行 Validator 和现有字典测试**

Run: `mvn -pl smart-ai -am -Dtest=AiProjectQueryValidatorTest,AiDictionaryResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交 Task 3**

```bash
git add smart-ai/src/main/java/com/smart/ai/project/query smart-ai/src/test/java/com/smart/ai/project/query
git commit -m "feat: validate project query fields"
```

---

### Task 4: 在原项目 Service 和 Mapper 链路执行查询

**Files:**
- Modify: `smart-boot/smart-service/src/main/java/com/smart/service/project/BusProjectService.java`
- Modify: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusProjectServiceImpl.java`
- Modify: `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/dao/BusProjectDao.java`
- Modify: `smart-boot/smart-business/smart-project/src/main/resources/mapper/BusProjectDao.xml`
- Create: `smart-boot/smart-business/smart-project/src/test/java/com/smart/project/service/BusProjectAiQueryServiceTest.java`

**Interfaces:**
- Add: `ProjectQueryResultVO queryForAi(ProjectQueryRequestVO request)` to `BusProjectService`。
- Add DAO detail and aggregate methods that consume a server-built `QueryWrapper` and server-owned select/group fragments.

- [ ] **Step 1: 写空可信范围和条件执行失败测试**

```java
@Test
public void emptyTrustedProjectScopeReturnsEmptyWithoutCallingMapper() {
    ProjectQueryRequestVO request = validRequest();
    request.setProjectIds(Collections.<String>emptyList());
    ProjectQueryResultVO result = service.queryForAi(request);
    assertEquals(0L, result.getTotal());
    verifyNoInteractions(mapper);
}
```

另测可信范围始终生成 `id IN (...)`、条件值参数绑定、AND/OR 嵌套、排序和分页。

- [ ] **Step 2: 写聚合执行失败测试**

覆盖按项目类型 count、预算 sum/avg/max/min、多字段 groupBy 和 50 组上限。断言 Mapper 接收的列只能来自字段注册结果，不能包含原始模型字段文本。

- [ ] **Step 3: 运行 Service 测试并确认红灯**

Run: `mvn -pl smart-business/smart-project -am -Dtest=BusProjectAiQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，`queryForAi` 尚不存在。

- [ ] **Step 4: 实现受控 QueryWrapper 构建**

递归构建条件时只使用注册后的数据库列：

```java
private void applyGroup(QueryWrapper<BusProjectEntity> wrapper, ValidatedFilterGroup group) {
    wrapper.nested(child -> {
        for (ValidatedCondition condition : group.getConditions()) {
            applyBoundCondition(child, condition);
        }
        for (ValidatedFilterGroup nested : group.getGroups()) {
            applyNestedWithDeclaredLogic(child, nested);
        }
    });
}
```

值通过 QueryWrapper 参数绑定。任何 `${}` 片段只能由服务端字段注册表生成，不能包含请求原文。详情查询固定选择全部业务字段；响应阶段将字典编码转换为中文显示值。

- [ ] **Step 5: 保证原权限链路**

通过 `BusProjectDao` 执行，使查询继续经过租户拦截器和 `QueryInterceptor`。所有查询额外加入可信项目 ID 范围；空范围短路。增加测试断言 `isCloseDataScope` 不被开启。

- [ ] **Step 6: 运行项目模块回归和 Java 8 编译**

Run: `mvn -pl smart-business/smart-project -am -Dtest=BusProjectAiQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run: `mvn -pl smart-business/smart-project -am -DskipTests package`

Expected: 两条命令均 BUILD SUCCESS。

- [ ] **Step 7: 提交 Task 4**

```bash
git add smart-service/src/main/java/com/smart/service/project/BusProjectService.java smart-business/smart-project/src/main
git add smart-business/smart-project/src/test/java/com/smart/project/service/BusProjectAiQueryServiceTest.java
git commit -m "feat: execute permission scoped project queries"
```

---

### Task 5: 暴露 smart-boot 内部查询端点

**Files:**
- Modify: `smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiProjectToolController.java`
- Create: `smart-boot/smart-ai/src/main/java/com/smart/ai/project/query/AiProjectQueryService.java`
- Modify: `smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiProjectToolControllerTest.java`
- Create: `smart-boot/smart-ai/src/test/java/com/smart/ai/project/query/AiProjectQueryServiceTest.java`

**Interfaces:**
- Add: `POST /internal/ai/tools/project-query`。
- Request: DSL + smart-agent 注入的可信 `projectIds`。
- Response: `ProjectQueryResultVO`。

- [ ] **Step 1: 写端点失败测试**

```java
@Test
public void projectQueryUsesTrustedProjectIdsAndProjectService() {
    ProjectQueryRequestVO request = validRequest();
    request.setProjectIds(Arrays.asList("project-1"));
    controller.query(request);
    verify(queryService).query(request);
}
```

覆盖空范围返回空结果、未知字段返回中文 400、原 `/projects` `/project-overview` `/project-contracts` 仍可调用。

- [ ] **Step 2: 运行端点测试并确认红灯**

Run: `mvn -pl smart-ai -am -Dtest=AiProjectToolControllerTest,AiProjectQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新端点和服务尚不存在。

- [ ] **Step 3: 实现端点及校验服务**

端点只负责接收和返回；`AiProjectQueryService` 顺序固定为 normalize、限额校验、字段/字典转换、调用 `BusProjectService.queryForAi`。内部签名继续由现有 `AiInternalRequestInterceptor` 保护。

- [ ] **Step 4: 运行 smart-ai 全部测试和编译**

Run: `mvn -pl smart-ai -am test`

Run: `mvn -pl smart-ai -am -DskipTests package`

Expected: BUILD SUCCESS，且为 Java 8 字节码。

- [ ] **Step 5: 提交 Task 5**

```bash
git add smart-ai/src/main/java/com/smart/ai smart-ai/src/test/java/com/smart/ai
git commit -m "feat: expose internal project query tool"
```

---

### Task 6: 接入 Agent `project.query` 和对话编排

**Files:**
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryInput.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryFilter.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryCondition.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryOrder.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryAggregation.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryResult.java`
- Create: `agent/src/main/java/com/smart/agent/tool/project/ProjectQueryTool.java`
- Modify: `agent/src/main/java/com/smart/agent/tool/project/ProjectBusinessClient.java`
- Modify: `agent/src/main/java/com/smart/agent/tool/project/SmartBootProjectBusinessClient.java`
- Modify: `agent/src/main/java/com/smart/agent/tool/project/LocalProjectBusinessClient.java`
- Modify: `agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Modify: `agent/src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`
- Modify: `agent/src/main/java/com/smart/agent/chat/AgentErrorMessageCatalog.java`
- Create: `agent/src/test/java/com/smart/agent/tool/project/ProjectQueryToolTest.java`
- Modify: `agent/src/test/java/com/smart/agent/tool/project/SmartBootProjectBusinessClientTest.java`
- Modify: `agent/src/test/java/com/smart/agent/chat/ChatControllerIT.java`

**Interfaces:**
- Tool key: `project.query`。
- Permission: `menu:project`。
- Risk: `L1`。
- Client method: `ProjectQueryResult query(ToolContext context, ProjectQueryInput input)`。

- [ ] **Step 1: 写工具权限与可信范围失败测试**

```java
@Test
void injectsTrustedProjectScopeInsteadOfAcceptingModelScope() {
    ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("p1", "p2"));
    tool.execute(validInput(), context);
    verify(client).query(eq(context), argThat(input -> !input.exposesProjectIds()));
}
```

确认工具 Schema 中不存在 tenantId、userId、projectIds、数据库列或 SQL 字段；调用客户端时由 `ToolContext.projectIds()` 单独注入 HTTP 请求。

- [ ] **Step 2: 写随机问法和上下文分页失败测试**

在 `ChatControllerIT` 覆盖：

- “查询今年创建且已经立项的联营项目数量”提供 `project.query`。
- “按所属组织统计项目数量”提供 `project.query`。
- “预算在一百万到五百万之间的项目有哪些”提供 `project.query`。
- “查看更多”保留上一轮筛选条件并将 page 加一。
- “查询今年立项的项目”不调用工具，先返回日期口径澄清。

- [ ] **Step 3: 运行 Agent 测试并确认红灯**

Run: `mvn -Dtest=ProjectQueryToolTest,SmartBootProjectBusinessClientTest,ChatControllerIT test`

Expected: FAIL，`project.query` 尚未注册。

- [ ] **Step 4: 实现 Agent 查询 DTO、工具和客户端**

`ProjectQueryInput` 只包含模型可控制的 DSL 字段。`SmartBootProjectBusinessClient` 使用独立内部请求对象合并：

```java
new ProjectQueryRequest(
        context.projectIds(), input.select(), input.filter(), input.groupBy(),
        input.aggregations(), input.orderBy(), input.page(), input.pageSize());
```

空可信范围不发起 smart-boot 请求，直接返回空结果。

- [ ] **Step 5: 更新工具选择和中文系统指令**

`ChatOrchestrator.isRelevantTool` 将统计、数量、筛选、排序以及项目业务字段问题视为项目工具相关；结合会话历史处理“查看更多”“按创建时间查”等追问。系统指令要求日期歧义先澄清、字典编码不输出、列表使用表格、聚合给出口径。

- [ ] **Step 6: 增加友好错误映射**

新增稳定错误码：`AGENT_QUERY_FIELD_UNKNOWN`、`AGENT_QUERY_OPERATOR_INVALID`、`AGENT_QUERY_VALUE_INVALID`、`AGENT_QUERY_TOO_COMPLEX`。所有错误持久化中文回答，不显示工具参数 JSON。

- [ ] **Step 7: 运行 Agent 专项和全量测试**

Run: `mvn -Dtest=ProjectQueryToolTest,SmartBootProjectBusinessClientTest,ChatControllerIT,ModelGatewayContractTest test`

Run: `mvn test`

Run: `mvn clean compile -DskipTests`

Expected: 全部 BUILD SUCCESS。

- [ ] **Step 8: 更新进度文档并提交 Task 6**

更新 `agent/AI能力建设方案与开发进度.md`，记录项目语义查询已完成范围、暂不支持资源和验收结果。

```bash
git add src/main/java src/test/java AI能力建设方案与开发进度.md
git commit -m "feat: add permission aware project semantic query"
```

---

## 最终端到端验收

1. 使用有项目菜单且有 59 个可信项目的管理员测试明细、统计、分组和分页。
2. 使用无项目菜单的用户确认 `project.query` 不出现在模型工具列表。
3. 使用只有部分项目数据权限的用户确认统计总数和明细均只包含其项目范围。
4. 检查 smart-agent 模型调用日志：工具列表包含 `project.query`，不出现参数 JSON 正文。
5. 检查 smart-boot 日志：请求包含可信项目数量，空范围不执行 Mapper。
6. 检查工具审计：记录字段摘要、结果数量、耗时和稳定错误码，不记录密钥及请求头。
7. 回归原项目页面、`project.listAccessible`、`project.getOverview`、`project.getContracts`，确认行为未改变。
