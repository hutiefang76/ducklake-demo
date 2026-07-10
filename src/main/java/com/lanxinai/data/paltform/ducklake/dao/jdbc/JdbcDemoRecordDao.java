package com.lanxinai.data.paltform.ducklake.dao.jdbc;

import com.lanxinai.data.paltform.ducklake.dao.DaoKind;
import com.lanxinai.data.paltform.ducklake.dao.DemoRecordDao;
import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import com.lanxinai.data.paltform.ducklake.support.DemoRecordJdbcSupport;
import com.lanxinai.data.paltform.ducklake.support.DemoSql;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class JdbcDemoRecordDao implements DemoRecordDao {

    private final DataSource dataSource;
    private final DemoSql sql;

    public JdbcDemoRecordDao(DataSource dataSource, DemoSql sql) {
        this.dataSource = dataSource;
        this.sql = sql;
    }

    @Override
    public DaoKind kind() {
        return DaoKind.JDBC;
    }

    @Override
    public List<DemoRecord> findAll(int limit) {
        return query(sql.selectAll(), statement -> statement.setInt(1, limit));
    }

    @Override
    public List<DemoRecord> findByBatchId(String batchId, int limit) {
        return query(sql.selectByBatch(), statement -> {
            statement.setString(1, batchId);
            statement.setInt(2, limit);
        });
    }

    @Override
    public long count() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.count());
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("JDBC count failed", exception);
        }
    }

    @Override
    public int insertBatch(List<DemoRecord> records) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.insert())) {
            for (DemoRecord record : records) {
                DemoRecordJdbcSupport.bindInsert(statement, record);
                statement.addBatch();
            }
            return Arrays.stream(statement.executeBatch()).sum();
        } catch (SQLException exception) {
            throw new IllegalStateException("JDBC batch insert failed", exception);
        }
    }

    @Override
    public int updateFirstN(String batchId, int limit, String suffix) {
        return executeUpdate(sql.updateFirstN(), statement -> {
            statement.setString(1, suffix);
            statement.setString(2, batchId);
            statement.setInt(3, limit);
        });
    }

    @Override
    public int deleteFirstN(String batchId, int limit) {
        return executeUpdate(sql.deleteFirstN(), statement -> {
            statement.setString(1, batchId);
            statement.setInt(2, limit);
        });
    }

    private List<DemoRecord> query(String querySql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(querySql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DemoRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(DemoRecordJdbcSupport.read(resultSet));
                }
                return records;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("JDBC query failed", exception);
        }
    }

    private int executeUpdate(String updateSql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateSql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("JDBC update failed", exception);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
