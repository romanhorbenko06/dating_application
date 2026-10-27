package com.example.dating_application.Service;

import com.example.dating_application.Entity.Complaint;
import com.example.dating_application.Entity.ComplaintStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.ComplaintRepository;
import com.example.dating_application.Repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Правила створення й розгляду скарг (FR-20, UC-13). */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    private static final long REPORTER = 1L;
    private static final long OFFENDER = 2L;

    @Mock private ComplaintRepository complaintRepository;
    @Mock private UserRepository userRepository;

    private ComplaintService service;
    private User reporter;
    private User offender;

    @BeforeEach
    void setUp() {
        service = new ComplaintService(complaintRepository, userRepository);

        reporter = user(REPORTER, "Скаржник", Role.DATER);
        offender = user(OFFENDER, "Порушник", Role.DATER);

        lenient().when(userRepository.findById(REPORTER)).thenReturn(Optional.of(reporter));
        lenient().when(userRepository.findById(OFFENDER)).thenReturn(Optional.of(offender));
        lenient().when(complaintRepository.save(any(Complaint.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("скарга створюється зі статусом PENDING і датою")
    void createdComplaintWaitsForModeration() {
        Complaint complaint = service.create(REPORTER, OFFENDER, "образи в чаті");

        assertThat(complaint.getStatus())
                .as("нова скарга має потрапити в чергу, а не вважатись розглянутою")
                .isEqualTo(ComplaintStatus.PENDING);
        assertThat(complaint.getReporter()).isSameAs(reporter);
        assertThat(complaint.getReportedUser()).isSameAs(offender);
        assertThat(complaint.getReason()).isEqualTo("образи в чаті");
        assertThat(complaint.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("на себе поскаржитись не можна")
    void cannotReportYourself() {
        assertThatThrownBy(() -> service.create(REPORTER, REPORTER, "сам на себе"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot report yourself");
        verify(complaintRepository, never()).save(any());
    }

    @Test
    @DisplayName("скарга на адміністратора відхиляється — її нікому розглядати")
    void cannotReportAdmin() {
        offender.setRole(Role.ADMIN);

        assertThatThrownBy(() -> service.create(REPORTER, OFFENDER, "не подобається модерація"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Administrators cannot be reported");
        verify(complaintRepository, never()).save(any());
    }

    @Test
    @DisplayName("скарга без вказаного порушника відхиляється зрозумілою помилкою")
    void requiresReportedUserId() {
        assertThatThrownBy(() -> service.create(REPORTER, null, "без адресата"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reportedUserId is required");
    }

    @Test
    @DisplayName("скарга на неіснуючого користувача відхиляється")
    void cannotReportUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(REPORTER, 99L, "привид"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reported user not found");
    }

    @Test
    @DisplayName("модератор переводить скаргу в «розглядається»")
    void markReviewedChangesStatus() {
        Complaint complaint = pending(10L);

        assertThat(service.markReviewed(10L).getStatus()).isEqualTo(ComplaintStatus.REVIEWED);
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.REVIEWED);
    }

    @Test
    @DisplayName("«вирішено» означає, що порушення підтвердилось")
    void resolveChangesStatus() {
        pending(11L);

        assertThat(service.resolve(11L).getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
    }

    @Test
    @DisplayName("«відхилено» — порушення не підтвердилось, санкцій не буде (FR-21.2)")
    void rejectChangesStatus() {
        pending(12L);

        assertThat(service.reject(12L).getStatus()).isEqualTo(ComplaintStatus.REJECTED);
    }

    @Test
    @DisplayName("дії над неіснуючою скаргою дають зрозумілу помилку, а не 500")
    void missingComplaintIsReported() {
        when(complaintRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markReviewed(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Complaint not found");
        assertThatThrownBy(() -> service.resolve(404L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reject(404L)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("без фільтра беруться всі скарги, з фільтром — лише потрібного статусу")
    void listUsesFilterOnlyWhenStatusGiven() {
        when(complaintRepository.findAllByOrderByCreatedAtDescComplainIdDesc(any(Pageable.class)))
                .thenReturn(List.of());
        when(complaintRepository.findByStatusOrderByCreatedAtDescComplainIdDesc(
                any(ComplaintStatus.class), any(Pageable.class))).thenReturn(List.of());

        service.getComplaints(null, 0, 50);
        verify(complaintRepository).findAllByOrderByCreatedAtDescComplainIdDesc(PageRequest.of(0, 50));

        service.getComplaints(ComplaintStatus.PENDING, 1, 20);
        verify(complaintRepository).findByStatusOrderByCreatedAtDescComplainIdDesc(
                ComplaintStatus.PENDING, PageRequest.of(1, 20));
    }

    @Test
    @DisplayName("сторінка передається в репозиторій як є — без мовчазної підміни")
    void pagingIsPassedThrough() {
        when(complaintRepository.findByReportedUserUserIdOrderByCreatedAtDescComplainIdDesc(
                any(Long.class), any(Pageable.class))).thenReturn(List.of());

        service.getByReportedUser(OFFENDER, 2, 15);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(complaintRepository).findByReportedUserUserIdOrderByCreatedAtDescComplainIdDesc(
                any(Long.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("DTO віддає імена обох сторін — модератор бачить, хто на кого")
    void dtoCarriesBothSides() {
        Complaint complaint = service.create(REPORTER, OFFENDER, "спам");
        complaint.setComplainId(7L);

        var dto = service.toResponseDTO(complaint);

        assertThat(dto.getComplaintId()).isEqualTo(7L);
        assertThat(dto.getReporterId()).isEqualTo(REPORTER);
        assertThat(dto.getReporterName()).isEqualTo("Скаржник");
        assertThat(dto.getReportedUserId()).isEqualTo(OFFENDER);
        assertThat(dto.getReportedUserName()).isEqualTo("Порушник");
        assertThat(dto.getReason()).isEqualTo("спам");
        assertThat(dto.getStatus()).isEqualTo(ComplaintStatus.PENDING);
    }

    private Complaint pending(long id) {
        Complaint c = new Complaint();
        c.setComplainId(id);
        c.setReporter(reporter);
        c.setReportedUser(offender);
        c.setReason("причина");
        c.setCreatedAt(LocalDateTime.now());
        c.setStatus(ComplaintStatus.PENDING);
        when(complaintRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setUserId(id);
        u.setName(name);
        u.setRole(role);
        return u;
    }
}
