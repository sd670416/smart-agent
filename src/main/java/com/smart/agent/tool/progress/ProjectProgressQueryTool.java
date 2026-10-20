package com.smart.agent.tool.progress;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectProgressDomainContributor;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProjectProgressQueryTool implements AgentTool<ProjectProgressQueryInput,Object> {
    private final ProjectProgressBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectProgressQueryTool(ProjectProgressBusinessClient client, ProjectReferenceResolver projects) {
        this.client=client; this.projects=projects;
    }
    public String key(){return "projectProgress.query";}
    public Class<ProjectProgressQueryInput> inputType(){return ProjectProgressQueryInput.class;}
    public String requiredPermission(){return "menu:project";}
    public ToolRisk risk(){return ToolRisk.L1;}
    public String description(){return "实时查询审批完成且有效项目的当前施工进度。summary按项目计算平均进度，detail按项目分页，tasks返回含子任务的任务明细。"
        +"项目名称精确匹配，同名先确认。序号仅用于定位历史列表中的真实projectId，再重新查询；不能把项目编号当ID。"
        +"仅支持按项目创建日期选择项目，再查询当前进度，不是历史进度快照；日期含义不明先调用projectProgress.clarifyDate。"
        +"不支持同比环比或两组比较；待办、已办、我发起使用approval。";}
    public String argumentsSchemaJson(){return """
        {"type":"object","properties":{
        "projectId":{"type":"string","description":"真实项目ID，不是项目编号"},
        "projectName":{"type":"string","description":"精确项目名称，同名先确认"},
        "mode":{"type":"string","enum":["summary","detail","tasks"]},
        "dateField":{"type":"string","enum":["projectCreateDate"]},
        "startDate":{"type":"string"},"endDate":{"type":"string"},
        "page":{"type":"integer","minimum":1},"pageSize":{"type":"integer","minimum":1,"maximum":100}},"additionalProperties":false}
        """;}
    public Object execute(ProjectProgressQueryInput input,ToolContext context){
        if(input.projectId()==null && input.projectName()==null)return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),null,input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查看哪一个项目的施工进度：","PROJECT_REFERENCE",
            matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),
                p.projectName()+"（编号："+p.projectCode()+"）",ProjectProgressDomainContributor.CODE)).toList());
        String id=matches.getFirst().projectId();
        var result=client.query(context,new ProjectProgressQueryInput(id,null,input.mode(),input.dateField(),input.startDate(),input.endDate(),input.page(),input.pageSize()));
        var filters=new ArrayList<>(result.appliedFilters());filters.add(Map.of("field","projectId","op","eq","value",id));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
