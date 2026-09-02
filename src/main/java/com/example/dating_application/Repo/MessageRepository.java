package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatChatIdOrderBySentAt(Long chatId);


    List<Message> findByChatChatIdOrderByMessageIdDesc(Long chatId, Pageable pageable);

    List<Message> findByChatChatIdAndMessageIdLessThanOrderByMessageIdDesc(
            Long chatId, Long beforeMessageId, Pageable pageable);

    List<Message> findByChatChatIdAndSenderUserIdNotAndIsReadFalse(Long chatId, Long senderId);

    void deleteByChatChatId(Long chatId);
}