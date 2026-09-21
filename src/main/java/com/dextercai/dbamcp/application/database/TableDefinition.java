package com.dextercai.dbamcp.application.database;

import java.util.List;

/** Bounded Oracle data-dictionary description of one table. */
public record TableDefinition(
        TableAttributes table,
        List<ColumnDefinition> columns,
        List<ConstraintColumn> constraints,
        List<IndexColumn> indexes) {
    public TableDefinition {
        columns = List.copyOf(columns);
        constraints = List.copyOf(constraints);
        indexes = List.copyOf(indexes);
    }

    public record TableAttributes(String owner, String tableName, String tablespaceName, String temporary,
                                  String partitioned, String iotType, String compression, String logging) {
    }

    public record ColumnDefinition(Integer position, String name, String dataType, Integer dataLength,
                                   Integer dataPrecision, Integer dataScale, String nullable,
                                   String identity, String virtual, String comment) {
    }

    public record ConstraintColumn(String name, String type, String status, String validated, String deferrable,
                                   String deferred, String deleteRule, String referencedOwner,
                                   String referencedConstraint, Integer position, String columnName) {
    }

    public record IndexColumn(String name, String type, String uniqueness, String status, String visibility,
                              String tablespaceName, Integer position, String columnName, String direction) {
    }
}
