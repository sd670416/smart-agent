package com.smart.agent.tool.invoice;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.InvoiceApplicationDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InvoiceApplicationClarifyDateTool implements AgentTool<InvoiceApplicationClarifyDateTool.Input,ClarificationPrompt> {
    public record Input() {}
    @Override public String key(){return "invoiceApplication.clarifyDate";}
    @Override public Class<Input> inputType(){return Input.class;}
    @Override public String requiredPermission(){return InvoiceApplicationDomainContributor.PERMISSION;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "开票申请查询日期含义不明确时，提供项目创建日期、申请创建日期供用户确认；不读取业务数据。";}
    @Override public ClarificationPrompt execute(Input input,ToolContext context){
        return new ClarificationPrompt("您希望按哪个日期筛选开票申请？","DATE_FIELD",List.of(
                new ClarificationPrompt.Option("date:projectCreateDate","项目创建日期",InvoiceApplicationDomainContributor.CODE),
                new ClarificationPrompt.Option("date:createDate","申请创建日期",InvoiceApplicationDomainContributor.CODE)));
    }
}
