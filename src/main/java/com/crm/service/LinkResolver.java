package com.crm.service;

import com.crm.dto.LinkedRecord;
import com.crm.entity.Customer;
import com.crm.entity.Deal;
import com.crm.entity.Lead;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a polymorphic (LinkedEntityType, id) pair into display data.
 *
 * Every lookup goes through the owning service's findById, so a rep cannot
 * attach a task/log/email to a record they are not allowed to see.
 */
@Component
@RequiredArgsConstructor
public class LinkResolver {

    private final LeadService leadService;
    private final CustomerService customerService;
    private final DealService dealService;

    @Transactional(readOnly = true)
    public LinkedRecord resolve(LinkedEntityType type, Long id, User currentUser) {
        if (type == null || id == null) {
            throw new IllegalArgumentException("A linked record is required.");
        }
        return switch (type) {
            case LEAD -> {
                Lead lead = leadService.findById(id, currentUser);
                yield new LinkedRecord(lead.getName(), lead.getEmail(), type.detailPath(id));
            }
            case CUSTOMER -> {
                Customer customer = customerService.findById(id, currentUser);
                yield new LinkedRecord(customer.getName(), customer.getEmail(), type.detailPath(id));
            }
            case DEAL -> {
                Deal deal = dealService.findById(id, currentUser);
                yield new LinkedRecord(deal.getTitle(), deal.getCustomer().getEmail(), type.detailPath(id));
            }
        };
    }
}
