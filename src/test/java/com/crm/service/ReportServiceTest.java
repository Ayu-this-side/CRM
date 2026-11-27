package com.crm.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock LeadRepository leadRepository;
    @Mock CustomerRepository customerRepository;
    @Mock DealRepository dealRepository;
    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ReportService reportService;

    private User admin;
    private User rep;

    @BeforeEach
    void setUp() {
        admin = user(1L, "Admin", Role.ADMIN);
        rep = user(2L, "Rep Two", Role.SALES_REP);
        when(dealRepository.sumWonRevenueSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void repReportIsScopedToTheCurrentUser() {
        when(leadRepository.countByStatusGrouped(2L)).thenReturn(List.<Object[]>of(new Object[]{LeadStatus.NEW, 3L}));
        when(leadRepository.countBySourceGrouped(2L)).thenReturn(List.<Object[]>of(new Object[]{LeadSource.WEBSITE, 3L}));
        when(leadRepository.countBySourceAndStatusGrouped(LeadStatus.CONVERTED, 2L))
                .thenReturn(List.<Object[]>of(new Object[]{LeadSource.WEBSITE, 1L}));
        when(leadRepository.countScoped(2L)).thenReturn(3L);
        when(leadRepository.countByStatusScoped(LeadStatus.CONVERTED, 2L)).thenReturn(1L);
        when(dealRepository.countByStageGrouped(2L)).thenReturn(List.<Object[]>of(
                new Object[]{DealStage.WON, 2L}, new Object[]{DealStage.LOST, 2L}));
        when(dealRepository.sumValueByStageGrouped(2L))
                .thenReturn(List.<Object[]>of(new Object[]{DealStage.WON, new BigDecimal("5000")}));

        DashboardReport report = reportService.buildReport(rep);

        assertThat(report.orgWide()).isFalse();
        assertThat(report.overallConversion().rate()).isEqualTo(100.0 / 3);
        assertThat(report.conversionBySource().get(LeadSource.WEBSITE).rate()).isEqualTo(100.0 / 3);
        assertThat(report.winRate()).isEqualTo(50.0);
        assertThat(report.repPerformance()).isEmpty();
        assertThat(report.leadsByStatus().get(LeadStatus.CONVERTED)).isZero();
        assertThat(report.pipelineValueByStage().get(DealStage.PROSPECTING)).isEqualByComparingTo("0");
        assertThat(report.pipelineValueByStage().get(DealStage.WON)).isEqualByComparingTo("5000");

        verify(leadRepository).countByStatusGrouped(2L);
        verify(dealRepository).countByStageGrouped(2L);
        verify(userRepository, org.mockito.Mockito.never()).findByRoleAndEnabledTrue(Role.SALES_REP);
    }

    @Test
    void adminReportIsOrganisationWideAndIncludesPerRepTable() {
        when(leadRepository.countBySourceGrouped(null)).thenReturn(List.<Object[]>of(new Object[]{LeadSource.EVENT, 4L}));
        when(leadRepository.countBySourceAndStatusGrouped(LeadStatus.CONVERTED, null))
                .thenReturn(List.<Object[]>of(new Object[]{LeadSource.EVENT, 2L}));
        when(leadRepository.countScoped(null)).thenReturn(4L);
        when(leadRepository.countByStatusScoped(LeadStatus.CONVERTED, null)).thenReturn(2L);
        when(leadRepository.countByRepGrouped()).thenReturn(List.<Object[]>of(new Object[]{2L, 4L}));
        when(customerRepository.countByRepGrouped()).thenReturn(List.<Object[]>of(new Object[]{2L, 1L}));
        when(taskRepository.countOpenByRepGrouped(List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 2L}));
        when(dealRepository.dealSummaryByRepGrouped()).thenReturn(List.<Object[]>of(
                new Object[]{2L, DealStage.PROSPECTING, 1L, new BigDecimal("1000")},
                new Object[]{2L, DealStage.WON, 1L, new BigDecimal("2000")},
                new Object[]{2L, DealStage.LOST, 1L, new BigDecimal("3000")}));
        when(userRepository.findByRoleAndEnabledTrue(Role.SALES_REP)).thenReturn(List.of(rep));

        DashboardReport report = reportService.buildReport(admin);

        assertThat(report.orgWide()).isTrue();
        assertThat(report.overallConversion().total()).isEqualTo(4);
        assertThat(report.overallConversion().rate()).isEqualTo(50.0);
        assertThat(report.repPerformance()).hasSize(1);
        RepPerformance performance = report.repPerformance().get(0);
        assertThat(performance.name()).isEqualTo("Rep Two");
        assertThat(performance.leads()).isEqualTo(4);
        assertThat(performance.customers()).isEqualTo(1);
        assertThat(performance.openDeals()).isEqualTo(1);
        assertThat(performance.wonDeals()).isEqualTo(1);
        assertThat(performance.wonRevenue()).isEqualByComparingTo("2000");
        assertThat(performance.openTasks()).isEqualTo(2);

        verify(leadRepository).countByStatusGrouped(null);
        verify(userRepository).findByRoleAndEnabledTrue(Role.SALES_REP);
    }

    @Test
    void ratesAreZeroWhenThereIsNoData() {
        DashboardReport report = reportService.buildReport(rep);

        assertThat(report.overallConversion().rate()).isZero();
        assertThat(report.winRate()).isZero();
        assertThat(report.conversionBySource().values())
                .allMatch(stat -> stat.rate() == 0.0);
        assertThat(report.wonCount()).isZero();
        assertThat(report.lostCount()).isZero();
    }

    @Test
    void revenueWindowsUseCurrentMonthAndQuarterStarts() {
        reportService.buildReport(admin);

        LocalDate today = LocalDate.now();
        verify(dealRepository).sumWonRevenueSince(eq(DealStage.WON),
                eq(LocalDate.of(today.getYear(), today.getMonthValue(), 1)), isNull());
        int quarterStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        verify(dealRepository).sumWonRevenueSince(eq(DealStage.WON),
                eq(LocalDate.of(today.getYear(), quarterStartMonth, 1)), isNull());
    }

    private static User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@crm.com");
        user.setRole(role);
        return user;
    }
}
