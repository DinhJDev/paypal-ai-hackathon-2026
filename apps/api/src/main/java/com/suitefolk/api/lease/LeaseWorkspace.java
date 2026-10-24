package com.suitefolk.api.lease;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.suitefolk.api.rent.Frequency;
import jakarta.persistence.*;

@Entity
@Table(name="lease_workspace")
public class LeaseWorkspace {
    @Id UUID id;
    @Column(name="owner_id",nullable=false,updatable=false) UUID ownerId;
    @Column(nullable=false) long revision;
    @Column(name="tenant_name",nullable=false,length=120) String tenantName;
    @Column(name="suite_label",nullable=false,length=120) String suiteLabel;
    @Column(name="start_date") LocalDate startDate;
    @Column(name="end_date") LocalDate endDate;
    @Column(precision=10,scale=2) BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(length=16) Frequency frequency;
    @Column(name="first_due_date") LocalDate firstDueDate;
    @Column(name="late_fee_terms",nullable=false,length=2000) String lateFeeTerms;
    @Column(name="furnished_items",nullable=false,length=4000) String furnishedItems;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Column(name="confirmed_at") Instant confirmedAt;
    @Column(name="confirmed_by") UUID confirmedBy;
    @Column(name="schedule_id",unique=true) UUID scheduleId;
    protected LeaseWorkspace() { }
    LeaseTerms terms() {
        return new LeaseTerms(tenantName,suiteLabel,startDate,endDate,amount,frequency,firstDueDate,lateFeeTerms,furnishedItems);
    }
    void saveTerms(LeaseTerms terms) {
        tenantName=terms.tenantName();suiteLabel=terms.suiteLabel();startDate=terms.startDate();endDate=terms.endDate();
        amount=terms.amount();frequency=terms.frequency();firstDueDate=terms.firstDueDate();
        lateFeeTerms=terms.lateFeeTerms();furnishedItems=terms.furnishedItems();
        revision++; updatedAt=Instant.now();
    }
    void confirm(UUID owner,UUID schedule) {
        confirmedBy=owner;confirmedAt=Instant.now();updatedAt=confirmedAt;scheduleId=schedule;
    }
}
