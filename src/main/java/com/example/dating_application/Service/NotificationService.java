package com.example.dating_application.Service;

import com.example.dating_application.DTO.Response.NotificationResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Notification;
import com.example.dating_application.Entity.NotificationType;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.NotificationRepository;
import com.example.dating_application.Websocket.NotificationRealtimeNotifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRealtimeNotifier realtimeNotifier;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationRealtimeNotifier realtimeNotifier) {
        this.notificationRepository = notificationRepository;
        this.realtimeNotifier = realtimeNotifier;
    }


    public void notifyNewLike(User recipient, User actor) {
        create(recipient, actor, NotificationType.NEW_LIKE, null);
    }

    public void notifyNewMatch(Chat chat) {
        User a = chat.getUser1();
        User b = chat.getUser2();
        create(a, b, NotificationType.NEW_MATCH, chat.getChatId());
        create(b, a, NotificationType.NEW_MATCH, chat.getChatId());
    }

    @Transactional
    public void notifyNewMessage(Chat chat, User sender) {
        User recipient = chat.getUser1().getUserId().equals(sender.getUserId())
                ? chat.getUser2()
                : chat.getUser1();

        var existing = notificationRepository.findFirstByRecipientUserIdAndChatIdAndTypeAndIsReadFalse(
                recipient.getUserId(), chat.getChatId(), NotificationType.NEW_MESSAGE);

        if (existing.isPresent()) {
            Notification n = existing.get();
            n.setMessageCount(n.getMessageCount() == null ? 2 : n.getMessageCount() + 1);
            // актор і час оновлюємо: показуємо ОСТАННЬОГО відправника і свіжий час,
            // щоб сповіщення піднялось нагору стрічки
            n.setActor(sender);
            n.setCreatedAt(LocalDateTime.now());

            Notification saved = notificationRepository.save(n);
            // той самий notificationId — клієнт замінює наявний запис, а не додає новий
            realtimeNotifier.notifyRecipient(recipient.getUserId(), toResponseDTO(saved));
            return;
        }

        create(recipient, sender, NotificationType.NEW_MESSAGE, chat.getChatId());
    }

    private void create(User recipient, User actor, NotificationType type, Long chatId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setChatId(chatId);
        n.setMessageCount(1);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(n);
        realtimeNotifier.notifyRecipient(recipient.getUserId(), toResponseDTO(saved));
    }


    public List<NotificationResponseDTO> list(Long userId, boolean unreadOnly, int page, int size) {
        return notificationRepository.findVisible(userId, unreadOnly, PageRequest.of(page, size)).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countVisibleUnread(userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    public NotificationResponseDTO toResponseDTO(Notification n) {
        return new NotificationResponseDTO(
                n.getNotificationId(),
                n.getType(),
                n.getActor() == null ? null : n.getActor().getUserId(),
                n.getActor() == null ? null : n.getActor().getName(),
                n.getChatId(),
                n.getMessageCount(),
                n.getIsRead(),
                n.getCreatedAt()
        );
    }
}
