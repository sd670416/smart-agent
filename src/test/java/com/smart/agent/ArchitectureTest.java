package com.smart.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.smart.agent")
class ArchitectureTest {
    @ArchTest
    static final ArchRule toolContractsMustNotDependOnWebOrJpa = noClasses()
            .that().haveSimpleName("AgentTool")
            .or().haveSimpleName("ToolContext")
            .or().haveSimpleName("VectorIndex")
            .or().haveSimpleName("ModelGateway")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule controllersOnlyCallUseCases = classes()
            .that().resideInAPackage("..chat..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "jakarta.validation..", "org.springframework..", "..chat..", "..security..");
}
