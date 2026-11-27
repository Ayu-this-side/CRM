package com.crm.dto;

import java.math.BigDecimal;

/** Admin-only per-rep rollup used by the dashboard table. */
public record RepPerformance(String name,
                             String email,
                             long leads,
                             long customers,
                             long openDeals,
                             long wonDeals,
                             BigDecimal wonRevenue,
                             long openTasks) {
}
