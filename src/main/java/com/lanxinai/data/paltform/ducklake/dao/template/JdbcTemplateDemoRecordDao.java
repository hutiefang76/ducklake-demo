package com.lanxinai.data.paltform.ducklake.dao.template;

import com.lanxinai.data.paltform.ducklake.dao.DaoKind;
import com.lanxinai.data.paltform.ducklake.dao.DemoRecordDao;
import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import com.lanxinai.data.paltform.ducklake.support.DemoRecordJdbcSupport;
import com.lanxinai.data.paltform.ducklake.support.DemoSql;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
public class JdbcTemplateDemoRecordDao implements DemoRecordDao {

    private final JdbcTemplate jdbcTemplate;
    private final DemoSql sql;

    public JdbcTemplateDemoRecordDao(JdbcTemplate jdbcTemplate, DemoSql sql) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
    }

    @Override
    public DaoKind kind() {
        return DaoKind.JDBC_TEMPLATE;
    }

    @Override
    public List<DemoRecord> findAll(int limit) {
        return jdbcTemplate.query(sql.selectAll(), (resultSet, rowNum) -> DemoRecordJdbcSupport.read(resultSet), limit);
    }

    @Override
    public List<DemoRecord> findByBatchId(String batchId, int limit) {
        return jdbcTemplate.query(sql.selectByBatch(),
                (resultSet, rowNum) -> DemoRecordJdbcSupport.read(resultSet), batchId, limit);
    }

    @Override
    public long count() {
        Long value = jdbcTemplate.queryForObject(sql.count(), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public int insertBatch(List<DemoRecord> records) {
        int[] results = jdbcTemplate.batchUpdate(sql.insert(), new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                DemoRecordJdbcSupport.bindInsert(statement, records.get(index));
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
        return Arrays.stream(results).sum();
    }

    @Override
    public int updateFirstN(String batchId, int limit, String suffix) {
        return jdbcTemplate.update(sql.updateFirstN(), suffix, batchId, limit);
    }

    @Override
    public int deleteFirstN(String batchId, int limit) {
        return jdbcTemplate.update(sql.deleteFirstN(), batchId, limit);
    }
}
