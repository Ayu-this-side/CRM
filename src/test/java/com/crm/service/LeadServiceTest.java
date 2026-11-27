package com.crm.service;

import com.crm.dto.LeadConvertDto;
import com.crm.dto.LeadDto;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import com.crm.enums.Role;
import com.crm.repository.CustomerRepository;
import com.crm.repository.LeadRepository;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeadService leadService;

    private User adminUser;
    private User salesRep1;
    private User salesRep2;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setName("Admin");
        adminUser.setEmail("admin@crm.com");
        adminUser.setRole(Role.ADMIN);

        salesRep1 = new User();
        salesRep1.setId(2L);
        salesRep1.setName("Alice Rep");
        salesRep1.setEmail("alice@crm.com");
        salesRep1.setRole(Role.SALES_REP);

        salesRep2 = new User();
        salesRep2.setId(3L);
        salesRep2.setName("Bob Rep");
        salesRep2.setEmail("bob@crm.com");
        salesRep2.setRole(Role.SALES_REP);
    }

    @Test
    @DisplayName("createLead: automatically scopes assignedRep to current user when current user is SALES_REP")
    void createLead_asSalesRep_setsCurrentUserAsAssignedRep() {
        LeadDto dto = new LeadDto();
        dto.setName("Acme Prospect");
        dto.setEmail("prospect@acme.com");
        dto.setSource(LeadSource.WEBSITE);
        dto.setStatus(LeadStatus.NEW);

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead created = leadService.createLead(dto, salesRep1);

        assertThat(created.getName()).isEqualTo("Acme Prospect");
        assertThat(created.getAssignedRep()).isEqualTo(salesRep1);
        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("createLead: fails if status is set to CONVERTED on creation")
    void createLead_withConvertedStatus_throwsException() {
        LeadDto dto = new LeadDto();
        dto.setName("Direct Prospect");
        dto.setEmail("prospect@direct.com");
        dto.setSource(LeadSource.REFERRAL);
        dto.setStatus(LeadStatus.CONVERTED);

        assertThatThrownBy(() -> leadService.createLead(dto, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New leads cannot be created with CONVERTED status");
    }

    @Test
    @DisplayName("findById: sales rep cannot view lead assigned to another rep")
    void findById_otherRepsLead_throwsAccessDenied() {
        Lead lead = new Lead();
        lead.setId(10L);
        lead.setName("Secret Lead");
        lead.setAssignedRep(salesRep2);

        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> leadService.findById(10L, salesRep1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission");
    }

    @Test
    @DisplayName("updateLead: blocks transition to CONVERTED directly via standard update")
    void updateLead_directTransitionToConverted_throwsException() {
        Lead lead = new Lead();
        lead.setId(20L);
        lead.setName("Qual Lead");
        lead.setStatus(LeadStatus.QUALIFIED);
        lead.setAssignedRep(salesRep1);

        when(leadRepository.findById(20L)).thenReturn(Optional.of(lead));

        LeadDto dto = new LeadDto();
        dto.setName("Qual Lead");
        dto.setEmail("qual@test.com");
        dto.setSource(LeadSource.EVENT);
        dto.setStatus(LeadStatus.CONVERTED); // Direct transition attempt

        assertThatThrownBy(() -> leadService.updateLead(20L, dto, salesRep1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot mark lead as CONVERTED directly");
    }

    @Test
    @DisplayName("updateLead: blocks modification if lead is already CONVERTED")
    void updateLead_alreadyConverted_throwsException() {
        Lead lead = new Lead();
        lead.setId(30L);
        lead.setName("Converted Lead");
        lead.setStatus(LeadStatus.CONVERTED);
        lead.setAssignedRep(salesRep1);

        when(leadRepository.findById(30L)).thenReturn(Optional.of(lead));

        LeadDto dto = new LeadDto();
        dto.setName("Converted Lead Modified");
        dto.setEmail("test@test.com");
        dto.setSource(LeadSource.OTHER);
        dto.setStatus(LeadStatus.CONVERTED);

        assertThatThrownBy(() -> leadService.updateLead(30L, dto, salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Converted leads are locked");
    }

    @Test
    @DisplayName("convertToCustomer: successfully converts lead, creates customer, and marks lead CONVERTED")
    void convertToCustomer_success() {
        Lead lead = new Lead();
        lead.setId(50L);
        lead.setName("Hot Opportunity");
        lead.setCompany("Future Unicorn");
        lead.setEmail("ceo@unicorn.com");
        lead.setPhone("+1 555-9999");
        lead.setStatus(LeadStatus.QUALIFIED);
        lead.setAssignedRep(salesRep1);

        when(leadRepository.findById(50L)).thenReturn(Optional.of(lead));
        when(customerRepository.findByOriginLeadId(50L)).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        LeadConvertDto convertDto = new LeadConvertDto();
        convertDto.setLeadId(50L);
        convertDto.setName(lead.getName());
        convertDto.setCompany(lead.getCompany());
        convertDto.setEmail(lead.getEmail());
        convertDto.setPhone(lead.getPhone());
        convertDto.setAddress("100 Innovation Way");
        convertDto.setStatus(CustomerStatus.ACTIVE);

        Customer customer = leadService.convertToCustomer(convertDto, salesRep1);

        assertThat(customer).isNotNull();
        assertThat(customer.getId()).isEqualTo(100L);
        assertThat(customer.getName()).isEqualTo("Hot Opportunity");
        assertThat(customer.getOriginLead()).isEqualTo(lead);
        assertThat(customer.getAssignedRep()).isEqualTo(salesRep1);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        verify(leadRepository).save(lead);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("findLeads: a sales rep cannot widen visibility with a forged repId")
    void findLeads_asSalesRep_ignoresRequestedRepId() {
        when(leadRepository.searchLeads(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        leadService.findLeads("acme", null, null, salesRep2.getId(), salesRep1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        org.mockito.ArgumentCaptor<Long> repId = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(leadRepository).searchLeads(any(), any(), any(), repId.capture(), any());
        assertThat(repId.getValue()).isEqualTo(salesRep1.getId());
    }

    @Test
    @DisplayName("convertToCustomer: a lead that has not reached QUALIFIED cannot be converted")
    void convertToCustomer_fromNewLead_throwsAndDoesNotCreateCustomer() {
        Lead lead = new Lead();
        lead.setId(60L);
        lead.setName("Too Early");
        lead.setEmail("early@example.com");
        lead.setStatus(LeadStatus.NEW);
        lead.setAssignedRep(salesRep1);

        when(leadRepository.findById(60L)).thenReturn(Optional.of(lead));

        LeadConvertDto dto = new LeadConvertDto();
        dto.setLeadId(60L);
        dto.setName("Too Early");
        dto.setEmail("early@example.com");
        dto.setStatus(CustomerStatus.ACTIVE);

        assertThatThrownBy(() -> leadService.convertToCustomer(dto, salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUALIFIED");
        verifyNoInteractions(customerRepository);
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("createLead: admin cannot assign a record to an Admin or inactive user")
    void createLead_asAdmin_withNonRepAssignee_throws() {
        LeadDto dto = new LeadDto();
        dto.setName("Acme");
        dto.setEmail("acme@example.com");
        dto.setSource(LeadSource.WEBSITE);
        dto.setStatus(LeadStatus.NEW);
        dto.setAssignedRepId(adminUser.getId());

        when(userRepository.findByIdAndRoleAndEnabledTrue(adminUser.getId(), Role.SALES_REP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.createLead(dto, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active Sales Rep");
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateLead: blank rep selection keeps the existing owner")
    void updateLead_adminLeavesRepBlank_keepsOwner() {
        Lead lead = new Lead();
        lead.setId(70L);
        lead.setName("Owned Lead");
        lead.setEmail("owned@example.com");
        lead.setSource(LeadSource.WEBSITE);
        lead.setStatus(LeadStatus.NEW);
        lead.setAssignedRep(salesRep1);

        when(leadRepository.findById(70L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadDto dto = new LeadDto();
        dto.setName("Owned Lead");
        dto.setEmail("owned@example.com");
        dto.setSource(LeadSource.WEBSITE);
        dto.setStatus(LeadStatus.CONTACTED);

        Lead updated = leadService.updateLead(70L, dto, adminUser);

        assertThat(updated.getAssignedRep()).isEqualTo(salesRep1);
        verify(userRepository, never()).findByIdAndRoleAndEnabledTrue(any(), any());
    }
}
