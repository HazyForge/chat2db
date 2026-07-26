package ai.chat2db.community.domain.api.model.datasource;

import lombok.Data;

/** Desired configuration for one controller-managed datasource. */
@Data
public class ManagedDataSourceSpec {

    private String managementKey;

    private String alias;

    private String type;

    private String host;

    private String port;

    private String database;

    private String user;

    private String password;

    private String authenticationType;

    private Long environmentId;

    private String url;
}
