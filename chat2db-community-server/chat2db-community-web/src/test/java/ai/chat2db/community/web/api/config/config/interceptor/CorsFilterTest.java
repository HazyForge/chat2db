package ai.chat2db.community.web.api.config.web.interceptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsFilterTest {

    @Test
    void defaultsAllowLocalOriginsAndMissingOrigin() {
        CorsFilter filter = new CorsFilter("");

        assertTrue(filter.allowCommunityOrigin(null));
        assertTrue(filter.allowCommunityOrigin("http://127.0.0.1:8888"));
        assertTrue(filter.allowCommunityOrigin("http://localhost:10825"));

        assertFalse(filter.allowCommunityOrigin(""));
        assertFalse(filter.allowCommunityOrigin("https://example.com"));
        assertFalse(filter.allowCommunityOrigin("http://127.0.0.1:3000"));
    }

    @Test
    void configuredHttpsOriginIsAllowedExactly() {
        CorsFilter filter = new CorsFilter("https://chat2db.hazyforge.io");

        assertTrue(filter.allowCommunityOrigin("https://chat2db.hazyforge.io"));
    }

    @Test
    void configuredOriginDoesNotAllowLookalikes() {
        CorsFilter filter = new CorsFilter("https://chat2db.hazyforge.io");

        assertFalse(filter.allowCommunityOrigin("http://chat2db.hazyforge.io"));
        assertFalse(filter.allowCommunityOrigin("https://sub.chat2db.hazyforge.io"));
        assertFalse(filter.allowCommunityOrigin("https://chat2db.hazyforge.io.attacker.example"));
        assertFalse(filter.allowCommunityOrigin("https://chat2db.hazyforge.io:8443"));
        assertFalse(filter.allowCommunityOrigin("https://chat2db.hazyforge.io/"));
        assertFalse(filter.allowCommunityOrigin("https://CHAT2DB.hazyforge.io"));
    }

    @Test
    void blankRequestOriginIsRejected() {
        CorsFilter filter = new CorsFilter("https://chat2db.hazyforge.io");

        assertFalse(filter.allowCommunityOrigin(""));
        assertFalse(filter.allowCommunityOrigin(" "));
    }

    @Test
    void malformedConfiguredOriginFailsConstruction() {
        String[] malformedOrigins = {
                "http://chat2db.hazyforge.io",
                "chat2db.hazyforge.io",
                "https://chat2db.hazyforge.io/",
                "https://chat2db.hazyforge.io/path",
                "https://chat2db.hazyforge.io?query=true",
                "https://chat2db.hazyforge.io#fragment",
                "https://user@chat2db.hazyforge.io",
                " https://chat2db.hazyforge.io",
                "https://chat2db.hazyforge.io ",
                "https://chat2db.hazyforge.io,https://attacker.example",
                "https://chat2db.hazyforge.io:65536"
        };

        for (String origin : malformedOrigins) {
            assertThrows(IllegalArgumentException.class, () -> new CorsFilter(origin), origin);
        }
    }
}
