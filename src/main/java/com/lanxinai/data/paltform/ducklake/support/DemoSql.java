package com.lanxinai.data.paltform.ducklake.support;

import com.lanxinai.data.paltform.ducklake.config.DemoTable;

public final class DemoSql {

    private final String table;

    public DemoSql(DemoTable demoTable) {
        this.table = demoTable.qualifiedName();
    }

    public String selectAll() {
        return "SELECT id, batch_id, name, quantity, amount, status, remark, created_at, updated_at "
                + "FROM " + table + " ORDER BY created_at DESC, id DESC LIMIT ?";
    }

    public String selectByBatch() {
        return "SELECT id, batch_id, name, quantity, amount, status, remark, created_at, updated_at "
                + "FROM " + table + " WHERE batch_id = ? ORDER BY created_at, id LIMIT ?";
    }

    public String count() {
        return "SELECT COUNT(*) FROM " + table;
    }

    public String insert() {
        return "INSERT INTO " + table
                + " (id, batch_id, name, quantity, amount, status, remark, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String updateFirstN() {
        return "UPDATE " + table + " SET name = name || ?, status = 'UPDATED', "
                + "remark = 'updated by demo', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id IN (SELECT id FROM " + table
                + " WHERE batch_id = ? ORDER BY created_at, id LIMIT ?)";
    }

    public String deleteFirstN() {
        return "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table
                + " WHERE batch_id = ? ORDER BY created_at, id LIMIT ?)";
    }

    public String table() {
        return table;
    }
}
