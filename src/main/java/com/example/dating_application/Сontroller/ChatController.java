package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.EditMessageDTO;
import com.example.dating_application.DTO.Request.SendMessageDTO;
import com.example.dating_application.DTO.Response.ChatResponseDTO;
import com.example.dating_application.DTO.Response.MessageResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Service.ChatService;
import com.example.dating_application.Service.MessageService;
import com.example.dating_application.Service.NotificationService;
import com.example.dating_application.Websocket.ChatRealtimeNotifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/chats")
@Validated
public class ChatController {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final ChatService chatService;
    private final MessageService messageService;
    private final ChatRepository chatRepository;
    private final ChatRealtimeNotifier chatRealtimeNotifier;
    private final NotificationService notificationService;

    public ChatController(ChatService chatService, MessageService messageService,
                          ChatRepository chatRepository, ChatRealtimeNotifier chatRealtimeNotifier,
                          NotificationService notificationService) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.chatRepository = chatRepository;
        this.chatRealtimeNotifier = chatRealtimeNotifier;
        this.notificationService = notificationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @GetMapping
    public ResponseEntity<List<ChatResponseDTO>> getUserChats() {
        Long currentUserId = getCurrentUserId();
        List<ChatResponseDTO> chats = chatService.getUserChats(currentUserId).stream()
                .map(chatService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/{chatId}")
    @PreAuthorize("@accessControlService.canReadChat(#chatId)")
    public ResponseEntity<ChatResponseDTO> getChatDetails(@PathVariable Long chatId) {
        var chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new BusinessException("Chat not found"));
        return ResponseEntity.ok(chatService.toResponseDTO(chat));
    }

    @GetMapping("/{chatId}/messages")
    @PreAuthorize("@accessControlService.canReadChat(#chatId)")
    public ResponseEntity<List<MessageResponseDTO>> getChatMessages(
            @PathVariable Long chatId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_MESSAGE_LIMIT)
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must not exceed 200") int limit) {

        List<MessageResponseDTO> messages = messageService.getChatMessages(chatId, before, limit).stream()
                .map(messageService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/messages")
    @PreAuthorize("@accessControlService.canSendMessage(#dto.chatId)")
    public ResponseEntity<MessageResponseDTO> sendMessage(@Valid @RequestBody SendMessageDTO dto) {
        Long currentUserId = getCurrentUserId();

        // 1. Зберігаємо в БД (джерело правди)
        var message = messageService.sendMessage(currentUserId, dto);
        MessageResponseDTO responseDTO = messageService.toResponseDTO(message);

        // 2. Пушимо обом учасникам через WebSocket (наживо)
        chatRealtimeNotifier.notifyNewMessage(message.getChat(), responseDTO);

        // 3. Запис у центр сповіщень адресата (FR-19.3): пуш побачить лише той,
        //    хто зараз онлайн, а сповіщення має дочекатися й офлайнового
        notificationService.notifyNewMessage(message.getChat(), message.getSender());

        return ResponseEntity.ok(responseDTO);
    }

    @PutMapping("/messages/{messageId}")
    @PreAuthorize("@accessControlService.canEditMessage(#messageId)")
    public ResponseEntity<MessageResponseDTO> editMessage(@PathVariable Long messageId,
                                                          @Valid @RequestBody EditMessageDTO dto) {
        Long currentUserId = getCurrentUserId();

        var message = messageService.editMessage(messageId, currentUserId, dto.getContent());
        MessageResponseDTO responseDTO = messageService.toResponseDTO(message);

        chatRealtimeNotifier.notifyMessageEdited(message.getChat(), responseDTO);

        return ResponseEntity.ok(responseDTO);
    }

    @DeleteMapping("/messages/{messageId}")
    @PreAuthorize("@accessControlService.canDeleteMessage(#messageId)")
    public ResponseEntity<MessageResponseDTO> deleteMessage(@PathVariable Long messageId) {
        Long currentUserId = getCurrentUserId();

        boolean wasDeleted = messageService.isDeleted(messageId);
        var message = messageService.deleteMessage(messageId, currentUserId);
        MessageResponseDTO responseDTO = messageService.toResponseDTO(message);

        // повторне видалення нічого не міняє — і пуш не шлемо
        if (!wasDeleted) {
            chatRealtimeNotifier.notifyMessageDeleted(message.getChat(), responseDTO);
        }

        return ResponseEntity.ok(responseDTO);
    }

    @PutMapping("/{chatId}/read")
    @PreAuthorize("@accessControlService.canReadChat(#chatId)")
    public ResponseEntity<Map<String, Object>> markChatRead(@PathVariable Long chatId) {
        Long currentUserId = getCurrentUserId();

        List<Long> markedIds = messageService.markChatRead(chatId, currentUserId);
        if (!markedIds.isEmpty()) {
            var chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new BusinessException("Chat not found"));
            chatRealtimeNotifier.notifyReadReceipt(chat, currentUserId, markedIds);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("markedCount", markedIds.size());
        return ResponseEntity.ok(response);
    }
}