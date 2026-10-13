package dev.eolmae.marketry.domain.view.dto;

import java.time.LocalDate;
import java.util.List;

public record MarketNoticeResponse(LocalDate date, List<MarketNoticeItem> items) {}
