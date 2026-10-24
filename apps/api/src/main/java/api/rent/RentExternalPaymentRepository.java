package api.rent;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RentExternalPaymentRepository extends JpaRepository<RentExternalPayment,UUID> {
    List<RentExternalPayment> findByPeriodIdOrderByRecordedAtAscIdAsc(UUID periodId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="""
        INSERT INTO rent_external_payment (id,period_id,amount,paid_on,method,reference,recorded_by)
        VALUES (:id,:period,:amount,:paidOn,:method,:reference,:owner) ON CONFLICT (id) DO NOTHING
        """,nativeQuery=true)
    int recordOnce(@org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("period") UUID period,
        @org.springframework.data.repository.query.Param("amount") java.math.BigDecimal amount,
        @org.springframework.data.repository.query.Param("paidOn") java.time.LocalDate paidOn,
        @org.springframework.data.repository.query.Param("method") String method,
        @org.springframework.data.repository.query.Param("reference") String reference,
        @org.springframework.data.repository.query.Param("owner") UUID owner);
}
