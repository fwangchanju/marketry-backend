package kr.co.marketry.common.event;

import java.time.LocalDate;
import kr.co.marketry.common.enums.Country;

public record MarketCalendarChangedEvent(Country country, LocalDate date) {}
