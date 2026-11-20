package com.smart.agent.tool.projectrisk;
import java.util.List;
public record ProjectRiskQueryInput(String projectId,String projectCode,String projectName,List<String> dimensions,
        String level,String mode,String dateMode,String dateField,String startDate,String endDate,Integer page,Integer pageSize) {
    public ProjectRiskQueryInput withProjectId(String id){return new ProjectRiskQueryInput(id,null,null,dimensions,level,mode,dateMode,dateField,startDate,endDate,page,pageSize);}
}
