package com.smart.agent.tool.company;
import com.smart.agent.routing.CompanyOperatingDomainContributor;
import java.time.LocalDate;
import java.util.Set;
public record CompanyOperatingQueryInput(String projectId,String projectCode,String projectName,
        String companyId,String companyName,String metric,String mode,String dateField,String startDate,String endDate,Integer page,Integer pageSize) {
    public CompanyOperatingQueryInput {
        metric=metric==null?"all":metric;mode=mode==null?"summary":mode;
        CompanyOperatingDomainContributor.permissions(metric);
        if(!Set.of("summary","detail","companies").contains(mode))throw new IllegalArgumentException("Unsupported mode");
        if("companies".equals(mode)&&!"projects".equals(metric))throw new IllegalArgumentException("Company lookup requires projects metric");
        for(String identifier:new String[]{projectId,projectCode,projectName,companyId,companyName})
            if(identifier!=null && identifier.isBlank())throw new IllegalArgumentException("Empty identifier");
        page=page==null?1:page;pageSize=pageSize==null?20:pageSize;
        if(page<1||pageSize<1||pageSize>100)throw new IllegalArgumentException("Invalid pagination");
        if(dateField!=null&&!"projectCreateDate".equals(dateField))throw new IllegalArgumentException("Unsupported date field");
        if(startDate!=null||endDate!=null) {
            if(!"projectCreateDate".equals(dateField)||startDate==null||endDate==null)throw new IllegalArgumentException("Confirm project creation date range");
            if(LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))throw new IllegalArgumentException("Reversed date range");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public CompanyOperatingQueryInput withProjectId(String id) {return new CompanyOperatingQueryInput(id,null,null,companyId,companyName,metric,mode,dateField,startDate,endDate,page,pageSize);}
    public CompanyOperatingQueryInput withCompanyId(String id) {return new CompanyOperatingQueryInput(projectId,projectCode,projectName,id,null,metric,mode,dateField,startDate,endDate,page,pageSize);}
    public CompanyOperatingQueryInput lookup() {return new CompanyOperatingQueryInput(projectId,projectCode,projectName,companyId,companyName,"projects","companies",dateField,startDate,endDate,1,100);}
}
