package com.smart.agent.knowledge.manage;

public record CreateKnowledgeSpaceCommand(String name, String description, KnowledgeScope scope, String projectId) {}
