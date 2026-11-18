# AI query permission switch

Accepted scope: tenant-local `sys_config.ai_query_permission_enabled`; `1` enforces existing permissions (default), `0` relaxes business read permissions. Missing, invalid or failed reads enforce permissions. Authentication, signatures, tenant isolation and business predicates remain required. No management/write privileges or identity changes. SELF approval queries remain SELF; explicit ALL/person queries are allowed when disabled.

Implementation plan and progress:
- [x] Add tenant-explicit uncached policy and business-read permission allowlist, with fail-closed tests.
- [x] Apply policy to outgoing signed context and internal tool requests; reject stale unrestricted contexts after re-enabling.
- [x] Support approval list/detail/diagram and archive reads without menu grants while disabled.
- [x] Add idempotent SQL initialization (default enabled); do not execute against databases.
- [x] Run focused permission regressions, review changes and document manual acceptance.

Manual acceptance: use an ordinary account without business menus. With 1, existing denials remain. With 0, project/contracts/business queries and explicit other-person approvals succeed within tenant; mine remains mine. Model/knowledge/question administration must remain denied. Restore 1 and ask again; old unrestricted tool requests must fail. Invalid/missing configuration must enforce permissions.

Restart smart-boot after deploying code; no server is started by this task. Database script execution is a deployment step. Agent and Web have no planned runtime changes.

## Deployment and behavior

Initialization script: `smart-boot/smart-app/src/main/resources/sql/2026-11-11-ai-query-permission-switch.sql`. On 2026-11-11, after explicit user authorization, executed against dev (`localhost:3306/gongcheng`) and uat (`192.168.1.200:3305/gongcheng`). Both databases had no existing switch; each inserted and committed exactly one row, then verified `config_value=1`, `need_auth=1`, `tenant_id=000000`. No changes to smart_agent databases. It initializes the existing default tenant (`000000`) without overwriting existing values. Current schema has a globally unique config_key; configurations belonging to a different tenant never disable this tenant's checks.

Change the setting through system configuration: `1` enforces, exact `0` relaxes. Unknown/missing/duplicate values or database failures enforce. The policy reads the database on every new outgoing context and every internal tool call, so no application restart/cache flush is required after changing a value.

The tool's signed project subset remains intact and is intersected with the tenant's current project set. It is not expanded when resolving a deliberately selected project. Real user/role values and management grants remain unchanged. Existing Camunda tenant behavior is preserved; this change does not add tenant support to workflows.

Review: a read-only reviewer found the signed-subset expansion issue; fixed with intersection and regression assertions. Initial focused regressions passed (87 tests). Final smart-ai suite passed: 311 tests, 0 failures, 0 errors, 0 skipped. Command: JDK8 `mvn -o -q -pl smart-ai -am test '-Dtest=com.smart.ai.**.*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false'`. Log: `smart-boot/ai-query-permission-tests.log`. `git diff --check` passed. Configuration context tests explicitly register the policy dependency. Manual UI/database acceptance remains pending; code is uncommitted on existing branches.

## 人工验证步骤

1. 在业务库执行初始化脚本，部署并重启 smart-boot；Agent 和 Web 无需重新部署。
2. 用没有项目/合同菜单的普通账号，配置值为 `1` 时确认原有无权限提示。
3. 把配置值改为 `0`，重新提问项目、合同等业务查询及详情，确认可以读取原权限范围外的数据。
4. 查询“我的待办”应仍是本人；查询“所有人的待办”或指定人员时允许查询对应范围，详情和流程图可查看。
5. 恢复 `1` 后重新提问应恢复权限限制；旧放开上下文请求应提示查询权限已更新。
6. 普通账号的模型、知识库、问题记录管理权限不应因开关关闭而增加。

下一步：按以上步骤验收该开关；此前暂停的其他开发任务未自动恢复。
