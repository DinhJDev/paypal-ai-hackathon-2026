package com.suitefolk.api.lease;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record LeaseWorkspaceView(UUID id,String status,long revision,LeaseTerms terms,String leaseStatus,
    List<String> confirmationIssues,List<LocalDate> previewDueDates,boolean hasMoreDueDates,
    UUID scheduleId,Instant updatedAt,Instant confirmedAt) { }
