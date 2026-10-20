package com.smart.agent.tool.progress;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectProgressDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectProgressClarifyDateTool implements AgentTool<ProjectProgressClarifyDateTool.Input,ClarificationPrompt> {
    public record Input() {}
    public String key(){return "projectProgress.clarifyDate";}
    public Class<Input> inputType(){return Input.class;}
    public String requiredPermission(){return "menu:project";}
    public ToolRisk risk(){return ToolRisk.L1;}
    public String description(){return "施工进度查询日期含义不清时先确认，不读取进度。仅支持按项目创建日期选择项目后查看当前进度，不支持历史进度快照或按任务日期统计。";}
    public ClarificationPrompt execute(Input input,ToolContext context){
        return new ClarificationPrompt("目前可以筛选这段时间创建的项目，再查看这些项目的当前施工进度，并非当时的历史进度。您希望如何查询？","DATE_FIELD",List.of(
            new ClarificationPrompt.Option("date:projectCreateDate","按项目创建日期筛选",ProjectProgressDomainContributor.CODE),
            new ClarificationPrompt.Option("date:allProjectDates","不限定日期，查看当前施工进度",ProjectProgressDomainContributor.CODE)));
    }
}
