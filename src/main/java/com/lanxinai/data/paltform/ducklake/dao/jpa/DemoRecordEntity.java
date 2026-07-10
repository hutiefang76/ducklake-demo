package com.lanxinai.data.paltform.ducklake.dao.jpa;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ducklake_demo_record")
public class DemoRecordEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "remark")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DemoRecordEntity() {
    }

    static DemoRecordEntity from(DemoRecord record) {
        var entity = new DemoRecordEntity();
        entity.id = record.getId();
        entity.batchId = record.getBatchId();
        entity.name = record.getName();
        entity.quantity = record.getQuantity();
        entity.amount = record.getAmount();
        entity.status = record.getStatus();
        entity.remark = record.getRemark();
        entity.createdAt = record.getCreatedAt();
        entity.updatedAt = record.getUpdatedAt();
        return entity;
    }

    DemoRecord toDomain() {
        return new DemoRecord(id, batchId, name, quantity, amount, status, remark, createdAt, updatedAt);
    }

    void markUpdated(String suffix, LocalDateTime now) {
        this.name = this.name + suffix;
        this.status = "UPDATED";
        this.remark = "updated by demo";
        this.updatedAt = now;
    }
}
