package ai.chat2db.community.domain.core.impl.db;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceReconcileResult;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceSpec;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.service.db.IDbWorkspaceDataSourceService;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedDataSourceReconcilerImplTest {

    private final List<WorkspaceDataSource> stored = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private WorkspaceDataSource updateRequest;
    private boolean failPreConnect;
    private ManagedDataSourceReconcilerImpl reconciler;

    @BeforeEach
    void setUp() {
        stored.clear();
        calls.clear();
        updateRequest = null;
        failPreConnect = false;
        reconciler = new ManagedDataSourceReconcilerImpl(storageFacade(), workspaceService());
    }

    @Test
    void createsManagedDatasourceAfterConnectionTest() {
        ManagedDataSourceReconcileResult result = reconciler.reconcile(spec());

        assertEquals(ManagedDataSourceReconcileResult.Action.CREATED, result.getAction());
        assertEquals(101L, result.getDataSourceId());
        assertEquals(List.of("preConnect", "create"), calls);
        assertEquals(ManagedDataSourceReconcilerImpl.MANAGED_BY, stored.get(0).getManagedBy());
        assertEquals("prod-readonly", stored.get(0).getManagedKey());
    }

    @Test
    void leavesIdenticalManagedDatasourceUnchanged() {
        stored.add(existing("secret"));

        ManagedDataSourceReconcileResult result = reconciler.reconcile(spec());

        assertEquals(ManagedDataSourceReconcileResult.Action.UNCHANGED, result.getAction());
        assertEquals(List.of("query:7"), calls);
    }

    @Test
    void testsThenUpdatesChangedPasswordWithoutPopulatingUnmanagedFields() {
        WorkspaceDataSource existing = existing("old-secret");
        existing.setKind("user-owned-kind");
        existing.setSupportDatabase(true);
        existing.setSupportSchema(true);
        DriverConfig customDriver = new DriverConfig();
        customDriver.setJdbcDriverClass("custom.Driver");
        existing.setDriverConfig(customDriver);
        stored.add(existing);

        ManagedDataSourceReconcileResult result = reconciler.reconcile(spec());

        assertEquals(ManagedDataSourceReconcileResult.Action.UPDATED, result.getAction());
        assertEquals(List.of("query:7", "preConnect", "update:7"), calls);
        assertEquals("secret", updateRequest.getPassword());
        assertNull(updateRequest.getKind());
        assertEquals(true, updateRequest.isSupportDatabase());
        assertEquals(true, updateRequest.isSupportSchema());
        assertEquals("custom.Driver", updateRequest.getDriverConfig().getJdbcDriverClass());
    }

    @Test
    void doesNotPersistWhenConnectionTestFails() {
        failPreConnect = true;

        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(spec()));

        assertEquals(List.of("preConnect"), calls);
        assertEquals(0, stored.size());
    }

    @Test
    void refusesToAdoptUserOwnedAlias() {
        WorkspaceDataSource userOwned = existing("secret");
        userOwned.setManagedBy(null);
        userOwned.setManagedKey(null);
        stored.add(userOwned);

        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(spec()));

        assertEquals(List.of(), calls);
        assertNull(stored.get(0).getManagedBy());
    }

    @Test
    void refusesDuplicateManagedKeys() {
        stored.add(existing("secret"));
        WorkspaceDataSource duplicate = existing("secret");
        duplicate.setId(8L);
        duplicate.setAlias("other alias");
        stored.add(duplicate);

        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(spec()));

        assertEquals(List.of(), calls);
    }

    @Test
    void refusesToRenameManagedDatasourceOntoAnotherAlias() {
        WorkspaceDataSource managed = existing("secret");
        managed.setAlias("Previous managed alias");
        stored.add(managed);
        WorkspaceDataSource userOwned = existing("unrelated-secret");
        userOwned.setId(8L);
        userOwned.setManagedBy(null);
        userOwned.setManagedKey(null);
        stored.add(userOwned);

        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(spec()));

        assertEquals(List.of(), calls);
        assertEquals("Previous managed alias", stored.get(0).getAlias());
    }

    private IWorkspaceStorageFacade storageFacade() {
        return (IWorkspaceStorageFacade) Proxy.newProxyInstance(
                IWorkspaceStorageFacade.class.getClassLoader(), new Class<?>[]{IWorkspaceStorageFacade.class},
                (proxy, method, args) -> {
                    if ("listDataSources".equals(method.getName())) {
                        return PageResponse.of(new ArrayList<>(stored), (long) stored.size(), 1, 10_000);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IDbWorkspaceDataSourceService workspaceService() {
        return (IDbWorkspaceDataSourceService) Proxy.newProxyInstance(
                IDbWorkspaceDataSourceService.class.getClassLoader(),
                new Class<?>[]{IDbWorkspaceDataSourceService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "queryDisplayDataSourceById" -> {
                        calls.add("query:" + args[0]);
                        yield stored.stream().filter(item -> item.getId().equals(args[0])).findFirst().orElse(null);
                    }
                    case "preConnect" -> {
                        calls.add("preConnect");
                        if (failPreConnect) {
                            throw new IllegalStateException("connection failed");
                        }
                        yield null;
                    }
                    case "createDataSource" -> {
                        calls.add("create");
                        WorkspaceDataSource created = (WorkspaceDataSource) args[0];
                        created.setId(101L);
                        stored.add(created);
                        yield created;
                    }
                    case "updateDataSource" -> {
                        updateRequest = (WorkspaceDataSource) args[0];
                        calls.add("update:" + updateRequest.getId());
                        yield updateRequest;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private ManagedDataSourceSpec spec() {
        ManagedDataSourceSpec spec = new ManagedDataSourceSpec();
        spec.setManagementKey("prod-readonly");
        spec.setAlias("Production (read only)");
        spec.setType("POSTGRESQL");
        spec.setHost("postgres.example.internal");
        spec.setPort("5432");
        spec.setDatabase("app");
        spec.setUser("chat2db_reader");
        spec.setPassword("secret");
        spec.setAuthenticationType("1");
        spec.setEnvironmentId(2L);
        spec.setUrl("jdbc:postgresql://postgres.example.internal:5432/app?sslmode=verify-full");
        return spec;
    }

    private WorkspaceDataSource existing(String password) {
        ManagedDataSourceSpec spec = spec();
        WorkspaceDataSource dataSource = new WorkspaceDataSource();
        dataSource.setId(7L);
        dataSource.setAlias(spec.getAlias());
        dataSource.setType(spec.getType());
        dataSource.setHost(spec.getHost());
        dataSource.setPort(spec.getPort());
        dataSource.setServiceName(spec.getDatabase());
        dataSource.setUrl(spec.getUrl());
        dataSource.setUser(spec.getUser());
        dataSource.setPassword(password);
        dataSource.setAuthenticationType(spec.getAuthenticationType());
        dataSource.setEnvironmentId(spec.getEnvironmentId());
        dataSource.setManagedBy(ManagedDataSourceReconcilerImpl.MANAGED_BY);
        dataSource.setManagedKey(spec.getManagementKey());
        return dataSource;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
