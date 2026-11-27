package com.crm.dto;

import com.crm.enums.DealStage;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * All dashboard aggregates for one request. Values are already scoped to the
 * current user (Admin = organisation-wide, Sales Rep = own records only).
 */
public record DashboardReport(Map<LeadStatus, Long> leadsByStatus,
                              Map<LeadSource, ConversionStat> conversionBySource,
                              ConversionStat overallConversion,
                              Map<DealStage, BigDecimal> pipelineValueByStage,
                              Map<DealStage, Long> outcomes,
                              double winRate,
                              BigDecimal revenueThisMonth,
                              BigDecimal revenueThisQuarter,
                              List<RepPerformance> repPerformance,
                              boolean orgWide) {

    public long wonCount() {
        return outcomes.getOrDefault(DealStage.WON, 0L);
    }

    public long lostCount() {
        return outcomes.getOrDefault(DealStage.LOST, 0L);
    }
}
