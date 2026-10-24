package com.suitefolk.api.rent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "rent_period")
public class RentPeriod {
    @Id private UUID id;
    @Column(name="schedule_id", nullable=false, updatable=false) private UUID scheduleId;
    @Column(name="due_date", nullable=false, updatable=false) private LocalDate dueDate;
    @Column(nullable=false, precision=10, scale=2, updatable=false) private BigDecimal amount;
    @Column(name="collection_held", nullable=false) private boolean collectionHeld;
    @Column(name="review_required", nullable=false) private boolean reviewRequired;
    @Column(name="hold_reason", length=32) private String holdReason;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long revision;
    protected RentPeriod() { }
    public RentPeriod(UUID scheduleId, LocalDate dueDate, BigDecimal amount) {
        this.id=UUID.randomUUID(); this.scheduleId=scheduleId; this.dueDate=dueDate;
        this.amount=amount; this.updatedAt=Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getScheduleId() { return scheduleId; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
    public boolean isCollectionHeld() { return collectionHeld; }
    public boolean isReviewRequired() { return reviewRequired; }
    public String getHoldReason() { return holdReason; }
    public Instant getReviewedAt() { return reviewedAt; }
    public long getRevision() { return revision; }
    public void hold(String reason) {
        collectionHeld=true; reviewRequired=true; holdReason=reason; updatedAt=Instant.now();
    }
    public void recordProviderChange() { reviewRequired=collectionHeld; updatedAt=Instant.now(); }
    public void acknowledge(UUID ownerId) {
        reviewedBy=ownerId; reviewedAt=Instant.now(); reviewRequired=false; updatedAt=reviewedAt;
    }
}
