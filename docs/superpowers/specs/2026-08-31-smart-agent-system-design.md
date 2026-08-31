# 工程管理智能体系统设计说明书

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 系统名称 | 工程管理智能体（smart-agent） |
| 文档类型 | 系统总体设计说明书 |
| 版本 | V1.0 |
| 日期 | 2026-08-31 |
| 适用阶段 | 一期至三期总体建设 |
| 关联系统 | smart-web、smart-boot |

## 2. 建设背景

现有工程管理系统采用前后端分离架构：前端为 Vue 3，后端为 Java 8、Spring Boot 2.7 和 MySQL。系统已经覆盖项目、合同、预算、财务、进度、招投标、档案等多个工程管理领域。

本次建设在现有系统之外新增独立的 `smart-agent` 服务，为用户提供统一智能问答、实时业务数据查询、知识库检索、综合分析、报告生成以及后续受控业务操作能力。

当前只建设面向本工程管理系统的智能体，不建设跨业务平台的通用智能体中台。

## 3. 建设目标

### 3.1 总体目标

建设一个用户可见的“工程管理智能助手”，通过统一对话中心和业务页面侧边助手，为用户提供以下能力：

1. 查询项目、合同、进度、预算、财务等实时业务数据。
2. 检索企业制度、工程规范、合同、项目文档及系统附件。
3. 对多个业务模块的数据进行综合分析和风险提示。
4. 生成报表、汇报材料、风险清单和业务草稿。
5. 在用户确认和权限复核后执行受控业务操作。
6. 完整记录模型、知识检索、工具调用和人工确认过程。

### 3.2 非目标

当前阶段不建设以下内容：

- 多业务平台接入中心；
- 通用 Connector SDK；
- 第三方开发者生态；
- 跨平台身份和知识空间；
- 完全自主执行的高风险智能体；
- 直接面向生产业务表的自由 SQL 智能体。

## 4. 已确定的架构决策

| 编号 | 决策 |
|---|---|
| D-01 | 用户侧只提供一个统一工程管理智能助手。 |
| D-02 | 系统内部按照工程领域动态加载能力包，不向用户暴露多个智能体。 |
| D-03 | `smart-agent` 独立于现有 `smart-boot` 部署。 |
| D-04 | `smart-agent` 使用 Java 21、Spring Boot 和 LangChain4j。 |
| D-05 | AI平台结构化数据使用独立 MySQL 数据库。 |
| D-06 | 知识向量使用 Qdrant，开发环境使用 Docker 单节点。 |
| D-07 | Redis用于限流、缓存、短期状态和异步任务状态。 |
| D-08 | 原始知识文件复用现有 MinIO、OSS或文件存储抽象。 |
| D-09 | 模型通过统一网关接入，兼容云端、本地和混合部署。 |
| D-10 | 智能体必须继承当前用户的角色、部门、公司、项目和数据范围权限。 |
| D-11 | 智能体不直接修改生产业务数据库，业务访问通过 `smart-boot` 工具适配接口。 |
| D-12 | 写操作按工具和角色配置风险等级，默认需要用户确认。 |
| D-13 | 一期以业务只读问答和知识库问答为主。 |

## 5. 总体架构

```text
┌─────────────────────────────────────────────┐
│                  smart-web                  │
│                                             │
│  统一AI对话中心          业务页面侧边助手    │
│  跨模块综合问答          自动携带页面上下文  │
└────────────────────┬────────────────────────┘
                     │ HTTPS + SSE
                     ▼
┌─────────────────────────────────────────────┐
│                 smart-agent                 │
│       Java 21 + Spring Boot + LangChain4j   │
│                                             │
│ 接入层  会话管理  智能体编排  能力路由       │
│ 工具中心  知识库RAG  模型网关  人工确认      │
│ 安全审计  异步任务  质量评测  用量统计       │
└────────┬──────────────┬──────────────┬──────┘
         │              │              │
         ▼              ▼              ▼
┌────────────────┐ ┌──────────┐ ┌──────────────┐
│ smart-boot     │ │ Qdrant   │ │ 模型供应方    │
│ AI业务适配模块 │ │ 向量检索 │ │ 云端/本地/混合│
└───────┬────────┘ └────┬─────┘ └──────────────┘
        │               │
        ▼               ▼
┌────────────────┐ ┌──────────────────┐
│ 现有业务MySQL  │ │ 知识切片及权限标签│
│ 权限与业务规则 │ │ 工具语义索引      │
└────────────────┘ └──────────────────┘

smart-agent基础设施：
├─ MySQL：配置、会话、运行、审计和知识元数据
├─ Redis：缓存、限流和临时状态
├─ Qdrant：知识和工具向量索引
└─ MinIO/OSS：原始文件和解析结果
```

## 6. 系统边界与职责

### 6.1 smart-web

`smart-web` 提供统一AI对话中心和业务页面侧边助手。

统一对话中心负责：

- 新建、查看和管理会话；
- 跨模块业务问答；
- 知识库问答；
- 显示工具执行状态和引用来源；
- 处理写操作确认；
- 收集点赞、点踩和纠错反馈。

页面侧边助手额外传递当前页面上下文：

```json
{
  "pageCode": "contract.detail",
  "projectId": "当前项目ID",
  "businessType": "contract",
  "businessId": "当前合同ID",
  "selectedIds": []
}
```

前端上下文不作为可信权限依据，后端必须重新加载业务对象并校验权限。

### 6.2 smart-agent

`smart-agent` 负责：

- 用户问题理解和意图识别；
- 会话上下文管理；
- 领域能力包选择；
- 工具发现、参数生成和调用编排；
- 知识检索、重排序和引用生成；
- 模型选择、调用、降级和用量统计；
- 人工确认和执行恢复；
- 安全控制、审计、评测和反馈；
- 文档解析和异步任务管理。

### 6.3 smart-boot

`smart-boot` 是业务事实和权限的最终权威，负责：

- 用户、租户、身份、角色、部门和公司权限；
- 项目和数据范围权限；
- 工程业务数据和业务规则；
- 工具参数的最终校验；
- 写操作事务、幂等和数据版本检查；
- 返回业务数据引用和详情页信息。

## 7. 统一智能体设计

### 7.1 用户侧形态

用户只看到一个“工程管理智能助手”，不需要选择项目、合同或财务智能体。

### 7.2 领域能力包

统一助手内部按照问题动态加载能力包：

| 能力包 | 主要职责 |
|---|---|
| project | 项目概况、组织、状态和综合查询 |
| contract | 合同检索、履约、付款和条款分析 |
| schedule | 计划、里程碑、延期和进度分析 |
| budget | 预算执行、成本偏差和超预算分析 |
| finance | 收付款、发票、资金和逾期分析 |
| bidding | 招标、投标和评审资料查询 |
| archive | 制度、规范、项目文件和档案问答 |
| report | 跨领域汇总、报表和汇报材料生成 |

每个能力包包含：

- 领域工具集合；
- 业务术语及规则；
- 允许检索的知识空间；
- 输出格式；
- 工具风险和确认规则；
- 领域提示词。

### 7.3 跨领域调用

跨领域问题由统一编排器生成受控执行计划。例如“分析项目综合风险”：

1. 查询项目基本信息；
2. 查询进度和延期节点；
3. 查询合同履约及付款；
4. 查询预算和成本偏差；
5. 检索风险管理制度；
6. 汇总事实、依据和智能分析。

## 8. 智能体执行状态机

智能体运行状态必须持久化：

```text
RECEIVED
→ ROUTING
→ PLANNING
→ TOOL_SELECTING
→ TOOL_EXECUTING
→ RETRIEVING
→ GENERATING
→ WAITING_APPROVAL
→ RESUMING
→ COMPLETED
```

异常状态包括：

```text
FAILED
CANCELLED
TIMEOUT
PERMISSION_DENIED
```

进入 `WAITING_APPROVAL` 时保存当前运行快照。用户确认后根据 `runId` 从中断位置恢复，不重新执行已经成功的读取步骤。

## 9. 业务工具设计

### 9.1 工具定义

每个工具至少声明：

- 工具编码和版本；
- 使用说明；
- 输入和输出Schema；
- 所属能力包；
- 风险等级；
- 所需业务权限；
- 是否需要确认；
- 超时时间；
- 幂等策略；
- 是否可重试或撤销。

### 9.2 工具示例

```text
project.listUserProjects
project.getOverview
contract.getSummary
contract.getPaymentSummary
schedule.getProgress
schedule.getDelayedNodes
budget.getExecutionAnalysis
finance.getReceivableSummary
knowledge.search
report.generate
```

### 9.3 风险等级

| 等级 | 类型 | 示例 | 默认策略 |
|---|---|---|---|
| L0 | 公共知识及元数据 | 查字典、查公开制度 | 自动执行 |
| L1 | 业务只读 | 查项目、合同和预算 | 自动执行并审计 |
| L2 | 可恢复写入 | 保存草稿、生成报表 | 预览后确认 |
| L3 | 高风险写入 | 审批、删除、付款、外发消息 | 强确认和权限复核 |

### 9.4 工具调用链路

```text
smart-agent选择工具
→ 检查工具授权和风险等级
→ 调用smart-boot内部AI接口
→ smart-boot重新验证用户和数据权限
→ 调用现有业务Service
→ 返回精简结构化结果
→ smart-agent生成答案并记录引用
```

## 10. 数据库和业务查询设计

### 10.1 标准业务工具

高频和口径稳定的查询必须封装为标准工具，作为业务问答的主要数据来源。

### 10.2 业务指标中心

统计分析通过指标定义实现。指标至少包括：

- 指标名称和业务说明；
- 计算公式；
- 数据来源；
- 时间字段；
- 可用维度；
- 权限维度；
- 空值及异常处理规则。

首期建议整理20至50个高频指标，如项目完成率、节点延期天数、合同付款率、预算执行率和成本偏差率。

### 10.3 受控探索查询

探索性查询必须满足：

- 使用只读数据源；
- 只开放白名单视图和字段；
- 只允许 `SELECT`；
- 进行SQL抽象语法树校验；
- 强制注入租户、公司、项目和数据范围条件；
- 限制执行时间、扫描范围和结果行数；
- 屏蔽或脱敏敏感字段；
- 记录SQL、参数、用户、耗时和结果摘要。

一期可以不开放自由探索SQL，优先使用标准工具和指标查询。

## 11. 知识库设计

### 11.1 知识空间

知识空间分为：

- 集团公共知识库；
- 公司知识库；
- 部门知识库；
- 项目知识库；
- 个人知识库；
- 业务专题知识库。

### 11.2 支持文件

一期支持PDF、Word、Excel、Markdown、TXT和系统已有附件。扫描件OCR和PPT可在后续阶段增强。

### 11.3 文档处理流程

```text
上传或同步文件
→ 安全检查
→ 内容、标题、表格和页码提取
→ OCR（需要时）
→ 按章节和语义切片
→ 生成Embedding
→ 写入Qdrant
→ 写入权限标签
→ 发布知识版本
```

### 11.4 Qdrant设计

开发环境使用Docker单节点，建议Collection：

```text
agent_knowledge_dev
agent_tool_index_dev
```

知识切片payload至少包含：

```json
{
  "tenant_id": "当前租户ID",
  "organization_id": "所属公司ID",
  "project_id": "关联项目ID",
  "space_id": "知识空间ID",
  "document_id": "文档ID",
  "document_version": 1,
  "chunk_id": "知识切片ID",
  "page_number": 1,
  "section_title": "章节标题",
  "status": "published"
}
```

MySQL保存文档和切片元数据并作为主数据；Qdrant是可重建的检索索引。

### 11.5 检索流程

```text
用户问题
→ 解析用户、项目和页面上下文
→ 计算允许访问的知识空间
→ Qdrant权限过滤
→ 关键词与向量混合检索
→ 重排序
→ 返回原文片段
→ 模型生成答案
→ 展示文件、页码、章节和链接
```

回答必须区分：

- 实时业务数据；
- 知识库制度或文档依据；
- 模型生成的分析和建议。

## 12. 模型网关设计

所有模型调用通过 `ModelGateway`，业务代码不得绑定具体供应商。

```text
Agent
└─ ModelGateway
   ├─ chatModel
   ├─ reasoningModel
   ├─ summaryModel
   ├─ embeddingModel
   └─ rerankModel
```

模型网关负责：

- 模型供应商适配；
- 云端、本地和混合路由；
- 模型超时、重试、熔断和降级；
- Token、费用和响应时间统计；
- 敏感内容脱敏；
- 模型能力检查；
- 模型和提示词版本记录。

## 13. 身份、权限和安全

### 13.1 权限上下文

智能体上下文包含：

- 用户和租户；
- 当前身份；
- 公司和部门；
- 角色；
- 项目范围；
- 菜单及按钮权限；
- 当前页面和业务对象；
- 允许使用的工具。

### 13.2 双重权限校验

1. `smart-agent`筛选用户可用能力、工具和知识空间。
2. `smart-boot`执行工具时重新校验业务权限和数据范围。

### 13.3 写操作确认

```text
生成操作草稿
→ smart-boot预校验
→ 前端展示变更预览
→ 用户确认
→ 再次校验权限和数据版本
→ 执行业务事务
→ 返回业务编号和审计编号
```

### 13.4 安全控制

- 系统提示词和用户输入隔离；
- 文档内容视为不可信数据，防止知识库提示词注入；
- 工具白名单和参数Schema校验；
- 单轮工具调用次数和最大执行深度限制；
- 写操作幂等键；
- 敏感字段脱敏；
- 会话身份改变后重新授权；
- 文档权限变更时同步更新向量索引；
- 不保存和展示模型内部思维链；
- 高风险工具不能配置为完全自动执行。

## 14. 核心数据模型

### 14.1 配置类

```text
ai_agent_config
ai_capability
ai_tool
ai_tool_permission
ai_model_provider
ai_model_config
ai_prompt_template
```

### 14.2 会话与运行类

```text
ai_conversation
ai_message
ai_run
ai_run_step
ai_tool_call
ai_approval
ai_citation
```

### 14.3 知识库类

```text
ai_knowledge_space
ai_document
ai_document_version
ai_document_acl
ai_document_chunk
ai_ingestion_job
```

### 14.4 运营治理类

```text
ai_feedback
ai_evaluation_case
ai_evaluation_result
ai_usage_daily
ai_security_event
```

所有业务表应遵循现有项目ID和审计字段规范，并至少包含租户、创建人、创建时间、更新人、更新时间和逻辑删除字段。

## 15. 核心接口

### 15.1 smart-web访问smart-agent

```text
POST /agent/chat/stream
POST /agent/chat/cancel
GET  /agent/conversation/page
GET  /agent/conversation/{id}
POST /agent/conversation/{id}/feedback
POST /agent/approval/{id}/confirm
POST /agent/approval/{id}/reject
POST /agent/knowledge/document/upload
GET  /agent/knowledge/document/page
POST /agent/knowledge/document/{id}/reindex
GET  /agent/task/{id}/status
```

对话采用SSE流式事件：

```text
message_start
status
tool_start
tool_result
citation
approval_required
message_delta
message_end
error
```

### 15.2 smart-agent访问smart-boot

```text
POST /internal/agent/context/exchange
POST /internal/agent/tool/validate
POST /internal/agent/tool/execute
POST /internal/agent/tool/confirm
GET  /internal/agent/business/citation
```

## 16. 代码结构

### 16.1 smart-agent

一期采用单Maven工程、按package分层：

```text
smart-agent/
└─ src/main/java/com/smart/agent/
   ├─ api
   ├─ core
   │  ├─ graph
   │  ├─ router
   │  ├─ planner
   │  └─ guardrail
   ├─ capability
   │  ├─ project
   │  ├─ contract
   │  ├─ schedule
   │  ├─ budget
   │  ├─ finance
   │  ├─ bidding
   │  ├─ archive
   │  └─ report
   ├─ tool
   ├─ knowledge
   ├─ model
   ├─ security
   ├─ task
   ├─ audit
   └─ infrastructure
```

代码规模扩大后再拆分Maven模块。

### 16.2 smart-boot

新增 `smart-agent-adapter` 模块：

```text
smart-agent-adapter/
├─ controller
├─ auth
├─ context
├─ tool
│  ├─ project
│  ├─ contract
│  ├─ schedule
│  ├─ budget
│  ├─ finance
│  └─ bidding
├─ citation
└─ audit
```

## 17. 部署设计

```text
Nginx
├─ /admin       → smart-web
├─ /api         → smart-boot
└─ /agent-api   → smart-agent
```

初期参考资源按100名用户、约20人同时在线估算：

| 组件 | 开发/测试建议 | 初期生产参考 |
|---|---|---|
| smart-agent | 2核4GB，1实例 | 4核8GB，2实例 |
| MySQL AI库 | 可共用开发实例并独立Schema | 4核16GB，SSD |
| Redis | 单节点 | 2核4GB或高可用实例 |
| Qdrant | Docker单节点、持久化目录 | 独立节点并配置备份 |
| 文档Worker | 2核4GB | 4至8核、8至16GB |
| 对象存储 | 复用现有设施 | 复用并配置生命周期 |

模型采用云端时不需要GPU；本地模型资源根据最终模型单独评估。

## 18. 可观测性和审计

每次智能体运行使用统一 `traceId` 关联：

- 用户问题；
- 模型和提示词版本；
- 工具选择和参数；
- 工具结果摘要；
- 知识检索条件和引用；
- 人工确认；
- 最终答案；
- Token、耗时和费用；
- 异常和降级过程。

监控指标包括：

- 请求量、成功率和并发；
- 首字响应时间和总响应时间；
- 模型错误率及降级次数；
- 工具成功率和耗时；
- 知识检索耗时和无结果率；
- Token和费用；
- 越权及安全事件；
- 用户反馈率和答案满意度。

## 19. 质量评测

上线前建立标准评测集，至少覆盖：

- 业务工具选择准确率；
- 工具参数准确率；
- 数据权限隔离；
- 知识引用准确率；
- 无依据问题拒答；
- 跨模块综合分析；
- Prompt注入防护；
- 写操作确认及幂等；
- 模型超时和降级；
- 文档权限变更。

提示词、模型、Embedding模型、切片方式和检索参数变更后必须重新运行评测。

## 20. 分阶段实施

### 20.1 第一期：智能问答基础平台

目标：完成安全、可追溯的只读业务问答和知识库问答。

交付内容：

- `smart-agent`基础工程；
- 模型网关；
- 统一AI对话中心；
- 页面侧边助手基础组件；
- 身份及权限上下文；
- 项目、合同、进度和预算基础工具；
- Qdrant开发环境；
- 知识空间、文档上传和基础解析；
- 带引用的知识问答；
- 会话、审计、反馈和基础评测。

### 20.2 第二期：综合分析与报表

- 财务、招投标和档案等更多工具；
- 指标中心；
- 跨领域综合分析；
- Excel深度解析、OCR和PPT；
- 混合检索及重排序；
- 报表、汇报和风险清单生成；
- 异步任务、模型路由和成本统计；
- 评测和运营管理后台。

### 20.3 第三期：受控业务执行

- 业务草稿生成；
- 写操作预校验；
- 人工确认和状态恢复；
- 审批提交、消息发送等业务工具；
- 幂等、版本校验和执行回放；
- 工具级角色授权；
- 生产监控、备份和横向扩容。

## 21. 一期验收原则

一期验收至少满足：

1. 用户只能查询本人原本有权访问的业务数据和知识。
2. 页面助手能够正确识别当前项目和业务对象。
3. 项目、合同、进度、预算四类工具稳定可用。
4. 知识回答能够展示真实文件、页码或章节引用。
5. 无可靠依据时明确提示无法回答，不编造业务数据。
6. 每次模型、工具和检索调用均可审计和追踪。
7. 模型不可用时有明确错误或备用模型降级。
8. Qdrant索引可以依据MySQL元数据重建。
9. 核心评测集可以重复运行并输出结果。
10. 一期不允许智能体直接执行生产写操作。

## 22. 实施前待确定参数

以下项目不影响总体架构，可在一期实施计划或技术验证阶段确定：

| 项目 | 默认建议 |
|---|---|
| 首个对话模型 | 选择支持稳定工具调用和流式输出的模型 |
| 备用对话模型 | 配置不同供应商或本地模型 |
| Embedding模型 | 优先选择中文及中英混合检索效果好的模型 |
| Rerank模型 | 一期可选，检索质量不足时启用 |
| LangChain4j具体版本 | 实施时锁定稳定版本并做兼容性验证 |
| Spring Boot具体版本 | 根据LangChain4j兼容矩阵选择稳定版本 |
| Qdrant镜像版本 | 实施时固定明确版本，不使用长期漂移的latest |
| 文档解析组件 | 技术验证后在Apache Tika、POI及专项解析器中选择 |
| OCR组件 | 二期根据私有部署和识别准确率选型 |
| 消息队列 | 一期可使用Redis任务队列，规模扩大后再引入独立MQ |
| 首期业务工具清单 | 与业务负责人确认高频问题后锁定 |
| 生产并发和资源 | 根据压测及真实用户规模调整 |
| 知识保密等级规则 | 与现有档案和项目权限制度对齐 |

## 23. 核心设计原则

1. 一个统一助手，内部动态加载领域能力。
2. 模型只提出工具请求，应用程序决定是否执行。
3. 业务权限最终由 `smart-boot` 校验。
4. 智能体不能自由写入业务数据库。
5. 知识检索必须先做权限过滤。
6. 回答必须区分业务事实、知识依据和模型分析。
7. 高风险操作必须人工确认。
8. MySQL是AI平台元数据主库，Qdrant是可重建的检索索引。
9. 模型、向量库和存储组件均通过接口隔离。
10. 当前只服务工程管理系统，不提前建设通用平台。
