package com.example.dating_application.Service;

import com.example.dating_application.DTO.Request.SendRequestDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.UserTagsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Життєвий цикл симпатії; репозиторії підмінені. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestServiceTest {

    private static final long ME = 1L;
    private static final long THEM = 2L;

    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatService chatService;
    @Mock private BlockRepository blockRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private UserTagsRepository userTagsRepository;

    private RequestService service;
    private User me;
    private User them;

    @BeforeEach
    void setUp() {
        service = new RequestService(requestRepository, userRepository, chatService,
                blockRepository, photoRepository, userTagsRepository);

        me = user(ME, "Я");
        them = user(THEM, "Вони");

        when(userRepository.findById(ME)).thenReturn(Optional.of(me));
        when(userRepository.findById(THEM)).thenReturn(Optional.of(them));
        when(blockRepository.existsBetween(anyLong(), anyLong())).thenReturn(false);
        when(requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("звичайний лайк створює запит у стані PENDING")
    void plainLikeCreatesPendingRequest() {
        var result = service.send(ME, sendTo(THEM));

        assertThat(result.chat()).as("метчу ще немає — чат не створюється").isNull();
        assertThat(result.request().getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(result.request().getFromUser()).isSameAs(me);
        assertThat(result.request().getToUser()).isSameAs(them);
    }

    @Test
    @DisplayName("не можна вподобати самого себе")
    void cannotLikeYourself() {
        assertThatThrownBy(() -> service.send(ME, sendTo(ME)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot like yourself");
    }

    @Test
    @DisplayName("блокування в будь-якому напрямку закриває взаємодію")
    void blockInEitherDirectionStopsLike() {
        when(blockRepository.existsBetween(ME, THEM)).thenReturn(true);

        assertThatThrownBy(() -> service.send(ME, sendTo(THEM)))
                .isInstanceOf(BusinessException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("повторний лайк тій самій людині відхиляється")
    void cannotLikeTwice() {
        when(requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(ME, THEM, RequestStatus.PENDING))
                .thenReturn(Optional.of(request(ME, THEM, RequestStatus.PENDING)));

        assertThatThrownBy(() -> service.send(ME, sendTo(THEM)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already liked");
    }

    @Test
    @DisplayName("лайк адміністратору відхиляється")
    void cannotLikeAdmin() {
        them.setRole(Role.ADMIN);

        assertThatThrownBy(() -> service.send(ME, sendTo(THEM)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Administrators");
    }

    @Test
    @DisplayName("лайк забаненому відхиляється")
    void cannotLikeBannedUser() {
        them.setBlocked(true);

        assertThatThrownBy(() -> service.send(ME, sendTo(THEM)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("РЕГРЕСІЯ: не можна лайкнути того, чию симпатію я вже ПРИЙНЯВ")
    void cannotLikeWhenMatchCameFromTheirRequest() {
        // Метч створено з їхнього лайку, тож ACCEPTED-рядок належить їм.
        when(requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(THEM, ME, RequestStatus.ACCEPTED))
                .thenReturn(Optional.of(request(THEM, ME, RequestStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.send(ME, sendTo(THEM)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already matched");
        verify(requestRepository, never()).save(any(Request.class));
    }

    @Test
    @DisplayName("зустрічний лайк одразу стає метчем, а не другим запитом")
    void reciprocalLikeBecomesMatch() {
        Request theirs = request(THEM, ME, RequestStatus.PENDING);
        theirs.setRequestId(77L);
        when(requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(THEM, ME, RequestStatus.PENDING))
                .thenReturn(Optional.of(theirs));
        when(requestRepository.findById(77L)).thenReturn(Optional.of(theirs));

        Chat chat = new Chat();
        when(chatService.createChat(THEM, ME)).thenReturn(chat);

        var result = service.send(ME, sendTo(THEM));

        assertThat(result.chat()).as("взаємність — це вже метч, чат має з'явитись").isSameAs(chat);
        assertThat(result.request()).isSameAs(theirs);
        assertThat(theirs.getStatus())
                .as("їхній запит приймається, а не лишається висіти в «Симпатіях»")
                .isEqualTo(RequestStatus.ACCEPTED);

        ArgumentCaptor<Request> saved = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue())
                .as("другий, зустрічний рядок створюватись не повинен")
                .isSameAs(theirs);
    }

    @Test
    @DisplayName("прийняття створює чат і переводить запит в ACCEPTED")
    void acceptCreatesChat() {
        Request theirs = request(THEM, ME, RequestStatus.PENDING);
        theirs.setRequestId(5L);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(theirs));
        Chat chat = new Chat();
        when(chatService.createChat(THEM, ME)).thenReturn(chat);

        assertThat(service.accept(5L, ME)).isSameAs(chat);
        assertThat(theirs.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("прийняття гасить і зустрічний запит, якщо він був")
    void acceptAlsoResolvesOppositePendingRequest() {
        // Обидва лайкнули до прийняття: мій зустрічний рядок не має лишитися PENDING.
        Request theirs = request(THEM, ME, RequestStatus.PENDING);
        theirs.setRequestId(5L);
        Request mine = request(ME, THEM, RequestStatus.PENDING);

        when(requestRepository.findById(5L)).thenReturn(Optional.of(theirs));
        when(requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(ME, THEM, RequestStatus.PENDING))
                .thenReturn(Optional.of(mine));
        when(chatService.createChat(THEM, ME)).thenReturn(new Chat());

        service.accept(5L, ME);

        assertThat(mine.getStatus())
                .as("зустрічний запит закривається тим самим метчем")
                .isEqualTo(RequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("прийняти чужу симпатію може лише адресат")
    void onlyAddresseeCanAccept() {
        Request theirs = request(THEM, ME, RequestStatus.PENDING);
        theirs.setRequestId(5L);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.accept(5L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only accept requests sent to you");
    }

    @Test
    @DisplayName("двічі прийняти той самий запит не можна")
    void cannotAcceptTwice() {
        Request theirs = request(THEM, ME, RequestStatus.ACCEPTED);
        theirs.setRequestId(5L);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.accept(5L, ME))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already processed");
    }

    @Test
    @DisplayName("блокування після лайку скасовує метч")
    void blockAfterLikePreventsMatch() {
        Request theirs = request(THEM, ME, RequestStatus.PENDING);
        theirs.setRequestId(5L);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(theirs));
        when(blockRepository.existsBetween(THEM, ME)).thenReturn(true);

        assertThatThrownBy(() -> service.accept(5L, ME))
                .isInstanceOf(BusinessException.class);
        verify(chatService, never()).createChat(anyLong(), anyLong());
    }

    private static User user(long id, String name) {
        User u = new User();
        u.setUserId(id);
        u.setName(name);
        u.setRole(Role.DATER);
        u.setBlocked(false);
        return u;
    }

    private Request request(long fromId, long toId, RequestStatus status) {
        Request r = new Request();
        r.setFromUser(fromId == ME ? me : them);
        r.setToUser(toId == ME ? me : them);
        r.setStatus(status);
        return r;
    }

    private static SendRequestDTO sendTo(long userId) {
        SendRequestDTO dto = new SendRequestDTO();
        dto.setToUserId(userId);
        return dto;
    }
}
