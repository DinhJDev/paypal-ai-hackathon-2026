package api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentPaymentView(LocalDate dueDate, BigDecimal amount, String currency, RentPaymentStatus status) { }
