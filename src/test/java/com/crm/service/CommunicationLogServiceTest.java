package com.crm.service;

import com.crm.dto.CommunicationLogDto;
import com.crm.dto.LinkedRecord;
import com.crm.entity.CommunicationLog;
import com.crm.entity.User;
import com.crm.enums.CommunicationType;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.repository.CommunicationLogRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunicationLogServiceTest {

    @Mock
    private CommunicationLogRepository repository;
    @Mock
    private LinkResolver linkResolver;

    @InjectMocks
    private CommunicationLogService service;

    private User rep;
    private User otherRep;
    private User admin;

    @BeforeEach
    void setUp() {
        rep = new User();
        rep.setId(2L);
        rep.setEmail("rep@crm.com");
        rep.setRole(Role.SALES_REP);

        otherRep = new User();
        otherRep.setId(3L);
        otherRep.setEmail("other@crm.com");
        otherRep.setRole(Role.SALES_REP);

        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@crm.com");
        admin.setRole(Role.ADMIN);
    }

    private CommunicationLogDto dto() {
        CommunicationLogDto dto = new CommunicationLogDto();
        dto.setType(CommunicationType.CALL);
        dto.setSubject("  Discovery call  ");
        dto.setNotes("  Discussed renewal timeline.  ");
        dto.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        dto.setLinkedEntityId(10L);
        return dto;
    }

    private CommunicationLog entry(User author) {
        CommunicationLog entry = new CommunicationLog();
        entry.setId(20L);
        entry.setType(CommunicationType.NOTE);
        entry.setNotes("Note");
        entry.setLinkedEntityType(LinkedEntityType.DEAL);
        entry.setLinkedEntityId(30L);
        entry.setLoggedBy(author);
        return entry;
    }

    @Test
    @DisplayName("logInteraction: resolves the link and stores a normalised entry")
    void logInteraction_savesEntry() {
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, rep))
                .thenReturn(new LinkedRecord("Acme Corp", "acme@example.com", "/customers/10"));
        when(repository.save(any(CommunicationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CommunicationLog saved = service.logInteraction(dto(), rep);

        assertThat(saved.getType()).isEqualTo(CommunicationType.CALL);
        assertThat(saved.getSubject()).isEqualTo("Discovery call");
        assertThat(saved.getNotes()).isEqualTo("Discussed renewal timeline.");
        assertThat(saved.getLinkedEntityName()).isEqualTo("Acme Corp");
        assertThat(saved.getLoggedBy()).isEqualTo(rep);
        assertThat(saved.getLoggedAt()).isNotNull();
    }

    @Test
    @DisplayName("logInteraction: an invisible linked record blocks the log")
    void logInteraction_invalidLink_rejected() {
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, rep))
                .thenThrow(new IllegalArgumentException("Customer not found"));

        assertThatThrownBy(() -> service.logInteraction(dto(), rep))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete: a rep cannot delete another user's activity entry")
    void delete_foreignEntry_denied() {
        when(repository.findById(20L)).thenReturn(Optional.of(entry(otherRep)));

        assertThatThrownBy(() -> service.delete(20L, rep))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: returns the linked record path for the redirect")
    void delete_returnsLinkedPath() {
        when(repository.findById(20L)).thenReturn(Optional.of(entry(rep)));

        String path = service.delete(20L, rep);

        assertThat(path).isEqualTo("/deals/30");
        verify(repository).delete(any(CommunicationLog.class));
    }

    @Test
    @DisplayName("delete: an admin may remove any activity entry")
    void delete_asAdmin_allowed() {
        when(repository.findById(20L)).thenReturn(Optional.of(entry(rep)));

        assertThat(service.delete(20L, admin)).isEqualTo("/deals/30");
        verify(repository).delete(any(CommunicationLog.class));
    }
}
