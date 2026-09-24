package com.smart.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessDomainRegistryTest {
    @Test
    void findsDomainByCodeAndToolWithoutFixedEnum() {
        BusinessDomainContributor custom = () -> new BusinessDomainDescriptor(
                "CONTRACT", "合同", List.of("contract.query"), List.of(), "REALTIME");
        BusinessDomainRegistry registry = new BusinessDomainRegistry(List.of(custom));

        assertEquals("CONTRACT", registry.findDomain("CONTRACT").orElseThrow().code());
        assertEquals("CONTRACT", registry.findByTool("contract.query").orElseThrow().code());
    }

    @Test
    void rejectsDuplicateDomainCodeAndToolOwnership() {
        BusinessDomainContributor first = () -> descriptor("A", "tool.one");
        BusinessDomainContributor duplicateCode = () -> descriptor("A", "tool.two");
        BusinessDomainContributor duplicateTool = () -> descriptor("B", "tool.one");

        assertThrows(IllegalStateException.class,
                () -> new BusinessDomainRegistry(List.of(first, duplicateCode)));
        assertThrows(IllegalStateException.class,
                () -> new BusinessDomainRegistry(List.of(first, duplicateTool)));
    }

    @Test
    void exposesOnlyContributorsAvailableToTheContext() {
        BusinessDomainContributor available = () -> descriptor("A", "tool.one");
        BusinessDomainContributor unavailable = new BusinessDomainContributor() {
            @Override public BusinessDomainDescriptor descriptor() { return descriptor("B", "tool.two"); }
            @Override public boolean availableTo(com.smart.agent.security.AgentUserContext context) { return false; }
        };
        BusinessDomainRegistry registry = new BusinessDomainRegistry(List.of(available, unavailable));

        assertEquals(1, registry.availableDomains(null).size());
        assertTrue(registry.availableDomains(null).get(0).code().equals("A"));
    }

    private static BusinessDomainDescriptor descriptor(String code, String tool) {
        return new BusinessDomainDescriptor(code, code, List.of(tool), List.of(), "REALTIME");
    }
}
