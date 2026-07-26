package ai.chat2db.community.start.bootstrap;

import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceReconcileResult;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceSpec;
import ai.chat2db.community.domain.api.service.db.IManagedDataSourceReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedDataSourceBootstrapRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsVerifyFullPostgresqlSpecFromMountedFiles() throws IOException {
        Path password = write("password", "db-password\n");
        Path ca = write("ca.crt", "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----\n");
        ManagedDataSourceBootstrapRunner runner = new ManagedDataSourceBootstrapRunner(
                properties(password, ca), noOpReconciler());

        ManagedDataSourceSpec spec = runner.buildSpec();

        assertEquals("db-password", spec.getPassword());
        assertEquals("POSTGRESQL", spec.getType());
        assertEquals("jdbc:postgresql://postgres.example.internal:5432/app?sslmode=verify-full&sslrootcert="
                + ca + "&ApplicationName=Chat2DB", spec.getUrl());
    }

    @Test
    void rejectsBlankPassword() throws IOException {
        Path password = write("password", "\n");
        Path ca = write("ca.crt", "certificate");
        ManagedDataSourceBootstrapRunner runner = new ManagedDataSourceBootstrapRunner(
                properties(password, ca), noOpReconciler());

        assertThrows(IllegalArgumentException.class, runner::buildSpec);
    }

    @Test
    void removesOnlyTheSecretFileLineEndingFromPassword() throws IOException {
        Path password = write("password", " leading-and-trailing-space \r\n");
        Path ca = write("ca.crt", "certificate");
        ManagedDataSourceBootstrapRunner runner = new ManagedDataSourceBootstrapRunner(
                properties(password, ca), noOpReconciler());

        ManagedDataSourceSpec spec = runner.buildSpec();

        assertEquals(" leading-and-trailing-space ", spec.getPassword());
    }

    @Test
    void rejectsSymlinkedSecretFile() throws IOException {
        Path target = write("password-target", "db-password");
        Path password = tempDir.resolve("password");
        Files.createSymbolicLink(password, target);
        Path ca = write("ca.crt", "certificate");
        ManagedDataSourceBootstrapRunner runner = new ManagedDataSourceBootstrapRunner(
                properties(password, ca), noOpReconciler());

        assertThrows(IllegalArgumentException.class, runner::buildSpec);
    }

    @Test
    void publicFailureDoesNotContainPassword() throws IOException {
        Path password = write("password", "highly-sensitive-password");
        Path ca = write("ca.crt", "certificate");
        IManagedDataSourceReconciler failing = spec -> {
            throw new IllegalStateException("driver echoed " + spec.getPassword());
        };
        ManagedDataSourceBootstrapRunner runner = new ManagedDataSourceBootstrapRunner(
                properties(password, ca), failing);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));

        assertFalse(exception.getMessage().contains("highly-sensitive-password"));
        assertEquals(null, exception.getCause());
    }

    private ManagedDataSourceBootstrapProperties properties(Path password, Path ca) {
        ManagedDataSourceBootstrapProperties properties = new ManagedDataSourceBootstrapProperties();
        properties.setEnabled(true);
        properties.setManagementKey("prod-readonly");
        properties.setAlias("Production (read only)");
        properties.setHost("postgres.example.internal");
        properties.setDatabase("app");
        properties.setUser("chat2db_reader");
        properties.setPasswordFile(password.toString());
        properties.setCaFile(ca.toString());
        return properties;
    }

    private IManagedDataSourceReconciler noOpReconciler() {
        return (IManagedDataSourceReconciler) Proxy.newProxyInstance(
                IManagedDataSourceReconciler.class.getClassLoader(),
                new Class<?>[]{IManagedDataSourceReconciler.class},
                (proxy, method, args) -> ManagedDataSourceReconcileResult.of(
                        ManagedDataSourceReconcileResult.Action.UNCHANGED, 7L));
    }

    private Path write(String name, String value) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, value);
        return path;
    }
}
