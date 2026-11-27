package com.crm.controller;

import com.crm.entity.User;
import com.crm.service.CustomerService;
import com.crm.service.DealService;
import com.crm.service.LeadService;
import com.crm.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles the root "/" path — dashboard landing page after login.
 * Displays KPI summary cards scoped to the current user's role.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final LeadService leadService;
    private final CustomerService customerService;
    private final DealService dealService;
    private final TaskService taskService;

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal User currentUser, Model model) {
        long totalLeads = leadService.countTotalLeads(currentUser);
        long activeCustomers = customerService.countActiveCustomers(currentUser);
        long openDeals = dealService.countOpenDeals(currentUser);
        long openTasks = taskService.countOpenTasks(currentUser);

        model.addAttribute("totalLeads", totalLeads);
        model.addAttribute("activeCustomers", activeCustomers);
        model.addAttribute("openDeals", openDeals);
        model.addAttribute("openTasks", openTasks);
        model.addAttribute("activeMenu", "dashboard");
        return "home/dashboard";
    }
}
