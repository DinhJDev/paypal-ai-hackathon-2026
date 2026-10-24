package com.suitefolk.api.rent;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RentPeriodRepository extends JpaRepository<RentPeriod,UUID> {
    List<RentPeriod> findByScheduleIdOrderByDueDateAsc(UUID scheduleId);
    Optional<RentPeriod> findByScheduleIdAndDueDate(UUID scheduleId, LocalDate dueDate);
    Optional<RentPeriod> findByIdAndScheduleId(UUID id, UUID scheduleId);
}
