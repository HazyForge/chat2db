package ai.chat2db.community.domain.core.impl.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelConfigServiceImplConfiguredModelsTest {

    private static final List<String> FALLBACK = List.of("gpt-default");

    @Test
    void usesFallbackWhenConfigurationIsMissing() {
        assertEquals(FALLBACK, AiModelConfigServiceImpl.configuredModels(null, FALLBACK));
        assertEquals(FALLBACK, AiModelConfigServiceImpl.configuredModels("   ", FALLBACK));
    }

    @Test
    void parsesTrimsAndDeduplicatesConfiguredModels() {
        assertEquals(List.of("gpt-5.4", "gpt-5-mini"),
                AiModelConfigServiceImpl.configuredModels(" gpt-5.4, gpt-5-mini, gpt-5.4 ", FALLBACK));
    }

    @Test
    void ignoresEmptyItems() {
        assertEquals(List.of("gpt-5.4"),
                AiModelConfigServiceImpl.configuredModels(",,gpt-5.4,,", FALLBACK));
    }
}
