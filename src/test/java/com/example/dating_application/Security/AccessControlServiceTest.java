package com.example.dating_application.Security;

import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Message;
import com.example.dating_application.Entity.Photo;
import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Правила доступу, які @PreAuthorize підставляє в контролери. */
@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Mock private PhotoRepository photoRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;

    private AccessControlService service;
    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        service = new AccessControlService(photoRepository, chatRepository, requestRepository,
                blockRepository, userRepository, messageRepository);

        me = user(ME, Role.DATER);
        other = user(OTHER, Role.DATER);
        lenient().when(blockRepository.existsBetween(anyLong(), anyLong())).thenReturn(false);
        lenient().when(userRepository.findById(OTHER)).thenReturn(Optional.of(other));
        authenticateAs(me);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("свою анкету редагує власник, чужу — ні")
    void onlyOwnerEditsProfile() {
        assertThat(service.canEditProfile(ME)).isTrue();
        assertThat(service.canEditProfile(OTHER)).isFalse();
        assertThat(service.canDeleteAccount(ME)).isTrue();
        assertThat(service.canDeleteAccount(OTHER)).isFalse();
    }

    @Test
    @DisplayName("адміністратор може редагувати будь-яку анкету")
    void adminEditsAnyProfile() {
        authenticateAs(user(99L, Role.ADMIN));

        assertThat(service.canEditProfile(OTHER)).isTrue();
        assertThat(service.isCurrentUserAdmin()).isTrue();
    }

    @Test
    @DisplayName("анкета заблокованого й забаненого не відкривається")
    void blockedAndBannedProfilesAreHidden() {
        assertThat(service.canViewProfile(OTHER)).as("звичайна чужа анкета видима").isTrue();

        when(blockRepository.existsBetween(ME, OTHER)).thenReturn(true);
        assertThat(service.canViewProfile(OTHER)).isFalse();

        when(blockRepository.existsBetween(ME, OTHER)).thenReturn(false);
        other.setBlocked(true);
        assertThat(service.canViewProfile(OTHER)).isFalse();
    }

    @Test
    @DisplayName("без автентифікації не дозволено нічого")
    void anonymousCanDoNothing() {
        SecurityContextHolder.clearContext();

        assertThat(service.canEditProfile(ME)).isFalse();
        assertThat(service.canViewProfile(OTHER)).isFalse();
        assertThat(service.canAccessChat(1L)).isFalse();
        assertThat(service.isCurrentUserAdmin()).isFalse();
    }

    @Test
    @DisplayName("null замість id — це «ні», а не помилка")
    void nullIdIsSimplyDenied() {
        assertThat(service.canEditProfile(null)).isFalse();
        assertThat(service.canViewProfile(null)).isFalse();
        assertThat(service.canAccessChat(null)).isFalse();
        assertThat(service.canModifyPhoto(null)).isFalse();
        assertThat(service.canRespondToRequest(null)).isFalse();
    }

    @Test
    @DisplayName("чат доступний лише учасникам")
    void onlyParticipantsAccessChat() {
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat(me, other)));
        assertThat(service.canAccessChat(10L)).isTrue();
        assertThat(service.canReadChat(10L)).isTrue();
        assertThat(service.canSendMessage(10L)).isTrue();

        when(chatRepository.findById(11L)).thenReturn(Optional.of(chat(other, user(3L, Role.DATER))));
        assertThat(service.canAccessChat(11L)).as("чужий чат").isFalse();
    }

    @Test
    @DisplayName("блокування закриває чат обом, бан співрозмовника — теж")
    void blockClosesChat() {
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat(me, other)));

        when(blockRepository.existsBetween(ME, OTHER)).thenReturn(true);
        assertThat(service.canAccessChat(10L)).as("взаємне блокування").isFalse();

        other.setBlocked(true);
        assertThat(service.canAccessChat(10L)).as("забанений співрозмовник").isFalse();
    }

    @Test
    @DisplayName("неіснуючий чат — просто «ні»")
    void unknownChatIsDenied() {
        when(chatRepository.findById(404L)).thenReturn(Optional.empty());

        assertThat(service.canAccessChat(404L)).isFalse();
    }

    @Test
    @DisplayName("фото змінює лише його власник")
    void onlyOwnerModifiesPhoto() {
        Photo mine = new Photo();
        mine.setUser(me);
        Photo theirs = new Photo();
        theirs.setUser(other);
        when(photoRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(photoRepository.findById(2L)).thenReturn(Optional.of(theirs));

        assertThat(service.canModifyPhoto(1L)).isTrue();
        assertThat(service.canDeletePhoto(1L)).isTrue();
        assertThat(service.canSetMainPhoto(1L)).isTrue();
        assertThat(service.canModifyPhoto(2L)).isFalse();
    }

    @Test
    @DisplayName("відповідати на симпатію може лише її адресат")
    void onlyAddresseeRespondsToRequest() {
        Request toMe = new Request();
        toMe.setFromUser(other);
        toMe.setToUser(me);
        Request toThem = new Request();
        toThem.setFromUser(me);
        toThem.setToUser(other);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(toMe));
        when(requestRepository.findById(2L)).thenReturn(Optional.of(toThem));

        assertThat(service.canAcceptRequest(1L)).isTrue();
        assertThat(service.canRejectRequest(1L)).isTrue();
        assertThat(service.canAcceptRequest(2L))
                .as("свою ж надіслану симпатію прийняти не можна")
                .isFalse();
    }

    @Test
    @DisplayName("повідомлення редагує й видаляє лише автор")
    void onlyAuthorEditsMessage() {
        Chat chat = chat(me, other);
        chat.setChatId(10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));

        Message mine = new Message();
        mine.setSender(me);
        mine.setChat(chat);
        Message theirs = new Message();
        theirs.setSender(other);
        theirs.setChat(chat);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(messageRepository.findById(2L)).thenReturn(Optional.of(theirs));

        assertThat(service.canEditMessage(1L)).isTrue();
        assertThat(service.canDeleteMessage(1L)).isTrue();
        assertThat(service.canEditMessage(2L)).isFalse();
        assertThat(service.canDeleteMessage(2L)).isFalse();
    }

    private static void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    private static User user(long id, Role role) {
        User u = new User();
        u.setUserId(id);
        u.setRole(role);
        u.setBlocked(false);
        return u;
    }

    private static Chat chat(User a, User b) {
        Chat c = new Chat();
        c.setUser1(a);
        c.setUser2(b);
        return c;
    }
}
