package com.smart.agent.model.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.knowledge.manage.PageResult;
import com.smart.agent.security.AgentUserContext;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelConfigControllerTest {
    private static final String CONTEXT = AgentUserContext.class.getName();

    private final ModelConfigService service = mock(ModelConfigService.class);
    private final ModelCompatibilityTester tester = mock(ModelCompatibilityTester.class);
    private final AgentUserContext actor = new AgentUserContext("tenant-1", "user-1", "identity-1",
            Set.of(ModelConfigService.PERMISSION_MENU), Set.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(new ModelConfigController(service, tester))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void returnsPagedModelsWithoutAnySecretField() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageResult<>(List.of(sample()), 1, 0, 20));

        mvc.perform(get("/agent/models/page").requestAttr(CONTEXT, actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].name").value("智谱"))
                .andExpect(jsonPath("$.items[0].hasApiKey").value(true))
                .andExpect(jsonPath("$.items[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].encryptedApiKey").doesNotExist());
    }

    @Test
    void returnsDetailWithMaskedApiKey() throws Exception {
        when(service.detail("model-1", actor)).thenReturn(sample());
        when(service.apiKeyMask(any())).thenReturn("sk-****klmn");

        mvc.perform(get("/agent/models/{id}", "model-1").requestAttr(CONTEXT, actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyMask").value("sk-****klmn"))
                .andExpect(jsonPath("$.encryptedApiKey").doesNotExist());
    }

    @Test
    void exposesEnabledSummariesWithoutEndpointOrSecrets() throws Exception {
        when(service.enabledModels()).thenReturn(List.of(sample()));

        mvc.perform(get("/agent/models/enabled").requestAttr(CONTEXT, actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("智谱"))
                .andExpect(jsonPath("$[0].defaultModel").value(false))
                .andExpect(jsonPath("$[0].baseUrl").doesNotExist())
                .andExpect(jsonPath("$[0].hasApiKey").doesNotExist());
    }

    @Test
    void mapsDomainFailuresToStableErrorCodes() throws Exception {
        when(service.detail("missing", actor)).thenThrow(new ModelConfigNotFoundException("missing"));
        mvc.perform(get("/agent/models/{id}", "missing").requestAttr(CONTEXT, actor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_NOT_FOUND"));

        when(service.detail("forbidden", actor)).thenThrow(new ModelConfigForbiddenException("aiModel:menu"));
        mvc.perform(get("/agent/models/{id}", "forbidden").requestAttr(CONTEXT, actor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_FORBIDDEN"));

        when(service.setEnabled(any(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("default model must remain enabled"));
        mvc.perform(put("/agent/models/{id}/enabled", "model-1").requestAttr(CONTEXT, actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_STATE_CONFLICT"));

        when(service.setDefault(any(), any()))
                .thenThrow(new IllegalArgumentException("云端模型必须使用 https 服务地址"));
        mvc.perform(put("/agent/models/{id}/default", "model-1").requestAttr(CONTEXT, actor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_INVALID"));

        when(service.apiKeyMask(any())).thenThrow(new ModelSecretException("历史 API Key 无法读取，请重新录入"));
        when(service.detail("secret", actor)).thenReturn(sample());
        mvc.perform(get("/agent/models/{id}", "secret").requestAttr(CONTEXT, actor))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_SECRET_UNAVAILABLE"));
    }

    @Test
    void rejectsUnknownRequestFieldsAndMissingEnabledFlag() throws Exception {
        mvc.perform(post("/agent/models").requestAttr(CONTEXT, actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权字段\",\"deploymentType\":\"LOCAL\","
                                + "\"baseUrl\":\"http://127.0.0.1:11434/v1\",\"modelName\":\"local\",\"tenantId\":\"tenant-2\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/agent/models/{id}/enabled", "model-1").requestAttr(CONTEXT, actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_INVALID"));
    }

    @Test
    void deletesAndReturnsNoContent() throws Exception {
        mvc.perform(delete("/agent/models/{id}", "model-1").requestAttr(CONTEXT, actor))
                .andExpect(status().isNoContent());
    }

    @Test
    void testsModelWithOptionalImageAttachment() throws Exception {
        ModelConfig config = sample();
        when(service.testTarget("model-1", actor)).thenReturn(config);
        when(tester.test(config, "attachment-1", actor)).thenReturn(
                new ModelTestResult("model-1", "PASSED", java.time.Instant.now(), List.of(
                        new ModelTestResult.Item("BASIC", "PASSED", 200, 12, "测试通过"))));

        mvc.perform(post("/agent/models/{id}/test", "model-1").requestAttr(CONTEXT, actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageAttachmentId\":\"attachment-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.items[0].testItem").value("BASIC"));
    }

    private static ModelConfig sample() {
        return ModelConfig.create("智谱", ModelProviderType.OPENAI_COMPATIBLE, ModelDeploymentType.CLOUD,
                "https://api.example.com/v1", "glm-4", "v1:encrypted-payload",
                Set.of(ModelCapability.STREAMING, ModelCapability.TOOL_CALLING),
                Duration.ofSeconds(10), Duration.ofSeconds(60), 1, "测试备注");
    }
}
