package ai.chat2db.community.domain.core.impl.db;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceReconcileResult;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceSpec;
import ai.chat2db.community.domain.api.model.datasource.SSHInfo;
import ai.chat2db.community.domain.api.model.request.datasource.DbDataSourcePageQueryRequest;
import ai.chat2db.community.domain.api.model.request.datasource.DbDataSourcePreConnectRequest;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.service.db.IDbWorkspaceDataSourceService;
import ai.chat2db.community.domain.api.service.db.IManagedDataSourceReconciler;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ManagedDataSourceReconcilerImpl implements IManagedDataSourceReconciler {

    public static final String MANAGED_BY = "KUBERNETES_BOOTSTRAP";

    private final IWorkspaceStorageFacade workspaceStorageFacade;
    private final IDbWorkspaceDataSourceService workspaceDataSourceService;

    public ManagedDataSourceReconcilerImpl(IWorkspaceStorageFacade workspaceStorageFacade,
            IDbWorkspaceDataSourceService workspaceDataSourceService) {
        this.workspaceStorageFacade = workspaceStorageFacade;
        this.workspaceDataSourceService = workspaceDataSourceService;
    }

    @Override
    public ManagedDataSourceReconcileResult reconcile(ManagedDataSourceSpec spec) {
        validate(spec);
        List<WorkspaceDataSource> dataSources = listDataSources();
        List<WorkspaceDataSource> managed = dataSources.stream()
                .filter(dataSource -> MANAGED_BY.equals(dataSource.getManagedBy()))
                .filter(dataSource -> spec.getManagementKey().equals(dataSource.getManagedKey()))
                .toList();
        if (managed.size() > 1) {
            throw new IllegalStateException("Multiple datasources use managed key: " + spec.getManagementKey());
        }
        if (managed.isEmpty()) {
            boolean aliasExists = dataSources.stream()
                    .anyMatch(dataSource -> spec.getAlias().equals(dataSource.getAlias()));
            if (aliasExists) {
                throw new IllegalStateException("Datasource alias is already owned outside this bootstrap: "
                        + spec.getAlias());
            }
            WorkspaceDataSource desired = desired(spec, null, true);
            workspaceDataSourceService.preConnect(preConnect(desired));
            WorkspaceDataSource created = workspaceDataSourceService.createDataSource(desired);
            return ManagedDataSourceReconcileResult.of(
                    ManagedDataSourceReconcileResult.Action.CREATED, created.getId());
        }

        Long dataSourceId = managed.get(0).getId();
        boolean aliasOwnedByAnotherDataSource = dataSources.stream()
                .anyMatch(dataSource -> spec.getAlias().equals(dataSource.getAlias())
                        && !Objects.equals(dataSourceId, dataSource.getId()));
        if (aliasOwnedByAnotherDataSource) {
            throw new IllegalStateException("Datasource alias is already owned by another datasource: "
                    + spec.getAlias());
        }
        WorkspaceDataSource existing = workspaceDataSourceService.queryDisplayDataSourceById(dataSourceId, true);
        if (existing == null) {
            throw new IllegalStateException("Managed datasource disappeared during reconciliation: " + dataSourceId);
        }
        boolean passwordChanged = !passwordEquals(existing.getPassword(), spec.getPassword());
        WorkspaceDataSource desired = desired(spec, dataSourceId, passwordChanged);
        preserveUnmanagedUpdateFields(existing, desired);
        if (!declarativeFieldsChanged(existing, desired) && !passwordChanged) {
            return ManagedDataSourceReconcileResult.of(
                    ManagedDataSourceReconcileResult.Action.UNCHANGED, dataSourceId);
        }

        WorkspaceDataSource testCandidate = desired(spec, dataSourceId, true);
        testCandidate.setDriverConfig(existing.getDriverConfig());
        workspaceDataSourceService.preConnect(preConnect(testCandidate));
        WorkspaceDataSource updated = workspaceDataSourceService.updateDataSource(desired);
        return ManagedDataSourceReconcileResult.of(
                ManagedDataSourceReconcileResult.Action.UPDATED, updated.getId());
    }

    private void preserveUnmanagedUpdateFields(WorkspaceDataSource existing, WorkspaceDataSource desired) {
        // Local storage merges nullable fields, but these booleans are primitive and
        // the workspace service supplies a default driver when it is absent.
        desired.setSupportDatabase(existing.isSupportDatabase());
        desired.setSupportSchema(existing.isSupportSchema());
        desired.setDriverConfig(existing.getDriverConfig());
    }

    private List<WorkspaceDataSource> listDataSources() {
        DbDataSourcePageQueryRequest request = new DbDataSourcePageQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10_000);
        PageResponse<WorkspaceDataSource> page = workspaceStorageFacade.listDataSources(request);
        return page == null || page.getData() == null ? Collections.emptyList() : page.getData();
    }

    private WorkspaceDataSource desired(ManagedDataSourceSpec spec, Long id, boolean includePassword) {
        WorkspaceDataSource desired = new WorkspaceDataSource();
        desired.setId(id);
        desired.setAlias(spec.getAlias());
        desired.setType(spec.getType());
        desired.setHost(spec.getHost());
        desired.setPort(spec.getPort());
        desired.setServiceName(spec.getDatabase());
        desired.setUrl(spec.getUrl());
        desired.setUser(spec.getUser());
        desired.setPassword(includePassword ? spec.getPassword() : null);
        desired.setAuthenticationType(spec.getAuthenticationType());
        desired.setEnvironmentId(spec.getEnvironmentId());
        desired.setManagedBy(MANAGED_BY);
        desired.setManagedKey(spec.getManagementKey());
        return desired;
    }

    private DbDataSourcePreConnectRequest preConnect(WorkspaceDataSource dataSource) {
        DbDataSourcePreConnectRequest request = new DbDataSourcePreConnectRequest();
        request.setId(null);
        request.setAlias(dataSource.getAlias());
        request.setType(dataSource.getType());
        request.setUrl(dataSource.getUrl());
        request.setUser(dataSource.getUser());
        request.setPassword(dataSource.getPassword());
        request.setAuthenticationType(dataSource.getAuthenticationType());
        request.setHost(dataSource.getHost());
        request.setPort(dataSource.getPort());
        request.setSsh(new SSHInfo());
        return request;
    }

    private boolean declarativeFieldsChanged(WorkspaceDataSource existing, WorkspaceDataSource desired) {
        return !Objects.equals(existing.getAlias(), desired.getAlias())
                || !Objects.equals(existing.getType(), desired.getType())
                || !Objects.equals(existing.getHost(), desired.getHost())
                || !Objects.equals(existing.getPort(), desired.getPort())
                || !Objects.equals(existing.getServiceName(), desired.getServiceName())
                || !Objects.equals(existing.getUrl(), desired.getUrl())
                || !Objects.equals(existing.getUser(), desired.getUser())
                || !Objects.equals(existing.getAuthenticationType(), desired.getAuthenticationType())
                || !Objects.equals(existing.getEnvironmentId(), desired.getEnvironmentId())
                || !Objects.equals(existing.getManagedBy(), desired.getManagedBy())
                || !Objects.equals(existing.getManagedKey(), desired.getManagedKey());
    }

    private boolean passwordEquals(String existing, String desired) {
        byte[] existingBytes = existing == null ? new byte[0] : existing.getBytes(StandardCharsets.UTF_8);
        byte[] desiredBytes = desired == null ? new byte[0] : desired.getBytes(StandardCharsets.UTF_8);
        try {
            return MessageDigest.isEqual(existingBytes, desiredBytes);
        } finally {
            Arrays.fill(existingBytes, (byte) 0);
            Arrays.fill(desiredBytes, (byte) 0);
        }
    }

    private void validate(ManagedDataSourceSpec spec) {
        if (spec == null
                || StringUtils.isAnyBlank(spec.getManagementKey(), spec.getAlias(), spec.getType(), spec.getHost(),
                spec.getPort(), spec.getDatabase(), spec.getUser(), spec.getPassword(), spec.getAuthenticationType(),
                spec.getUrl())
                || spec.getEnvironmentId() == null) {
            throw new IllegalArgumentException("Managed datasource configuration is incomplete");
        }
        if (!"POSTGRESQL".equalsIgnoreCase(spec.getType())) {
            throw new IllegalArgumentException("Managed datasource bootstrap currently supports POSTGRESQL only");
        }
    }
}
