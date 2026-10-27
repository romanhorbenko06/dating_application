package com.example.dating_application.Security;

import com.example.dating_application.Entity.Chat;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("accessControlService")
public class AccessControlService {

    private final PhotoRepository photoRepository;
    private final ChatRepository chatRepository;
    private final RequestRepository requestRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public AccessControlService(PhotoRepository photoRepository,
                                ChatRepository chatRepository,
                                RequestRepository requestRepository,
                                BlockRepository blockRepository,
                                UserRepository userRepository,
                                MessageRepository messageRepository) {
        this.photoRepository = photoRepository;
        this.chatRepository = chatRepository;
        this.requestRepository = requestRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private boolean isOwner(User user, Long ownerId) {
        return user != null && ownerId != null && ownerId.equals(user.getUserId());
    }

    public boolean canEditProfile(Long userId) {
        User me = currentUser();
        if (me == null || userId == null) return false;
        return isOwner(me, userId) || isAdmin(me);
    }

    public boolean canDeleteAccount(Long userId) {
        return canEditProfile(userId);
    }

    public boolean canViewProfile(Long userId) {
        User me = currentUser();
        if (me == null || userId == null) return false;
        if (isAdmin(me) || isOwner(me, userId)) return true;

        if (blockRepository.existsBetween(me.getUserId(), userId)) {
            return false;
        }

        return userRepository.findById(userId)
                .map(target -> !Boolean.TRUE.equals(target.getBlocked()))
                .orElse(false);
    }

    /** Лише власник: модерація для адміна — бан акаунта, а не правки чужого профілю. */
    public boolean canModifyPhoto(Long photoId) {
        User me = currentUser();
        if (me == null || photoId == null) return false;

        return photoRepository.findById(photoId)
                .map(Photo::getUser)
                .map(owner -> owner.getUserId().equals(me.getUserId()))
                .orElse(false);
    }

    public boolean canDeletePhoto(Long photoId) {
        return canModifyPhoto(photoId);
    }

    public boolean canSetMainPhoto(Long photoId) {
        return canModifyPhoto(photoId);
    }

    public boolean canRespondToRequest(Long requestId) {
        User me = currentUser();
        if (me == null || requestId == null) return false;

        return requestRepository.findById(requestId)
                .map(Request::getToUser)
                .map(to -> to.getUserId().equals(me.getUserId()))
                .orElse(false);
    }

    public boolean canAcceptRequest(Long requestId) {
        return canRespondToRequest(requestId);
    }

    public boolean canRejectRequest(Long requestId) {
        return canRespondToRequest(requestId);
    }

    /** Учасник або адміністратор; блок між учасниками закриває чат обом. */
    public boolean canAccessChat(Long chatId) {
        User me = currentUser();
        if (me == null || chatId == null) return false;
        if (isAdmin(me)) return true;

        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (chat == null) return false;

        Long myId = me.getUserId();
        boolean participant = chat.getUser1().getUserId().equals(myId)
                || chat.getUser2().getUserId().equals(myId);
        if (!participant) return false;

        User other = chat.getUser1().getUserId().equals(myId)
                ? chat.getUser2()
                : chat.getUser1();

        if (Boolean.TRUE.equals(other.getBlocked())) {
            return false;
        }

        return !blockRepository.existsBetween(myId, other.getUserId());
    }

    public boolean canReadChat(Long chatId) {
        return canAccessChat(chatId);
    }

    public boolean canSendMessage(Long chatId) {
        return canAccessChat(chatId);
    }

    /** Лише автор і лише поки чат йому доступний; адмін винятку не має. */
    public boolean canModifyMessage(Long messageId) {
        User me = currentUser();
        if (me == null || messageId == null) return false;

        return messageRepository.findById(messageId)
                .map(msg -> msg.getSender().getUserId().equals(me.getUserId())
                        && canAccessChat(msg.getChat().getChatId()))
                .orElse(false);
    }

    public boolean canEditMessage(Long messageId) {
        return canModifyMessage(messageId);
    }

    public boolean canDeleteMessage(Long messageId) {
        return canModifyMessage(messageId);
    }

    /** Лише двоє учасників доступного чату; адмін винятку не має. */
    public boolean canPlayIsland(Long chatId) {
        User me = currentUser();
        if (me == null || chatId == null || isAdmin(me)) return false;
        return canAccessChat(chatId);
    }

    public boolean isCurrentUserAdmin() {
        return isAdmin(currentUser());
    }
}