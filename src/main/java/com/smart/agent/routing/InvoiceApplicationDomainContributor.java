package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InvoiceApplicationDomainContributor implements BusinessDomainContributor {
    public static final String CODE="INVOICE_APPLICATION";
    public static final String PERMISSION="menu:finance:invoice-application";
    private static final List<String> TERMS=List.of("开票申请","申请开票");
    private static final BusinessDomainDescriptor DESCRIPTOR=new BusinessDomainDescriptor(CODE,"开票申请",
            List.of("invoiceApplication.query","invoiceApplication.clarifyDate"),
            List.of(new DataScopeDescriptor("FINANCE_MENU","开票申请菜单及可见项目范围",List.of("MENU","PROJECT_MEMBER"))),"REALTIME",TERMS);
    public static boolean matches(String question){return question!=null && TERMS.stream().anyMatch(question::contains);}
    @Override public BusinessDomainDescriptor descriptor(){return DESCRIPTOR;}
    @Override public boolean availableTo(AgentUserContext context){return context!=null && context.permissions().contains(PERMISSION);}
}
