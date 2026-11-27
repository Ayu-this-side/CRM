package com.crm.service;

import com.crm.dto.ConversionStat;
import com.crm.dto.DashboardReport;
import com.crm.dto.RepPerformance;
import com.crm.entity.User;
import com.crm.enums.DealStage;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import com.crm.enums.Role;
import com.crm.enums.TaskStatus;
import com.crm.repository.CustomerRepository;
import com.crm.repository.DealRepository;
import com.crm.repository.LeadRepository;
import com.crm.repository.TaskRepository;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the role-scoped aggregates displayed on the reporting dashboard. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<TaskStatus> OPEN_TASK_STATUSES =
            List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS);

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardReport buildReport(User currentUser) {
        boolean orgWide = currentUser.getRole() == Role.ADMIN;
        Long repId = orgWide ? null : currentUser.getId();

        Map<LeadStatus, Long> leadsByStatus = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            leadsByStatus.put(status, 0L);
        }
        for (Object[] row : leadRepository.countByStatusGrouped(repId)) {
            leadsByStatus.put(toEnum(row[0], LeadStatus.class), toLong(row[1]));
        }

        Map<LeadSource, Long> totalBySource = new EnumMap<>(LeadSource.class);
        for (Object[] row : leadRepository.countBySourceGrouped(repId)) {
            totalBySource.put(toEnum(row[0], LeadSource.class), toLong(row[1]));
        }
        Map<LeadSource, Long> convertedBySource = new EnumMap<>(LeadSource.class);
        for (Object[] row : leadRepository.countBySourceAndStatusGrouped(LeadStatus.CONVERTED, repId)) {
            convertedBySource.put(toEnum(row[0], LeadSource.class), toLong(row[1]));
        }
        Map<LeadSource, ConversionStat> conversionBySource = new EnumMap<>(LeadSource.class);
        for (LeadSource source : LeadSource.values()) {
            long total = totalBySource.getOrDefault(source, 0L);
            long converted = convertedBySource.getOrDefault(source, 0L);
            conversionBySource.put(source, new ConversionStat(total, converted, rate(converted, total)));
        }

        long allLeads = leadRepository.countScoped(repId);
        long convertedLeads = leadRepository.countByStatusScoped(LeadStatus.CONVERTED, repId);
        ConversionStat overallConversion = new ConversionStat(allLeads, convertedLeads, rate(convertedLeads, allLeads));

        Map<DealStage, BigDecimal> pipelineValueByStage = new EnumMap<>(DealStage.class);
        Map<DealStage, Long> outcomes = new EnumMap<>(DealStage.class);
        for (DealStage stage : DealStage.values()) {
            pipelineValueByStage.put(stage, BigDecimal.ZERO);
            outcomes.put(stage, 0L);
        }
        for (Object[] row : dealRepository.sumValueByStageGrouped(repId)) {
            pipelineValueByStage.put(toEnum(row[0], DealStage.class), toDecimal(row[1]));
        }
        for (Object[] row : dealRepository.countByStageGrouped(repId)) {
            outcomes.put(toEnum(row[0], DealStage.class), toLong(row[1]));
        }

        long won = outcomes.getOrDefault(DealStage.WON, 0L);
        long lost = outcomes.getOrDefault(DealStage.LOST, 0L);
        double winRate = rate(won, won + lost);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonthValue(), 1);
        int quarterStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate quarterStart = LocalDate.of(today.getYear(), quarterStartMonth, 1);
        BigDecimal revenueThisMonth = dealRepository.sumWonRevenueSince(DealStage.WON, monthStart, repId);
        BigDecimal revenueThisQuarter = dealRepository.sumWonRevenueSince(DealStage.WON, quarterStart, repId);

        List<RepPerformance> repPerformance = orgWide ? buildRepPerformance() : List.of();

        return new DashboardReport(leadsByStatus, conversionBySource, overallConversion,
                pipelineValueByStage, outcomes, winRate, revenueThisMonth, revenueThisQuarter,
                repPerformance, orgWide);
    }

    /** Admin-only per-rep table; every active rep is included even with zero activity. */
    private List<RepPerformance> buildRepPerformance() {
        Map<Long, Long> leadsByRep = toLongMap(leadRepository.countByRepGrouped());
        Map<Long, Long> customersByRep = toLongMap(customerRepository.countByRepGrouped());
        Map<Long, Long> openTasksByRep = toLongMap(taskRepository.countOpenByRepGrouped(OPEN_TASK_STATUSES));

        Map<Long, Long> openDealsByRep = new HashMap<>();
        Map<Long, Long> wonDealsByRep = new HashMap<>();
        Map<Long, BigDecimal> revenueByRep = new HashMap<>();
        for (Object[] row : dealRepository.dealSummaryByRepGrouped()) {
            Long id = toLong(row[0]);
            DealStage stage = toEnum(row[1], DealStage.class);
            long count = toLong(row[2]);
            BigDecimal value = toDecimal(row[3]);
            if (stage == DealStage.WON) {
                wonDealsByRep.merge(id, count, Long::sum);
                revenueByRep.merge(id, value, BigDecimal::add);
            } else if (stage != DealStage.LOST) {
                openDealsByRep.merge(id, count, Long::sum);
            }
        }

        return userRepository.findByRoleAndEnabledTrue(Role.SALES_REP).stream()
                .map(rep -> new RepPerformance(
                        rep.getName(),
                        rep.getEmail(),
                        leadsByRep.getOrDefault(rep.getId(), 0L),
                        customersByRep.getOrDefault(rep.getId(), 0L),
                        openDealsByRep.getOrDefault(rep.getId(), 0L),
                        wonDealsByRep.getOrDefault(rep.getId(), 0L),
                        revenueByRep.getOrDefault(rep.getId(), BigDecimal.ZERO),
                        openTasksByRep.getOrDefault(rep.getId(), 0L)))
                .toList();
    }

    // ---- Conversion helpers for grouped query results ----

    private <E extends Enum<E>> E toEnum(Object value, Class<E> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return Enum.valueOf(type, value.toString());
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Map<Long, Long> toLongMap(List<Object[]> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(toLong(row[0]), toLong(row[1]));
        }
        return result;
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (numerator * 100.0) / denominator;
    }
}
