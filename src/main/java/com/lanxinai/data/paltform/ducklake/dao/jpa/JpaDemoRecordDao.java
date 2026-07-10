package com.lanxinai.data.paltform.ducklake.dao.jpa;

import com.lanxinai.data.paltform.ducklake.dao.DaoKind;
import com.lanxinai.data.paltform.ducklake.dao.DemoRecordDao;
import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class JpaDemoRecordDao implements DemoRecordDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public DaoKind kind() {
        return DaoKind.JPA;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemoRecord> findAll(int limit) {
        return entityManager.createQuery(
                        "select record from DemoRecordEntity record order by record.createdAt desc, record.id desc",
                        DemoRecordEntity.class)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(DemoRecordEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemoRecord> findByBatchId(String batchId, int limit) {
        return findBatchEntities(batchId, limit).stream()
                .map(DemoRecordEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery("select count(record) from DemoRecordEntity record", Long.class)
                .getSingleResult();
    }

    @Override
    @Transactional
    public int insertBatch(List<DemoRecord> records) {
        records.stream().map(DemoRecordEntity::from).forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();
        return records.size();
    }

    @Override
    @Transactional
    public int updateFirstN(String batchId, int limit, String suffix) {
        List<DemoRecordEntity> entities = findBatchEntities(batchId, limit);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        entities.forEach(entity -> entity.markUpdated(suffix, now));
        entityManager.flush();
        return entities.size();
    }

    @Override
    @Transactional
    public int deleteFirstN(String batchId, int limit) {
        List<DemoRecordEntity> entities = findBatchEntities(batchId, limit);
        entities.forEach(entityManager::remove);
        entityManager.flush();
        return entities.size();
    }

    private List<DemoRecordEntity> findBatchEntities(String batchId, int limit) {
        return entityManager.createQuery(
                        "select record from DemoRecordEntity record "
                                + "where record.batchId = :batchId order by record.createdAt, record.id",
                        DemoRecordEntity.class)
                .setParameter("batchId", batchId)
                .setMaxResults(limit)
                .getResultList();
    }
}
