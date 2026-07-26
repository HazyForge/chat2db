package ai.chat2db.community.domain.api.model.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class ManagedDataSourceReconcileResult {

    public enum Action {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    private Action action;

    private Long dataSourceId;
}
