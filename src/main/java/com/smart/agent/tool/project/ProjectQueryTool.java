package com.smart.agent.tool.project;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class ProjectQueryTool implements AgentTool<ProjectQueryInput, ProjectQueryResult> {
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "select":{"type":"array","description":"要返回的项目业务字段。普通项目概况默认不传；如需指定，可用 projectName、projectCode、organizationName、projectNature、affiliatedCompanyName、projectType、constructionCompany、personInChargeName、projectStatus、projectBudget、createDate","items":{"type":"string"}},
              "filter":{"type":"object","properties":{
                "logic":{"type":"string"},
                "conditions":{"type":"array","items":{"type":"object","properties":{"field":{"type":"string"},"operator":{"type":"string"},"value":{}},"required":["field","operator"],"additionalProperties":false}},
                "groups":{"type":"array","items":{"type":"object","properties":{"logic":{"type":"string"},"conditions":{"type":"array","items":{"type":"object","properties":{"field":{"type":"string"},"operator":{"type":"string"},"value":{}},"required":["field","operator"],"additionalProperties":false}}},"additionalProperties":false}}
              },"additionalProperties":false},
              "groupBy":{"type":"array","items":{"type":"string"}},
              "aggregations":{"type":"array","items":{"type":"object","properties":{"function":{"type":"string"},"field":{"type":"string"},"alias":{"type":"string"}},"required":["function"],"additionalProperties":false}},
              "orderBy":{"type":"array","items":{"type":"object","properties":{"field":{"type":"string"},"direction":{"type":"string"}},"required":["field"],"additionalProperties":false}},
              "page":{"type":"integer"},"pageSize":{"type":"integer"}
            },"additionalProperties":false}
            """;

    private final ProjectBusinessClient client;

    public ProjectQueryTool(ProjectBusinessClient client) { this.client = client; }
    @Override public String key() { return "project.query"; }
    @Override public Class<ProjectQueryInput> inputType() { return ProjectQueryInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "按任意项目业务字段组合筛选、排序、分页、分组和统计当前用户有权访问的项目；不支持系统字段";
    }
    @Override public String argumentsSchemaJson() { return SCHEMA; }
    @Override public ProjectQueryResult execute(ProjectQueryInput input, ToolContext context) {
        return client.query(context, input);
    }
}
