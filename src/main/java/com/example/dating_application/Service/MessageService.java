package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.SendMessageDTO;
import com.example.dating_application.DTO.Response.MessageResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Message;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          ChatRepository chatRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
    }

    public Message sendMessage(Long senderId, SendMessageDTO dto) {
        Chat chat = chatRepository.findById(dto.getChatId())
                .orElseThrow(() -> new BusinessException("Chat not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Подвійна перевірка (як в решті сервісів): писати може лише учасник чату.
        // @PreAuthorize у контролері — не єдиний бар'єр.
        boolean participant = chat.getUser1().getUserId().equals(senderId)
                || chat.getUser2().getUserId().equals(senderId);
        if (!participant) {
            throw new AccessDeniedException("You are not a participant of this chat");
        }

        Message msg = new Message();
        msg.setChat(chat);
        msg.setSender(sender);
        msg.setContent(dto.getContent());
        msg.setSentAt(LocalDateTime.now());
        msg.setIsRead(false);

        return messageRepository.save(msg);
    }

    /**
     * Сторінка історії чату: найсвіжіші `limit` повідомлень, або старіші за `beforeMessageId`
     * (прокрутка вгору). Повертає у ХРОНОЛОГІЧНОМУ порядку — так їх і малює клієнт.
     *
     * Курсор, а не offset: чат поповнюється під час читання, і зі зсувом сторінки
     * при прокрутці вгору повідомлення дублювались би або пропускались.
     */
    public List<Message> getChatMessages(Long chatId, Long beforeMessageId, int limit) {
        Pageable page = PageRequest.of(0, limit);

        List<Message> newestFirst = beforeMessageId == null
                ? messageRepository.findByChatChatIdOrderByMessageIdDesc(chatId, page)
                : messageRepository.findByChatChatIdAndMessageIdLessThanOrderByMessageIdDesc(
                        chatId, beforeMessageId, page);

        List<Message> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    /**
     * Позначає прочитаними всі непрочитані повідомлення в чаті, адресовані reader'у
     * (тобто надіслані співрозмовником). Повертає id позначених повідомлень.
     */
    @Transactional
    public List<Long> markChatRead(Long chatId, Long readerId) {
        List<Message> unread =
                messageRepository.findByChatChatIdAndSenderUserIdNotAndIsReadFalse(chatId, readerId);
        if (unread.isEmpty()) {
            return List.of();
        }
        unread.forEach(m -> m.setIsRead(true));
        messageRepository.saveAll(unread);
        return unread.stream().map(Message::getMessageId).collect(Collectors.toList());
    }


    @Transactional
    public Message editMessage(Long messageId, Long editorId, String newContent) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found"));

        // подвійна перевірка: @PreAuthorize у контролері — не єдиний бар'єр
        if (!msg.getSender().getUserId().equals(editorId)) {
            throw new AccessDeniedException("You can only edit your own messages");
        }
        if (Boolean.TRUE.equals(msg.getDeleted())) {
            throw new BusinessException("Deleted message cannot be edited");
        }

        msg.setContent(newContent);
        msg.setEditedAt(LocalDateTime.now());

        return messageRepository.save(msg);
    }

    /** Чи повідомлення вже видалене — контролеру, щоб не пушити подію двічі. */
    public boolean isDeleted(Long messageId) {
        return messageRepository.findById(messageId)
                .map(m -> Boolean.TRUE.equals(m.getDeleted()))
                .orElse(false);
    }


    @Transactional
    public Message deleteMessage(Long messageId, Long deleterId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found"));

        if (!msg.getSender().getUserId().equals(deleterId)) {
            throw new AccessDeniedException("You can only delete your own messages");
        }
        if (Boolean.TRUE.equals(msg.getDeleted())) {
            return msg; // вже видалене — нічого не робимо і не пушимо повторно
        }

        msg.setDeleted(true);
        msg.setDeletedAt(LocalDateTime.now());
        msg.setContent(null); // вміст прибираємо реально, а не ховаємо у відповіді

        return messageRepository.save(msg);
    }

    public MessageResponseDTO toResponseDTO(Message msg) {
        return new MessageResponseDTO(
                msg.getMessageId(),
                msg.getChat().getChatId(),
                msg.getSender().getUserId(),
                msg.getSender().getName(),
                msg.getContent(),
                msg.getSentAt(),
                msg.getIsRead(),
                msg.getEditedAt(),
                Boolean.TRUE.equals(msg.getDeleted())
        );
    }
}

