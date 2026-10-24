package com.suitefolk.api.rent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentPaymentRepository extends JpaRepository<RentPayment, UUID> {

	Optional<RentPayment> findByScheduleIdAndDueDate(UUID scheduleId, LocalDate dueDate);

	List<RentPayment> findTop5ByScheduleIdOrderByDueDateDesc(UUID scheduleId);

	Optional<RentPayment> findByPaypalOrderId(String orderId);

	Optional<RentPayment> findByPaypalCaptureId(String captureId);

	Optional<RentPayment> findByIdempotencyKey(String idempotencyKey);
}
