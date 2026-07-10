package com.lanxinai.data.paltform.ducklake.dao;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;

import java.util.List;

public interface DemoRecordDao {

    DaoKind kind();

    List<DemoRecord> findAll(int limit);

    List<DemoRecord> findByBatchId(String batchId, int limit);

    long count();

    int insertBatch(List<DemoRecord> records);

    int updateFirstN(String batchId, int limit, String suffix);

    int deleteFirstN(String batchId, int limit);
}
