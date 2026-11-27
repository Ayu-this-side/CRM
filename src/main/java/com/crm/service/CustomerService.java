package com.crm.service;

import com.crm.dto.CustomerDto;
import com.crm.entity.Customer;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
import com.crm.enums.Role;
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.CustomerRepository;
import com.crm.repository.DealRepository;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Customer management.
 * Enforces role-based visibility and customer CRUD operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final DealRepository dealRepository;

    // -------------------------------------------------
    // Queries
    // -------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Customer> findCustomers(String keyword,
                                        CustomerStatus status,
                                        Long repId,
                                        User currentUser,
                                        Pageable pageable) {
        Long effectiveRepId = resolveEffectiveRepId(repId, currentUser);
        String pattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        return customerRepository.searchCustomers(pattern, status, effectiveRepId, pageable);
    }

    /** Customers available for deal assignment in dropdowns, scoped by role. */
    @Transactional(readOnly = true)
    public List<Customer> findSelectableCustomers(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return customerRepository.findAllByOrderByNameAsc();
        }
        return customerRepository.findByAssignedRepIdOrderByNameAsc(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public Customer findById(Long id, User currentUser) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));

        verifyCustomerOwnership(customer, currentUser);
        return customer;
    }

    @Transactional(readOnly = true)
    public long countActiveCustomers(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return customerRepository.countByStatus(CustomerStatus.ACTIVE);
        } else {
            return customerRepository.countByAssignedRepIdAndStatus(currentUser.getId(), CustomerStatus.ACTIVE);
        }
    }

    @Transactional(readOnly = true)
    public long countTotalCustomers(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return customerRepository.count();
        } else {
            return customerRepository.countByAssignedRepId(currentUser.getId());
        }
    }

    // -------------------------------------------------
    // Mutations
    // -------------------------------------------------

    @Transactional
    public Customer createCustomer(CustomerDto dto, User currentUser) {
        Customer customer = new Customer();
        customer.setName(dto.getName().trim());
        customer.setCompany(trimToNull(dto.getCompany()));
        customer.setEmail(dto.getEmail().trim().toLowerCase());
        customer.setPhone(trimToNull(dto.getPhone()));
        customer.setAddress(trimToNull(dto.getAddress()));
        customer.setStatus(dto.getStatus() != null ? dto.getStatus() : CustomerStatus.ACTIVE);

        if (currentUser.getRole() == Role.SALES_REP) {
            customer.setAssignedRep(currentUser);
        } else {
            customer.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId(), currentUser));
        }

        Customer saved = customerRepository.save(customer);
        log.info("Created customer [id={}] name='{}' rep={}",
                saved.getId(), saved.getName(),
                saved.getAssignedRep() != null ? saved.getAssignedRep().getEmail() : "none");
        return saved;
    }

    @Transactional
    public Customer updateCustomer(Long id, CustomerDto dto, User currentUser) {
        Customer customer = findById(id, currentUser);

        customer.setName(dto.getName().trim());
        customer.setCompany(trimToNull(dto.getCompany()));
        customer.setEmail(dto.getEmail().trim().toLowerCase());
        customer.setPhone(trimToNull(dto.getPhone()));
        customer.setAddress(trimToNull(dto.getAddress()));
        customer.setStatus(dto.getStatus() != null ? dto.getStatus() : customer.getStatus());

        // Blank selection keeps the existing owner — records must not be orphaned.
        if (currentUser.getRole() == Role.ADMIN && dto.getAssignedRepId() != null) {
            customer.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId(), currentUser));
        }

        Customer updated = customerRepository.save(customer);
        log.info("Updated customer [id={}]", updated.getId());
        return updated;
    }

    @Transactional
    public void deleteCustomer(Long id, User currentUser) {
        Customer customer = findById(id, currentUser);

        if (customer.getOriginLead() != null) {
            throw new IllegalStateException(
                    "Converted customers cannot be deleted: the link to the source lead must be preserved.");
        }
        if (dealRepository.countByCustomerId(id) > 0) {
            throw new IllegalStateException(
                    "This customer has deals and cannot be deleted. Close or keep the deal history intact.");
        }

        customerRepository.delete(customer);
        log.info("Deleted customer [id={}]", id);
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private Long resolveEffectiveRepId(Long requestedRepId, User currentUser) {
        if (currentUser.getRole() == Role.SALES_REP) {
            return currentUser.getId();
        }
        return requestedRepId;
    }

    private User resolveAssignableRep(Long repId, User currentUser) {
        if (repId == null) {
            return currentUser;
        }
        return userRepository.findByIdAndRoleAndEnabledTrue(repId, Role.SALES_REP)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only active Sales Rep accounts can be assigned to records (id: " + repId + ")."));
    }

    private void verifyCustomerOwnership(Customer customer, User currentUser) {
        if (currentUser.getRole() == Role.SALES_REP) {
            if (customer.getAssignedRep() == null || !customer.getAssignedRep().getId().equals(currentUser.getId())) {
                log.warn("Access denied: User [{}] attempted to access Customer [{}] owned by [{}]",
                        currentUser.getEmail(), customer.getId(),
                        customer.getAssignedRep() != null ? customer.getAssignedRep().getId() : "unassigned");
                throw new AccessDeniedException("You do not have permission to access this customer.");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
