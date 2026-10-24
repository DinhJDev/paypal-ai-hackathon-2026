package com.suitefolk.api.rent;

import java.time.LocalDate;
import java.time.YearMonth;

public final class RentScheduleDates {

	private RentScheduleDates() { }

	public static LocalDate nextDueDate(Frequency frequency, LocalDate currentDueDate, int monthlyAnchorDay) {
		return switch (frequency) {
			case WEEKLY -> currentDueDate.plusWeeks(1);
			case MONTHLY -> {
				YearMonth nextMonth = YearMonth.from(currentDueDate).plusMonths(1);
				yield nextMonth.atDay(Math.min(monthlyAnchorDay, nextMonth.lengthOfMonth()));
			}
		};
	}
}
