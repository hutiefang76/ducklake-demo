package com.lanxinai.data.paltform.ducklake.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DemoRecord {

    private String id;
    private String batchId;
    private String name;
    private int quantity;
    private BigDecimal amount;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DemoRecord() {
    }

    public DemoRecord(String id, String batchId, String name, int quantity, BigDecimal amount,
                      String status, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.batchId = batchId;
        this.name = name;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
