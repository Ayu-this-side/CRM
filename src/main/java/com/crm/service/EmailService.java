package com.crm.service;

import com.crm.dto.EmailDto;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends CRM email and writes a matching EMAIL communication log.
 *
 * Dev default is simulate-only ({@code crm.mail.enabled=false}): nothing leaves
 * the machine, but the log entry is still created so the activity feed is
 * realistic. Set {@code crm.mail.enabled=true} plus SMTP env vars to send for real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final CommunicationLogService communicationLogService;

    @Value("${crm.mail.enabled:false}")
    private boolean enabled;

    @Value("${crm.mail.from:crm@localhost}")
    private String from;

    public void sendEmail(User sender, EmailDto dto) {
        String recipient = dto.getRecipientEmail() == null ? "" : dto.getRecipientEmail().trim();
        String subject = dto.getSubject() == null ? "" : dto.getSubject().trim();
        String body = dto.getBody() == null ? "" : dto.getBody().trim();
        if (recipient.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            throw new IllegalArgumentException("Recipient, subject and message body are required.");
        }

        deliver(recipient, subject, body,
                "Email to " + recipient + " (subject: " + subject + ")");

        communicationLogService.logEmail(sender, recipient, subject, body,
                dto.getLinkedEntityType(), dto.getLinkedEntityId());
    }

    /** One digest per rep, listing their due/overdue open tasks. Not logged as an activity. */
    public void sendTaskDigest(User recipient, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder("Hi " + recipient.getName() + ",\n\n")
                .append("You have ").append(tasks.size())
                .append(" task(s) due or overdue in the CRM:\n\n");
        for (Task task : tasks) {
            body.append("• ").append(task.getTitle())
                    .append(" — due ").append(task.getDueAt())
                    .append(" — ").append(task.getStatus().getDisplayName())
                    .append(" (").append(task.getLinkedEntityType().getDisplayName())
                    .append(": ").append(task.getLinkedEntityName()).append(")\n");
        }
        body.append("\nOpen My Tasks in the CRM to update them.\n");

        deliver(recipient.getEmail(), "CRM daily task digest", body.toString(),
                "Task digest to " + recipient.getEmail() + " (" + tasks.size() + " task(s))");
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void deliver(String to, String subject, String text, String description) {
        if (!enabled) {
            log.info("[SIMULATED EMAIL] {} — set crm.mail.enabled=true to send via SMTP.", description);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent: {}", description);
        } catch (MailException ex) {
            log.error("Email delivery failed ({}): {}", description, ex.getMessage());
            throw new IllegalStateException("The email could not be sent. Please check the mail configuration.", ex);
        }
    }
}
