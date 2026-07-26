package ai.chat2db.community.domain.core.impl.ai;

import ai.chat2db.community.domain.api.model.request.ai.AiChatRuntimeResolveRequest;
import ai.chat2db.community.domain.core.converter.AiModelConfigConverter;
import ai.chat2db.community.tools.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelConfigServiceImplServerPresetTest {

    @TempDir
    Path tempDirectory;

    @Test
    void completeConfigurationExposesAndResolvesOnlyDeclaredPreset() {
        AiModelConfigServiceImpl service = service(configuration("https://codex.hazycloud.io/v1"));

        var options = service.listModelOptions();
        assertEquals(List.of("preset:OPENAI:gpt-5.4"),
                options.stream().map(option -> option.getValue()).toList());
        assertEquals("preset:OPENAI:gpt-5.4", options.get(0).getModelConfigId());

        var runtime = service.resolveRuntimeModel(preset("preset:OPENAI:gpt-5.4"));
        assertEquals("OPENAI", runtime.getProvider());
        assertEquals("gpt-5.4", runtime.getModel());
        assertEquals("proxy-client-key", runtime.getApiKey());
        assertEquals("https://codex.hazycloud.io", runtime.getBaseUrl());
        assertTrue(runtime.isSystemPreset());
        assertFalse(Files.exists(tempDirectory.resolve("ai-model-configs.json")));
    }

    @Test
    void undeclaredOrMalformedPresetFailsClosed() {
        AiModelConfigServiceImpl service = service(configuration("https://codex.hazycloud.io"));

        assertThrows(BusinessException.class,
                () -> service.resolveRuntimeModel(preset("preset:OPENAI:gpt-unapproved")));
        assertThrows(BusinessException.class,
                () -> service.resolveRuntimeModel(preset("preset:OPENAI")));
    }

    @Test
    void declaredPresetRejectsEveryUpstreamOrModelOverride() {
        AiModelConfigServiceImpl service = service(configuration("https://codex.hazycloud.io"));

        AiChatRuntimeResolveRequest baseUrlOverride = preset("preset:OPENAI:gpt-5.4");
        baseUrlOverride.setBaseUrl("https://attacker.example");
        assertThrows(BusinessException.class, () -> service.resolveRuntimeModel(baseUrlOverride));

        AiChatRuntimeResolveRequest modelOverride = preset("preset:OPENAI:gpt-5.4");
        modelOverride.setModel("gpt-unapproved");
        assertThrows(BusinessException.class, () -> service.resolveRuntimeModel(modelOverride));

        AiChatRuntimeResolveRequest providerOverride = preset("preset:OPENAI:gpt-5.4");
        providerOverride.setProvider("CLAUDE");
        assertThrows(BusinessException.class, () -> service.resolveRuntimeModel(providerOverride));

        AiChatRuntimeResolveRequest keyOverride = preset("preset:OPENAI:gpt-5.4");
        keyOverride.setApiKey("browser-supplied-key");
        assertThrows(BusinessException.class, () -> service.resolveRuntimeModel(keyOverride));
    }

    @Test
    void directKeylessRequestCannotBorrowServerCredential() {
        AiChatRuntimeResolveRequest request = new AiChatRuntimeResolveRequest();
        request.setProvider("OPENAI");
        request.setModel("gpt-5.4");

        assertThrows(IllegalArgumentException.class,
                () -> service(configuration("https://codex.hazycloud.io")).resolveRuntimeModel(request));
    }

    @Test
    void partialOrUnsafeConfigurationFailsConstruction() {
        assertThrows(IllegalStateException.class,
                () -> service(Map.of("OPENAI_API_KEY", "proxy-client-key")));
        assertThrows(IllegalStateException.class,
                () -> service(configuration("http://codex.hazycloud.io")));
        assertThrows(IllegalStateException.class,
                () -> service(configuration("https://user@codex.hazycloud.io/v1")));
    }

    private AiChatRuntimeResolveRequest preset(String id) {
        AiChatRuntimeResolveRequest request = new AiChatRuntimeResolveRequest();
        request.setModelConfigId(id);
        return request;
    }

    private Map<String, String> configuration(String baseUrl) {
        return Map.of(
                "CHAT2DB_OPENAI_MODELS", "gpt-5.4",
                "OPENAI_API_KEY", "proxy-client-key",
                "OPENAI_BASE_URL", baseUrl);
    }

    private AiModelConfigServiceImpl service(Map<String, String> environment) {
        return new AiModelConfigServiceImpl(new ObjectMapper().findAndRegisterModules(), new AiModelConfigConverter(),
                () -> 42L, tempDirectory.resolve("ai-model-configs.json"), null, environment::get);
    }
}
