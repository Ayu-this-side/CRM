package com.crm.repository;

import com.crm.entity.Customer;
import com.crm.entity.Deal;
import com.crm.entity.Lead;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
import com.crm.enums.DealStage;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the role-scoped aggregate queries used by the Phase 5 dashboard. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportingRepositoryTest {

    @Autowired LeadRepository leadRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired DealRepository dealRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired UserRepository userRepository;

    private User repA;
    private User repB;

    @BeforeEach
    void setUp() {
        repA = saveUser("Rep A", "repa@crm.com");
        repB = saveUser("Rep B", "repb@crm.com");

        // Rep A: 2 new website leads, 1 converted referral lead
        saveLead("Lead A1", LeadSource.WEBSITE, LeadStatus.NEW, repA);
        saveLead("Lead A2", LeadSource.WEBSITE, LeadStatus.CONTACTED, repA);
        saveLead("Lead A3", LeadSource.REFERRAL, LeadStatus.CONVERTED, repA);
        // Rep B: 1 qualified event lead
        saveLead("Lead B1", LeadSource.EVENT, LeadStatus.QUALIFIED, repB);

        Customer customerA = saveCustomer("Customer A", repA);
        Customer customerB = saveCustomer("Customer B", repB);

        saveDeal("Open A", customerA, repA, DealStage.PROSPECTING, "5000", null);
        saveDeal("Won A", customerA, repA, DealStage.WON, "10000", LocalDate.now().minusDays(3));
        saveDeal("Lost A", customerA, repA, DealStage.LOST, "7000", null);
        saveDeal("Won B", customerB, repB, DealStage.WON, "20000", LocalDate.now().minusDays(2));
        saveDeal("Old Won B", customerB, repB, DealStage.WON, "30000", LocalDate.now().minusDays(200));

        saveTask("Open task A", repA, TaskStatus.PENDING);
        saveTask("Done task A", repA, TaskStatus.COMPLETED);
        saveTask("Open task B", repB, TaskStatus.IN_PROGRESS);
    }

    @Test
    void leadStatusCountsAreScopedByRep() {
        Map<Object, Long> admin = toMap(leadRepository.countByStatusGrouped(null));
        assertThat(admin.get(LeadStatus.NEW)).isEqualTo(1L);
        assertThat(admin.get(LeadStatus.CONTACTED)).isEqualTo(1L);
        assertThat(admin.get(LeadStatus.CONVERTED)).isEqualTo(1L);

        Map<Object, Long> repAOnly = toMap(leadRepository.countByStatusGrouped(repA.getId()));
        assertThat(repAOnly.get(LeadStatus.QUALIFIED)).isNull();
        assertThat(repAOnly.get(LeadStatus.NEW)).isEqualTo(1L);
        assertThat(leadRepository.countScoped(repA.getId())).isEqualTo(3);
        assertThat(leadRepository.countByStatusScoped(LeadStatus.CONVERTED, repA.getId())).isEqualTo(1);
    }

    @Test
    void conversionCountsBySourceAreScoped() {
        Map<Object, Long> total = toMap(leadRepository.countBySourceGrouped(null));
        assertThat(total.get(LeadSource.WEBSITE)).isEqualTo(2L);

        Map<Object, Long> converted = toMap(
                leadRepository.countBySourceAndStatusGrouped(LeadStatus.CONVERTED, null));
        assertThat(converted.get(LeadSource.REFERRAL)).isEqualTo(1L);

        Map<Object, Long> convertedForRepB = toMap(
                leadRepository.countBySourceAndStatusGrouped(LeadStatus.CONVERTED, repB.getId()));
        assertThat(convertedForRepB).isEmpty();
    }

    @Test
    void pipelineValueAndOutcomeCountsAreScoped() {
        Map<Object, BigDecimal> values = toMapDecimal(dealRepository.sumValueByStageGrouped(null));
        assertThat(values.get(DealStage.WON)).isEqualByComparingTo("60000");
        assertThat(values.get(DealStage.PROSPECTING)).isEqualByComparingTo("5000");

        Map<Object, Long> outcomes = toMap(dealRepository.countByStageGrouped(null));
        assertThat(outcomes.get(DealStage.WON)).isEqualTo(3L);
        assertThat(outcomes.get(DealStage.LOST)).isEqualTo(1L);

        Map<Object, Long> repBOutcomes = toMap(dealRepository.countByStageGrouped(repB.getId()));
        assertThat(repBOutcomes.get(DealStage.WON)).isEqualTo(2L);
        assertThat(repBOutcomes.get(DealStage.LOST)).isNull();
    }

    @Test
    void wonRevenueSinceExcludesOlderDealsAndOtherReps() {
        BigDecimal orgWide = dealRepository.sumWonRevenueSince(DealStage.WON,
                LocalDate.now().minusDays(30), null);
        assertThat(orgWide).isEqualByComparingTo("30000");

        BigDecimal repAOnly = dealRepository.sumWonRevenueSince(DealStage.WON,
                LocalDate.now().minusDays(30), repA.getId());
        assertThat(repAOnly).isEqualByComparingTo("10000");
    }

    @Test
    void perRepGroupedQueriesProvideAdminRollupInputs() {
        Map<Object, Long> leadsByRep = toMap(leadRepository.countByRepGrouped());
        assertThat(leadsByRep.get(repA.getId())).isEqualTo(3L);
        assertThat(leadsByRep.get(repB.getId())).isEqualTo(1L);

        Map<Object, Long> customersByRep = toMap(customerRepository.countByRepGrouped());
        assertThat(customersByRep.get(repA.getId())).isEqualTo(1L);

        Map<Object, Long> openTasksByRep = toMap(taskRepository.countOpenByRepGrouped(
                List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS)));
        assertThat(openTasksByRep.get(repA.getId())).isEqualTo(1L);
        assertThat(openTasksByRep.get(repB.getId())).isEqualTo(1L);

        assertThat(dealRepository.dealSummaryByRepGrouped()).hasSize(4);
    }

    @Test
    void scopedSearchQueriesRespectRepAndPatternFilters() {
        assertThat(leadRepository.searchLeads("%lead%", null, null, repA.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(3);
        assertThat(leadRepository.searchLeads("%b1%", null, null, repA.getId(),
                org.springframework.data.domain.Pageable.unpaged())).isEmpty();
        assertThat(customerRepository.countScoped(repB.getId())).isEqualTo(1);
    }

    // ---- Fixture helpers ----

    private User saveUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("encoded");
        user.setRole(Role.SALES_REP);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private Lead saveLead(String name, LeadSource source, LeadStatus status, User rep) {
        Lead lead = new Lead();
        lead.setName(name);
        lead.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        lead.setSource(source);
        lead.setStatus(status);
        lead.setAssignedRep(rep);
        return leadRepository.save(lead);
    }

    private Customer saveCustomer(String name, User rep) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setAssignedRep(rep);
        return customerRepository.save(customer);
    }

    private Deal saveDeal(String title, Customer customer, User rep, DealStage stage,
                          String value, LocalDate actualCloseDate) {
        Deal deal = new Deal();
        deal.setTitle(title);
        deal.setCustomer(customer);
        deal.setAssignedRep(rep);
        deal.setStage(stage);
        deal.setProbability(stage.getDefaultProbability());
        deal.setValue(new BigDecimal(value));
        deal.setActualCloseDate(actualCloseDate);
        if (stage == DealStage.LOST) {
            deal.setLostReason("Test reason");
        }
        return dealRepository.save(deal);
    }

    private void saveTask(String title, User rep, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setDueAt(LocalDateTime.now().plusDays(1));
        task.setStatus(status);
        task.setPriority(TaskPriority.MEDIUM);
        task.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        task.setLinkedEntityId(1L);
        task.setLinkedEntityName("Test customer");
        task.setAssignedTo(rep);
        task.setCreatedBy(rep);
        taskRepository.save(task);
    }

    private static Map<Object, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row[0], row -> ((Number) row[1]).longValue()));
    }

    private static Map<Object, BigDecimal> toMapDecimal(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row[0], row -> (BigDecimal) row[1]));
    }
}
