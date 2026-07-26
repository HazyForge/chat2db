package ai.chat2db.community.domain.api.service.db;

import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceReconcileResult;
import ai.chat2db.community.domain.api.model.datasource.ManagedDataSourceSpec;

public interface IManagedDataSourceReconciler {

    ManagedDataSourceReconcileResult reconcile(ManagedDataSourceSpec spec);
}
