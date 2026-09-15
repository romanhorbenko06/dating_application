package com.example.dating_application.Service;

import com.example.dating_application.DTO.Response.ChatResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.UserTagsRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final UserTagsRepository userTagsRepository;

    public ChatService(ChatRepository chatRepository,
                       UserRepository userRepository,
                       UserTagsRepository userTagsRepository) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.userTagsRepository = userTagsRepository;
    }

    public Chat createChat(Long user1Id, Long user2Id) {

        if (user1Id.equals(user2Id)) {
            throw new EntityExistsException("Cannot create chat with yourself");
        }

        // перевірка, чи чат вже існує
        Chat existing = chatRepository
                .findChatBetweenUsers(user1Id, user2Id);

        if (existing != null) {
            return existing;
        }

        User u1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new EntityNotFoundException("User1 not found"));
        User u2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new EntityNotFoundException("User2 not found"));

        Chat chat = new Chat();
        chat.setUser1(u1);
        chat.setUser2(u2);

        return chatRepository.save(chat);
    }



    /**
     * Чати користувача. Чати зі співрозмовниками, з якими є блок (у будь-якому
     * напрямку) або яких забанив адміністратор, зі списку зникають —
     * доступ до них закриває також AccessControlService.canAccessChat.
     */
    public List<Chat> getUserChats(Long userId, int page, int size) {
        return chatRepository.findVisibleChats(userId, PageRequest.of(page, size));
    }

    public ChatResponseDTO toResponseDTO(Chat chat) {
        User first = chat.getUser1();
        User second = chat.getUser2();

        return new ChatResponseDTO(
                chat.getChatId(),
                first.getUserId(),
                first.getName(),
                second.getUserId(),
                second.getName(),
                first.getDateOfBirth(),
                first.getCity(),
                second.getDateOfBirth(),
                second.getCity(),
                userTagsRepository.countSharedTags(first.getUserId(), second.getUserId())
        );
    }
}

