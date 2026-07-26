package ai.chat2db.community.start.bootstrap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat2db.bootstrap.datasource")
public class ManagedDataSourceBootstrapProperties {

    private boolean enabled;

    private String managementKey;

    private String alias;

    private String type = "POSTGRESQL";

    private String host;

    private int port = 5432;

    private String database;

    private String user;

    private String authenticationType = "1";

    private Long environmentId = 2L;

    private String sslMode = "verify-full";

    private String passwordFile;

    private String caFile;
}
