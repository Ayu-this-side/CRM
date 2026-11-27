package com.crm.service;

import com.crm.dto.EmailDto;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private CommunicationLogService communicationLogService;

    private EmailService service;
    private User sender;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, communicationLogService);
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "from", "crm@localhost");
        sender = new User();
        sender.setId(2L);
        sender.setName("Rep One");
        sender.setEmail("rep@crm.com");
        sender.setRole(Role.SALES_REP);
    }

    private EmailDto dto() {
        EmailDto dto = new EmailDto();
        dto.setRecipientEmail("  client@example.com  ");
        dto.setSubject("  Follow-up  ");
        dto.setBody("  Hello there  ");
        dto.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        dto.setLinkedEntityId(10L);
        return dto;
    }

    @Test
    @DisplayName("Dev mode: simulated email is logged but never sent over SMTP")
    void sendEmail_simulated_logsButDoesNotSend() {
        service.sendEmail(sender, dto());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(communicationLogService).logEmail(sender, "client@example.com", "Follow-up",
                "Hello there", LinkedEntityType.CUSTOMER, 10L);
    }

    @Test
    @DisplayName("Enabled mail: sends via JavaMailSender and logs the EMAIL entry")
    void sendEmail_enabled_sendsAndLogs() {
        ReflectionTestUtils.setField(service, "enabled", true);

        service.sendEmail(sender, dto());

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(communicationLogService).logEmail(eq(sender), eq("client@example.com"), eq("Follow-up"),
                eq("Hello there"), eq(LinkedEntityType.CUSTOMER), eq(10L));
    }

    @Test
    @DisplayName("sendEmail: blank recipient/subject/body is rejected before delivery")
    void sendEmail_blankFields_rejected() {
        EmailDto dto = dto();
        dto.setBody("   ");

        assertThatThrownBy(() -> service.sendEmail(sender, dto))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(communicationLogService, never()).logEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendTaskDigest: empty digest does nothing")
    void sendTaskDigest_empty_noSend() {
        service.sendTaskDigest(sender, List.of());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendTaskDigest: lists each due task with its linked record")
    void sendTaskDigest_dueTasks_sends() {
        ReflectionTestUtils.setField(service, "enabled", true);
        Task task = new Task();
        task.setTitle("Call Acme");
        task.setDueAt(LocalDateTime.of(2027, 1, 1, 8, 0));
        task.setStatus(TaskStatus.PENDING);
        task.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        task.setLinkedEntityName("Acme Corp");

        service.sendTaskDigest(sender, List.of(task));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Email without a linked CRM record is still deliverable, but not logged")
    void sendEmail_withoutLink_notLoggedAgainstRecord() {
        EmailDto dto = dto();
        dto.setLinkedEntityType(null);
        dto.setLinkedEntityId(null);

        service.sendEmail(sender, dto);

        verify(communicationLogService).logEmail(eq(sender), eq("client@example.com"), eq("Follow-up"),
                eq("Hello there"), isNull(), isNull());
    }
}
