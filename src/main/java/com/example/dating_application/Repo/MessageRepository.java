package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatChatIdOrderBySentAt(Long chatId);

    /** Непрочитані повідомлення в чаті, надіслані НЕ цим користувачем (тобто адресовані йому). */
    List<Message> findByChatChatIdAndSenderUserIdNotAndIsReadFalse(Long chatId, Long senderId);

    void deleteByChatChatId(Long chatId);
}