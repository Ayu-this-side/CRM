package com.crm.config;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo accounts and sample data on a fresh database only.
 * Disabled with {@code crm.seed.enabled=false} (recommended outside local development)
 * so a renamed/deleted demo admin can never be recreated with a known password.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL    = "admin@crm.com";
    private static final String ADMIN_PASSWORD = "admin123";

    private static final String REP_EMAIL      = "rep@crm.com";
    private static final String REP_PASSWORD   = "rep123";

    @Value("${crm.seed.enabled:true}")
    private boolean seedEnabled;

    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Demo data seeding is disabled (crm.seed.enabled=false).");
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Users already exist — skipping demo account seeding.");
            return;
        }

        User admin = seedDefaultAdmin();
        User rep = seedDefaultRep();
        seedDemoData(admin, rep);
    }

    private User seedDefaultAdmin() {
        User admin = new User();
        admin.setName("Admin User");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        User saved = userRepository.save(admin);
        log.info("Seeded demo admin account [id={}] email={}", saved.getId(), ADMIN_EMAIL);
        return saved;
    }

    private User seedDefaultRep() {
        User rep = new User();
        rep.setName("John Rep");
        rep.setEmail(REP_EMAIL);
        rep.setPassword(passwordEncoder.encode(REP_PASSWORD));
        rep.setRole(Role.SALES_REP);
        rep.setEnabled(true);
        User saved = userRepository.save(rep);
        log.info("Seeded demo sales rep account [id={}] email={}", saved.getId(), REP_EMAIL);
        return saved;
    }

    private void seedDemoData(User admin, User rep) {
        if (leadRepository.count() > 0) {
            return;
        }

        log.info("Seeding demonstration Leads and Customers for Phase 2...");

        // Lead 1: New inbound lead assigned to Rep
        Lead lead1 = new Lead();
        lead1.setName("Alice Montgomery");
        lead1.setCompany("Apex Technologies");
        lead1.setEmail("alice.m@apextech.io");
        lead1.setPhone("+1 (555) 234-5678");
        lead1.setSource(LeadSource.WEBSITE);
        lead1.setStatus(LeadStatus.NEW);
        lead1.setAssignedRep(rep);
        leadRepository.save(lead1);

        // Lead 2: Contacted lead assigned to Rep
        Lead lead2 = new Lead();
        lead2.setName("Brian Vance");
        lead2.setCompany("Vance Refrigeration");
        lead2.setEmail("brian@vancerefrig.com");
        lead2.setPhone("+1 (555) 876-5432");
        lead2.setSource(LeadSource.REFERRAL);
        lead2.setStatus(LeadStatus.CONTACTED);
        lead2.setAssignedRep(rep);
        leadRepository.save(lead2);

        // Lead 3: Qualified enterprise lead assigned to Admin
        Lead lead3 = new Lead();
        lead3.setName("Carla Rossi");
        lead3.setCompany("Starlight Dynamics");
        lead3.setEmail("c.rossi@starlightdyn.com");
        lead3.setPhone("+1 (555) 432-1098");
        lead3.setSource(LeadSource.EVENT);
        lead3.setStatus(LeadStatus.QUALIFIED);
        lead3.setAssignedRep(admin);
        leadRepository.save(lead3);

        // Lead 4: Lost lead assigned to Rep
        Lead lead4 = new Lead();
        lead4.setName("David Cho");
        lead4.setCompany("Novalogic Labs");
        lead4.setEmail("david@novalogic.net");
        lead4.setPhone("+1 (555) 901-2345");
        lead4.setSource(LeadSource.COLD_CALL);
        lead4.setStatus(LeadStatus.LOST);
        lead4.setAssignedRep(rep);
        leadRepository.save(lead4);

        // Lead 5: Converted lead with matching Customer
        Lead lead5 = new Lead();
        lead5.setName("Elena Rostova");
        lead5.setCompany("Solaria Energy Solutions");
        lead5.setEmail("elena@solariaenergy.com");
        lead5.setPhone("+1 (555) 345-6789");
        lead5.setSource(LeadSource.WEBSITE);
        lead5.setStatus(LeadStatus.CONVERTED);
        lead5.setAssignedRep(rep);
        lead5 = leadRepository.save(lead5);

        Customer customer1 = new Customer();
        customer1.setName(lead5.getName());
        customer1.setCompany(lead5.getCompany());
        customer1.setEmail(lead5.getEmail());
        customer1.setPhone(lead5.getPhone());
        customer1.setAddress("742 Evergreen Terrace, Suite 100, Austin, TX");
        customer1.setStatus(CustomerStatus.ACTIVE);
        customer1.setOriginLead(lead5);
        customer1.setAssignedRep(rep);
        customerRepository.save(customer1);

        // Customer 2: Direct enterprise customer created under Admin
        Customer customer2 = new Customer();
        customer2.setName("Marcus Sterling");
        customer2.setCompany("Sterling & Partners Law");
        customer2.setEmail("m.sterling@sterlinglaw.com");
        customer2.setPhone("+1 (555) 678-9012");
        customer2.setAddress("450 Lexington Avenue, 28th Floor, New York, NY");
        customer2.setStatus(CustomerStatus.ACTIVE);
        customer2.setAssignedRep(admin);
        customerRepository.save(customer2);

        log.info("✅ Phase 2 demonstration leads and customers seeded successfully.");
    }
}
