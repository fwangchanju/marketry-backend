package dev.eolmae.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketSectorTrendItem(LocalDate date, String sectorName, BigDecimal changeRate) {}
