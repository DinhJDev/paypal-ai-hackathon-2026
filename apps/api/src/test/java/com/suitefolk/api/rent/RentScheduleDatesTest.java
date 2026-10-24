package com.suitefolk.api.rent;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RentScheduleDatesTest {

	@Test
	void weeklyRentAdvancesBySevenDays() {
		assertThat(RentScheduleDates.nextDueDate(Frequency.WEEKLY, LocalDate.parse("2026-10-15"), 15))
				.isEqualTo(LocalDate.parse("2026-10-22"));
	}

	@Test
	void monthlyRentKeepsItsOriginalAnchorAfterShortMonths() {
		LocalDate february = RentScheduleDates.nextDueDate(Frequency.MONTHLY, LocalDate.parse("2027-01-31"), 31);
		LocalDate march = RentScheduleDates.nextDueDate(Frequency.MONTHLY, february, 31);

		assertThat(february).isEqualTo(LocalDate.parse("2027-02-28"));
		assertThat(march).isEqualTo(LocalDate.parse("2027-03-31"));
	}
}
