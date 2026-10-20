package com.smart.agent.tool.company;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.CompanyOperatingDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class CompanyOperatingClarifyDateTool implements AgentTool<CompanyOperatingClarifyDateTool.Input,ClarificationPrompt> {
    public record Input() {}
    @Override public String key(){return "companyOperating.clarifyDate";}
    @Override public Class<Input> inputType(){return Input.class;}
    @Override public String requiredPermission(){return "menu:project";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "公司经营查询日期含义不清时先确认，仅支持按项目创建日期筛选后查当前累计；不读取金额。";}
    @Override public ClarificationPrompt execute(Input input,ToolContext context) {
        if(!context.permissions().contains("menu:project"))throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"当前账号尚未开通公司项目查看权限");
        return new ClarificationPrompt("目前可以筛选这段时间创建的项目，查看它们在各自持公司下的当前累计数据。您希望如何查询？","DATE_FIELD",List.of(
                new ClarificationPrompt.Option("date:projectCreateDate","按项目创建日期筛选",CompanyOperatingDomainContributor.CODE),
                new ClarificationPrompt.Option("date:allProjectDates","不限定项目日期，查看当前累计",CompanyOperatingDomainContributor.CODE)));
    }
}
