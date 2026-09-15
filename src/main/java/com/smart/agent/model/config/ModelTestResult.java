package com.smart.agent.model.config;

import java.time.Instant;
import java.util.List;

/** 模型兼容性测试的脱敏结果。 */
public record ModelTestResult(String modelId, String status, Instant testedAt, List<Item> items) {

    public record Item(String testItem, String status, Integer httpStatus, long durationMillis, String message) {
    }
}
