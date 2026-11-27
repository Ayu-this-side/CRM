package com.crm.service;

import com.crm.dto.CustomerDto;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
import com.crm.enums.LeadStatus;
import com.crm.enums.Role;
import com.crm.repository.CustomerRepository;
import com.crm.repository.DealRepository;
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
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private CustomerService customerService;

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
    @DisplayName("createCustomer: sets assignedRep to currentUser when created by SALES_REP")
    void createCustomer_asSalesRep_setsRep() {
        CustomerDto dto = new CustomerDto();
        dto.setName("Direct Client");
        dto.setEmail("client@direct.com");
        dto.setStatus(CustomerStatus.ACTIVE);

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer created = customerService.createCustomer(dto, salesRep1);

        assertThat(created.getName()).isEqualTo("Direct Client");
        assertThat(created.getAssignedRep()).isEqualTo(salesRep1);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("findById: sales rep cannot access customer assigned to another rep")
    void findById_otherRepsCustomer_throwsAccessDenied() {
        Customer customer = new Customer();
        customer.setId(40L);
        customer.setName("Private Account");
        customer.setAssignedRep(salesRep2);

        when(customerRepository.findById(40L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.findById(40L, salesRep1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission");
    }

    @Test
    @DisplayName("findById: admin can view any customer regardless of assigned rep")
    void findById_admin_canAccessAnyCustomer() {
        Customer customer = new Customer();
        customer.setId(40L);
        customer.setName("Any Customer");
        customer.setAssignedRep(salesRep2);

        when(customerRepository.findById(40L)).thenReturn(Optional.of(customer));

        Customer found = customerService.findById(40L, adminUser);
        assertThat(found).isEqualTo(customer);
    }

    @Test
    @DisplayName("findCustomers: a sales rep cannot widen visibility with a forged repId")
    void findCustomers_asSalesRep_ignoresRequestedRepId() {
        when(customerRepository.searchCustomers(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        customerService.findCustomers("acme", null, salesRep2.getId(), salesRep1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        org.mockito.ArgumentCaptor<Long> repId = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(customerRepository).searchCustomers(any(), any(), repId.capture(), any());
        assertThat(repId.getValue()).isEqualTo(salesRep1.getId());
    }

    @Test
    @DisplayName("deleteCustomer: converted customers are kept to preserve lead traceability")
    void deleteCustomer_convertedOrigin_throws() {
        Customer customer = new Customer();
        customer.setId(41L);
        customer.setName("Converted Client");
        customer.setAssignedRep(salesRep1);
        Lead sourceLead = new Lead();
        sourceLead.setId(9L);
        sourceLead.setStatus(LeadStatus.CONVERTED);
        customer.setOriginLead(sourceLead);

        when(customerRepository.findById(41L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.deleteCustomer(41L, salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be deleted");
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("updateCustomer: blank rep selection keeps the existing owner")
    void updateCustomer_adminLeavesRepBlank_keepsOwner() {
        Customer customer = new Customer();
        customer.setId(42L);
        customer.setName("Existing Client");
        customer.setEmail("client@example.com");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setAssignedRep(salesRep1);

        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDto dto = new CustomerDto();
        dto.setName("Existing Client");
        dto.setEmail("client@example.com");
        dto.setStatus(CustomerStatus.ACTIVE);

        Customer updated = customerService.updateCustomer(42L, dto, adminUser);

        assertThat(updated.getAssignedRep()).isEqualTo(salesRep1);
        verify(userRepository, never()).findByIdAndRoleAndEnabledTrue(any(), any());
    }

    @Test
    @DisplayName("deleteCustomer: a customer with deals is kept to preserve deal history")
    void deleteCustomer_withDeals_throws() {
        Customer customer = new Customer();
        customer.setId(43L);
        customer.setName("Client With Deals");
        customer.setAssignedRep(salesRep1);

        when(customerRepository.findById(43L)).thenReturn(Optional.of(customer));
        when(dealRepository.countByCustomerId(43L)).thenReturn(2L);

        assertThatThrownBy(() -> customerService.deleteCustomer(43L, salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has deals");
        verify(customerRepository, never()).delete(any());
    }
}
