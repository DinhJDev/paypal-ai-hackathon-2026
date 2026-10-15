package api.rent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentScheduleRepository extends JpaRepository<RentSchedule, UUID> {

	List<RentSchedule> findAllByOwnerIdOrderByNextDueDateAsc(UUID ownerId);

	Optional<RentSchedule> findByIdAndOwnerId(UUID id, UUID ownerId);

	Optional<RentSchedule> findByPaypalSetupTokenId(String setupTokenId);

	@Query("select s.id from RentSchedule s where s.status = :status and s.nextDueDate <= :dueDate order by s.nextDueDate")
	List<UUID> findDueIds(@Param("status") RentScheduleStatus status, @Param("dueDate") LocalDate dueDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from RentSchedule s where s.id = :id")
	Optional<RentSchedule> findByIdForUpdate(@Param("id") UUID id);
}
