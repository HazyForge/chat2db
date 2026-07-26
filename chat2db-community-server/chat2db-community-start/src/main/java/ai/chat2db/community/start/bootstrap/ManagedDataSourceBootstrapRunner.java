package ai.chat2db.community.start.bootstrap;

import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceReconcileResult;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceSpec;
import ai.chat2db.community.domain.api.service.db.IManagedDataSourceReconciler;
import ai.chat2db.community.tools.annotation.CommunityRuntimeOnly;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@Slf4j
@Component
@CommunityRuntimeOnly
@ConditionalOnProperty(name = "chat2db.bootstrap.datasource.enabled", havingValue = "true")
public class ManagedDataSourceBootstrapRunner implements ApplicationRunner {

    private static final long MAX_PASSWORD_BYTES = 64 * 1024;
    private static final long MAX_CA_BYTES = 1024 * 1024;

    private final ManagedDataSourceBootstrapProperties properties;
    private final IManagedDataSourceReconciler reconciler;

    public ManagedDataSourceBootstrapRunner(ManagedDataSourceBootstrapProperties properties,
            IManagedDataSourceReconciler reconciler) {
        this.properties = properties;
        this.reconciler = reconciler;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ManagedDataSourceSpec spec = buildSpec();
            ManagedDataSourceReconcileResult result = reconciler.reconcile(spec);
            log.info("Managed datasource reconciliation action={} id={} key={}",
                    result.getAction(), result.getDataSourceId(), properties.getManagementKey());
        } catch (RuntimeException exception) {
            log.error("Managed datasource reconciliation failed for key={}", safeManagementKey());
            throw new IllegalStateException("Managed datasource bootstrap failed");
        }
    }

    ManagedDataSourceSpec buildSpec() {
        validateProperties();
        Path passwordPath = validatedPath(properties.getPasswordFile(), MAX_PASSWORD_BYTES, "password");
        Path caPath = validatedPath(properties.getCaFile(), MAX_CA_BYTES, "CA certificate");
        String password = readPassword(passwordPath);
        requireNonBlankFile(caPath, "CA certificate");

        ManagedDataSourceSpec spec = new ManagedDataSourceSpec();
        spec.setManagementKey(properties.getManagementKey());
        spec.setAlias(properties.getAlias());
        spec.setType("POSTGRESQL");
        spec.setHost(properties.getHost());
        spec.setPort(Integer.toString(properties.getPort()));
        spec.setDatabase(properties.getDatabase());
        spec.setUser(properties.getUser());
        spec.setPassword(password);
        spec.setAuthenticationType(properties.getAuthenticationType());
        spec.setEnvironmentId(properties.getEnvironmentId());
        spec.setUrl(buildJdbcUrl(caPath));
        return spec;
    }

    private String buildJdbcUrl(Path caPath) {
        return "jdbc:postgresql://" + properties.getHost() + ":" + properties.getPort() + "/"
                + properties.getDatabase() + "?sslmode=verify-full&sslrootcert=" + caPath
                + "&ApplicationName=Chat2DB";
    }

    private void validateProperties() {
        if (StringUtils.isAnyBlank(properties.getManagementKey(), properties.getAlias(), properties.getType(),
                properties.getHost(), properties.getDatabase(), properties.getUser(),
                properties.getAuthenticationType(), properties.getPasswordFile(), properties.getCaFile())) {
            throw new IllegalArgumentException("Managed datasource bootstrap configuration is incomplete");
        }
        if (!"POSTGRESQL".equalsIgnoreCase(properties.getType())) {
            throw new IllegalArgumentException("Managed datasource bootstrap currently supports POSTGRESQL only");
        }
        if (!"verify-full".equals(properties.getSslMode())) {
            throw new IllegalArgumentException("Managed PostgreSQL datasource requires sslMode=verify-full");
        }
        if (!properties.getHost().matches("[A-Za-z0-9.-]+")
                || !properties.getDatabase().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Managed datasource host or database contains unsupported characters");
        }
        if (properties.getPort() < 1 || properties.getPort() > 65535 || properties.getEnvironmentId() == null) {
            throw new IllegalArgumentException("Managed datasource port or environmentId is invalid");
        }
    }

    private Path validatedPath(String configuredPath, long maxBytes, String description) {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Path.of(configuredPath).isAbsolute()
                || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Managed datasource " + description + " file is invalid");
        }
        try {
            long size = Files.size(path);
            if (size < 1 || size > maxBytes) {
                throw new IllegalArgumentException("Managed datasource " + description + " file has invalid size");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect managed datasource " + description + " file");
        }
        return path;
    }

    private String readPassword(Path path) {
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8);
            if (value.endsWith("\r\n")) {
                value = value.substring(0, value.length() - 2);
            } else if (value.endsWith("\n")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException("Managed datasource password file is blank");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read managed datasource password file");
        }
    }

    private void requireNonBlankFile(Path path, String description) {
        try {
            if (Files.readString(path, StandardCharsets.UTF_8).isBlank()) {
                throw new IllegalArgumentException("Managed datasource " + description + " file is blank");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read managed datasource " + description + " file");
        }
    }

    private String safeManagementKey() {
        return StringUtils.defaultIfBlank(properties.getManagementKey(), "<unset>");
    }
}
