package com.smart.agent.tool.cost;
import java.time.LocalDate;
import java.util.Set;
import com.smart.agent.routing.CostContractDomainContributor;

public record CostContractQueryInput(String projectId,String projectCode,String projectName,String source,
        String mode,String contractId,String contractName,String dateField,String startDate,String endDate,
        Integer page,Integer pageSize) {
    public CostContractQueryInput {
        source=source==null?"all":source; mode=mode==null?"summary":mode;
        CostContractDomainContributor.permissions(source);
        if(!Set.of("summary","detail").contains(mode))throw new IllegalArgumentException("Unsupported mode");
        if(contractId!=null && "all".equals(source))throw new IllegalArgumentException("Confirm contract source");
        page=page==null?1:page;pageSize=pageSize==null?20:pageSize;
        if(page<1 || pageSize<1 || pageSize>100)throw new IllegalArgumentException("Invalid pagination");
        if(dateField!=null && !"projectCreateDate".equals(dateField))throw new IllegalArgumentException("Unsupported date field");
        if(startDate!=null || endDate!=null) {
            if(!"projectCreateDate".equals(dateField)||startDate==null||endDate==null)throw new IllegalArgumentException("Confirm project creation date range");
            if(LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))throw new IllegalArgumentException("Reversed date range");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public CostContractQueryInput withProjectId(String id) {
        return new CostContractQueryInput(id,null,null,source,mode,contractId,contractName,dateField,startDate,endDate,page,pageSize);
    }
}
