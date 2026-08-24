package com.example.dating_application.Service;

import com.example.dating_application.DTO.Response.ChatResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.UserRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;

    public ChatService(ChatRepository chatRepository,
                       UserRepository userRepository,
                       BlockRepository blockRepository) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
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
    public List<Chat> getUserChats(Long userId) {
        return chatRepository.findByUser1UserIdOrUser2UserId(userId, userId).stream()
                .filter(chat -> {
                    User other = chat.getUser1().getUserId().equals(userId)
                            ? chat.getUser2()
                            : chat.getUser1();
                    return !Boolean.TRUE.equals(other.getBlocked())
                            && !blockRepository.existsBetween(userId, other.getUserId());
                })
                .collect(Collectors.toList());
    }

    public ChatResponseDTO toResponseDTO(Chat chat) {
        return new ChatResponseDTO(
                chat.getChatId(),
                chat.getUser1().getUserId(),
                chat.getUser1().getName(),
                chat.getUser2().getUserId(),
                chat.getUser2().getName()
        );
    }
}

