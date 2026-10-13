package dev.eolmae.marketry.domain.stock.dto;

import java.util.List;

public record TossStockListResponse(List<Item> result) {
    public record Item(String symbol) {}
}
