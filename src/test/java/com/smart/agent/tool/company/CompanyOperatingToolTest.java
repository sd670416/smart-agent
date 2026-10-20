package com.smart.agent.tool.company;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.CompanyOperatingDomainContributor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanyOperatingToolTest {
    private final ToolContext context=new ToolContext("t","u","i",Set.of(),Set.of("p"),Set.copyOf(CompanyOperatingDomainContributor.permissions("all")));
    private CompanyOperatingQueryInput input(String name){return new CompanyOperatingQueryInput(null,null,null,null,name,"all","summary",null,null,null,1,20);}
    private ProjectQueryResult result(List<Map<String,Object>> rows){return new ProjectQueryResult("detail",1,20,(long)rows.size(),false,List.of(),rows,List.of());}
    @Test void sameNameCompanyMustClarifyBeforeQueryingMoney() {
        var tool=new CompanyOperatingQueryTool((ctx,q)->{
            assertEquals("companies",q.mode());assertEquals("projects",q.metric());
            return result(List.of(Map.of("companyId","c1","companyName","公司","companyCode","001"),Map.of("companyId","c2","companyName","公司","companyCode","002")));
        },null);
        var prompt=assertInstanceOf(ClarificationPrompt.class,tool.execute(input("公司"),context));
        assertEquals("COMPANY_REFERENCE",prompt.ambiguityType());assertEquals(2,prompt.options().size());
    }
    @Test void uniqueCompanyUsesCanonicalIdentityAndQueriesAgainEveryTime() {
        var calls=new ArrayList<CompanyOperatingQueryInput>();
        var tool=new CompanyOperatingQueryTool((ctx,q)->{calls.add(q);return "companies".equals(q.mode())
                ?result(List.of(Map.of("companyId","c1","companyName","公司","companyCode","001"))):result(List.of(Map.of("companyId","c1","receivedAmount",12)));},null);
        tool.execute(input("公司"),context);tool.execute(input("公司"),context);
        assertEquals(4,calls.size());assertEquals("c1",calls.get(1).companyId());assertNull(calls.get(1).companyName());
    }
    @Test void sourcePermissionsAreCheckedBeforeAnyRequest() {
        var tool=new CompanyOperatingQueryTool((ctx,q)->{throw new AssertionError("must not read");},null);
        assertThrows(com.smart.agent.common.error.AgentException.class,()->tool.execute(input(null),new ToolContext("t","u","i",Set.of(),Set.of("p"),Set.of("menu:project"))));
    }
    @Test void inputRejectsUnsupportedMetricsAndUnconfirmedDate() {
        assertThrows(IllegalArgumentException.class,()->new CompanyOperatingQueryInput(null,null,null,null,null,"profit","summary",null,null,null,1,20));
        assertThrows(IllegalArgumentException.class,()->new CompanyOperatingQueryInput(null,null,null,null,null,"all","summary",null,"2026-01-01","2026-01-31",1,20));
    }
}
