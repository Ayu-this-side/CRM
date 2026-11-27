package com.crm.service;

import com.crm.dto.CommunicationLogDto;
import com.crm.dto.LinkedRecord;
import com.crm.entity.CommunicationLog;
import com.crm.entity.User;
import com.crm.enums.CommunicationType;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.CommunicationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Records manual interactions and the EMAIL entries created by EmailService. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationLogService {

    private final CommunicationLogRepository communicationLogRepository;
    private final LinkResolver linkResolver;

    @Transactional(readOnly = true)
    public List<CommunicationLog> findByEntity(LinkedEntityType type, Long entityId, User currentUser) {
        linkResolver.resolve(type, entityId, currentUser); // validates existence + visibility
        return communicationLogRepository.findByLinkedEntity(type, entityId);
    }

    @Transactional
    public CommunicationLog logInteraction(CommunicationLogDto dto, User currentUser) {
        LinkedRecord link = linkResolver.resolve(dto.getLinkedEntityType(), dto.getLinkedEntityId(), currentUser);
        CommunicationType type = dto.getType() != null ? dto.getType() : CommunicationType.NOTE;

        CommunicationLog entry = buildEntry(currentUser, type, dto.getSubject(), dto.getNotes(),
                dto.getRecipientEmail(), dto.getLinkedEntityType(), dto.getLinkedEntityId(), link);
        CommunicationLog saved = communicationLogRepository.save(entry);
        log.info("Logged {} [id={}] against {} [{}]", type, saved.getId(),
                dto.getLinkedEntityType(), dto.getLinkedEntityId());
        return saved;
    }

    /** Used by EmailService after a successful (or simulated) send. */
    @Transactional
    public CommunicationLog logEmail(User sender,
                                     String recipient,
                                     String subject,
                                     String body,
                                     LinkedEntityType type,
                                     Long entityId) {
        LinkedRecord link = (type == null || entityId == null)
                ? null
                : linkResolver.resolve(type, entityId, sender);
        if (link == null) {
            return null; // no CRM record selected — nothing to attach an activity entry to
        }
        CommunicationLog saved = communicationLogRepository.save(buildEntry(
                sender, CommunicationType.EMAIL, subject, body, recipient, type, entityId, link));
        log.info("Logged EMAIL [id={}] against {} [{}]", saved.getId(), type, entityId);
        return saved;
    }

    /** Deletes an entry and returns the linked record's page, for the redirect. */
    @Transactional
    public String delete(Long id, User currentUser) {
        CommunicationLog entry = communicationLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Communication log not found with id: " + id));
        if (currentUser.getRole() != Role.ADMIN
                && (entry.getLoggedBy() == null || !entry.getLoggedBy().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("You do not have permission to delete this activity entry.");
        }
        String path = entry.getLinkedEntityType().detailPath(entry.getLinkedEntityId());
        communicationLogRepository.delete(entry);
        log.info("Deleted communication log [id={}]", id);
        return path;
    }

    private CommunicationLog buildEntry(User user,
                                        CommunicationType type,
                                        String subject,
                                        String notes,
                                        String recipient,
                                        LinkedEntityType entityType,
                                        Long entityId,
                                        LinkedRecord link) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Notes are required for an activity entry.");
        }
        CommunicationLog entry = new CommunicationLog();
        entry.setType(type);
        entry.setSubject(trimToNull(subject));
        entry.setNotes(notes.trim());
        entry.setLinkedEntityType(entityType);
        entry.setLinkedEntityId(entityId);
        entry.setLinkedEntityName(link.displayName());
        entry.setRecipientEmail(trimToNull(recipient));
        entry.setLoggedBy(user);
        entry.setLoggedAt(java.time.LocalDateTime.now());
        return entry;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
