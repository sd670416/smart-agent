package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Spring-collected registry for extensible business domains and their tools. */
@Component
public class BusinessDomainRegistry {
    private final Map<String, BusinessDomainContributor> byCode;
    private final Map<String, BusinessDomainDescriptor> byTool;

    public BusinessDomainRegistry(Collection<BusinessDomainContributor> contributors) {
        Map<String, BusinessDomainContributor> codes = new LinkedHashMap<>();
        Map<String, BusinessDomainDescriptor> tools = new LinkedHashMap<>();
        if (contributors != null) {
            for (BusinessDomainContributor contributor : contributors) {
                if (contributor == null) continue;
                BusinessDomainDescriptor descriptor = contributor.descriptor();
                if (codes.putIfAbsent(descriptor.code(), contributor) != null) {
                    throw new IllegalStateException("重复业务域编码：" + descriptor.code());
                }
                for (String toolKey : descriptor.toolKeys()) {
                    if (toolKey == null || toolKey.isBlank()) {
                        throw new IllegalStateException("业务域工具编码不能为空：" + descriptor.code());
                    }
                    if (tools.putIfAbsent(toolKey, descriptor) != null) {
                        throw new IllegalStateException("工具已归属于多个业务域：" + toolKey);
                    }
                }
            }
        }
        byCode = Collections.unmodifiableMap(codes);
        byTool = Collections.unmodifiableMap(tools);
    }

    public Optional<BusinessDomainDescriptor> findDomain(String code) {
        BusinessDomainContributor contributor = byCode.get(code);
        return contributor == null ? Optional.empty() : Optional.of(contributor.descriptor());
    }

    public Optional<BusinessDomainDescriptor> findByTool(String toolKey) {
        return Optional.ofNullable(byTool.get(toolKey));
    }

    public List<BusinessDomainDescriptor> availableDomains(AgentUserContext context) {
        return byCode.values().stream()
                .filter(contributor -> contributor.availableTo(context))
                .map(BusinessDomainContributor::descriptor)
                .toList();
    }

    public List<BusinessDomainDescriptor> allDomains() {
        return byCode.values().stream().map(BusinessDomainContributor::descriptor).toList();
    }
}
