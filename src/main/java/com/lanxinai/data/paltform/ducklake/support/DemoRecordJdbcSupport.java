package com.lanxinai.data.paltform.ducklake.support;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public final class DemoRecordJdbcSupport {

    private DemoRecordJdbcSupport() {
    }

    public static DemoRecord read(ResultSet resultSet) throws SQLException {
        return new DemoRecord(
                resultSet.getString("id"),
                resultSet.getString("batch_id"),
                resultSet.getString("name"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("status"),
                resultSet.getString("remark"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    public static void bindInsert(PreparedStatement statement, DemoRecord record) throws SQLException {
        statement.setString(1, record.getId());
        statement.setString(2, record.getBatchId());
        statement.setString(3, record.getName());
        statement.setInt(4, record.getQuantity());
        statement.setBigDecimal(5, record.getAmount());
        statement.setString(6, record.getStatus());
        statement.setString(7, record.getRemark());
        statement.setTimestamp(8, Timestamp.valueOf(record.getCreatedAt()));
        statement.setTimestamp(9, Timestamp.valueOf(record.getUpdatedAt()));
    }
}
