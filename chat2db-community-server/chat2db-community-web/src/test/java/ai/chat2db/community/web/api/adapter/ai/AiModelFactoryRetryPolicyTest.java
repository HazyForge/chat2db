package ai.chat2db.community.web.api.adapter.ai;

import ai.chat2db.community.domain.api.model.ai.AiRuntimeModel;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModelFactoryRetryPolicyTest {

    @Test
    void streamingRequestsAttemptExactlyOnce() {
        assertEquals(1, countAttempts(AiModelFactory.createRetryTemplate(
                AiModelFactory.RequestMode.STREAMING)));
    }

    @Test
    void synchronousRequestsPreserveTheDefaultRetryPolicy() {
        int defaultAttempts = countAttempts(RetryTemplate.defaultInstance());

        assertEquals(3, defaultAttempts);
        assertEquals(defaultAttempts, countAttempts(AiModelFactory.createRetryTemplate(
                AiModelFactory.RequestMode.SYNCHRONOUS)));
    }

    @Test
    void validatedServerPresetCanConstructOpenAiClient() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            AiRuntimeModel runtime = new AiRuntimeModel();
            runtime.setProvider("OPENAI");
            runtime.setModel("gpt-5.4");
            runtime.setApiKey("proxy-client-key");
            runtime.setBaseUrl("https://codex.hazycloud.io");
            runtime.setSystemPreset(true);

            assertNotNull(new AiModelFactory(context).create(runtime, AiModelFactory.RequestMode.STREAMING));
        }
    }

    private int countAttempts(RetryTemplate retryTemplate) {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> retryTemplate.execute(context -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("retry policy test");
        }));
        return attempts.get();
    }
}
