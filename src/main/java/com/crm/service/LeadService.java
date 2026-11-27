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
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.CustomerRepository;
import com.crm.repository.LeadRepository;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for Lead management.
 * Enforces role-based data scoping and pipeline transition validations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------
    // Queries
    // -------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Lead> findLeads(String keyword,
                                LeadStatus status,
                                LeadSource source,
                                Long repId,
                                User currentUser,
                                Pageable pageable) {
        Long effectiveRepId = resolveEffectiveRepId(repId, currentUser);
        String pattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        return leadRepository.searchLeads(pattern, status, source, effectiveRepId, pageable);
    }

    @Transactional(readOnly = true)
    public Lead findById(Long id, User currentUser) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with id: " + id));

        verifyLeadOwnership(lead, currentUser);
        return lead;
    }

    @Transactional(readOnly = true)
    public long countTotalLeads(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return leadRepository.count();
        } else {
            return leadRepository.countByAssignedRepId(currentUser.getId());
        }
    }

    // -------------------------------------------------
    // Mutations
    // -------------------------------------------------

    @Transactional
    public Lead createLead(LeadDto dto, User currentUser) {
        if (dto.getStatus() == LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("New leads cannot be created with CONVERTED status.");
        }

        Lead lead = new Lead();
        lead.setName(dto.getName().trim());
        lead.setCompany(trimToNull(dto.getCompany()));
        lead.setEmail(dto.getEmail().trim().toLowerCase());
        lead.setPhone(trimToNull(dto.getPhone()));
        lead.setSource(dto.getSource());
        lead.setStatus(dto.getStatus() != null ? dto.getStatus() : LeadStatus.NEW);

        // Rep assignment scoping — admins may only assign active Sales Reps
        if (currentUser.getRole() == Role.SALES_REP) {
            lead.setAssignedRep(currentUser);
        } else {
            lead.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId(), currentUser));
        }

        Lead saved = leadRepository.save(lead);
        log.info("Created lead [id={}] name='{}' rep={}",
                saved.getId(), saved.getName(),
                saved.getAssignedRep() != null ? saved.getAssignedRep().getEmail() : "none");
        return saved;
    }

    @Transactional
    public Lead updateLead(Long id, LeadDto dto, User currentUser) {
        Lead lead = findById(id, currentUser);

        if (lead.isConverted()) {
            throw new IllegalStateException("Converted leads are locked and cannot be edited.");
        }

        LeadStatus targetStatus = dto.getStatus() != null ? dto.getStatus() : lead.getStatus();
        if (targetStatus == LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("Cannot mark lead as CONVERTED directly. Please use the 'Convert to Customer' action.");
        }

        if (!lead.getStatus().canTransitionTo(targetStatus)) {
            throw new IllegalArgumentException(String.format("Invalid status transition from '%s' to '%s'.",
                    lead.getStatus().getDisplayName(), targetStatus.getDisplayName()));
        }

        lead.setName(dto.getName().trim());
        lead.setCompany(trimToNull(dto.getCompany()));
        lead.setEmail(dto.getEmail().trim().toLowerCase());
        lead.setPhone(trimToNull(dto.getPhone()));
        lead.setSource(dto.getSource());
        lead.setStatus(targetStatus);

        // Admin can reassign to another active rep; a blank selection keeps the current owner.
        if (currentUser.getRole() == Role.ADMIN && dto.getAssignedRepId() != null) {
            lead.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId(), currentUser));
        }

        Lead updated = leadRepository.save(lead);
        log.info("Updated lead [id={}] status={}", updated.getId(), updated.getStatus());
        return updated;
    }

    @Transactional
    public Lead updateStatus(Long id, LeadStatus newStatus, User currentUser) {
        Lead lead = findById(id, currentUser);

        if (lead.isConverted()) {
            throw new IllegalStateException("Converted leads are locked and cannot have their status modified.");
        }

        if (newStatus == null) {
            throw new IllegalArgumentException("A target status is required.");
        }
        if (newStatus == LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("Please use the 'Convert to Customer' action to convert this lead.");
        }

        if (!lead.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(String.format("Invalid status transition from '%s' to '%s'.",
                    lead.getStatus().getDisplayName(), newStatus.getDisplayName()));
        }

        lead.setStatus(newStatus);
        Lead updated = leadRepository.save(lead);
        log.info("Advanced lead [id={}] to status={}", updated.getId(), updated.getStatus());
        return updated;
    }

    @Transactional
    public Customer convertToCustomer(LeadConvertDto dto, User currentUser) {
        Lead lead = findById(dto.getLeadId(), currentUser);

        if (lead.isConverted()) {
            throw new IllegalStateException("This lead has already been converted to a customer.");
        }
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            throw new IllegalStateException(
                    "Only QUALIFIED leads can be converted. Move the lead through the pipeline first.");
        }

        if (customerRepository.findByOriginLeadId(lead.getId()).isPresent()) {
            throw new IllegalStateException("A customer already exists linked to this lead.");
        }

        Customer customer = new Customer();
        customer.setName(dto.getName().trim());
        customer.setCompany(trimToNull(dto.getCompany()));
        customer.setEmail(dto.getEmail().trim().toLowerCase());
        customer.setPhone(trimToNull(dto.getPhone()));
        customer.setAddress(trimToNull(dto.getAddress()));
        customer.setStatus(dto.getStatus() != null ? dto.getStatus() : CustomerStatus.ACTIVE);
        customer.setOriginLead(lead);

        // Rep assignment — keep the converted pair under the same owner unless
        // the admin explicitly selects another active Sales Rep.
        if (currentUser.getRole() == Role.SALES_REP) {
            customer.setAssignedRep(currentUser);
        } else if (dto.getAssignedRepId() != null) {
            customer.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId(), currentUser));
        } else {
            customer.setAssignedRep(lead.getAssignedRep() != null ? lead.getAssignedRep() : currentUser);
        }

        Customer savedCustomer = customerRepository.save(customer);

        // Mark lead as CONVERTED
        lead.setStatus(LeadStatus.CONVERTED);
        leadRepository.save(lead);

        log.info("Successfully converted lead [id={}] to customer [id={}]", lead.getId(), savedCustomer.getId());
        return savedCustomer;
    }

    @Transactional
    public void deleteLead(Long id, User currentUser) {
        Lead lead = findById(id, currentUser);

        if (lead.isConverted()) {
            throw new IllegalStateException("Cannot delete a lead that has been converted to a customer.");
        }

        leadRepository.delete(lead);
        log.info("Deleted lead [id={}]", id);
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

    private void verifyLeadOwnership(Lead lead, User currentUser) {
        if (currentUser.getRole() == Role.SALES_REP) {
            if (lead.getAssignedRep() == null || !lead.getAssignedRep().getId().equals(currentUser.getId())) {
                log.warn("Access denied: User [{}] attempted to access Lead [{}] owned by [{}]",
                        currentUser.getEmail(), lead.getId(),
                        lead.getAssignedRep() != null ? lead.getAssignedRep().getId() : "unassigned");
                throw new AccessDeniedException("You do not have permission to access this lead.");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
