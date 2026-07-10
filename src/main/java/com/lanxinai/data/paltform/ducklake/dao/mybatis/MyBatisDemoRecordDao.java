package com.lanxinai.data.paltform.ducklake.dao.mybatis;

import com.lanxinai.data.paltform.ducklake.dao.DaoKind;
import com.lanxinai.data.paltform.ducklake.dao.DemoRecordDao;
import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import com.lanxinai.data.paltform.ducklake.support.DemoSql;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisDemoRecordDao implements DemoRecordDao {

    private final DemoRecordMapper mapper;
    private final String table;

    public MyBatisDemoRecordDao(DemoRecordMapper mapper, DemoSql sql) {
        this.mapper = mapper;
        this.table = sql.table();
    }

    @Override
    public DaoKind kind() {
        return DaoKind.MYBATIS;
    }

    @Override
    public List<DemoRecord> findAll(int limit) {
        return mapper.findAll(table, limit);
    }

    @Override
    public List<DemoRecord> findByBatchId(String batchId, int limit) {
        return mapper.findByBatchId(table, batchId, limit);
    }

    @Override
    public long count() {
        return mapper.count(table);
    }

    @Override
    public int insertBatch(List<DemoRecord> records) {
        return mapper.insertBatch(table, records);
    }

    @Override
    public int updateFirstN(String batchId, int limit, String suffix) {
        return mapper.updateFirstN(table, batchId, limit, suffix);
    }

    @Override
    public int deleteFirstN(String batchId, int limit) {
        return mapper.deleteFirstN(table, batchId, limit);
    }
}
