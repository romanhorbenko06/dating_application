package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.SendRequestDTO;
import com.example.dating_application.DTO.Response.RequestResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final BlockRepository blockRepository;

    public RequestService(RequestRepository requestRepository,
                          UserRepository userRepository,
                          ChatService chatService,
                          BlockRepository blockRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.blockRepository = blockRepository;
    }

    public Request send(Long fromUserId, SendRequestDTO dto) {
        // Без цієї перевірки null доїжджав би до репозиторію й давав 500 замість зрозумілої 400
        if (dto.getToUserId() == null) {
            throw new BusinessException("toUserId is required");
        }
        if (fromUserId.equals(dto.getToUserId())) {
            throw new BusinessException("You cannot like yourself");
        }

        // Блок у будь-якому напрямку повністю закриває взаємодію
        if (blockRepository.existsBetween(fromUserId, dto.getToUserId())) {
            throw new BusinessException("Interaction with this user is not available");
        }

        var pending = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                fromUserId, dto.getToUserId(), RequestStatus.PENDING);
        if (pending.isPresent()) {
            throw new BusinessException("You already liked this user");
        }

        var accepted = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                fromUserId, dto.getToUserId(), RequestStatus.ACCEPTED);
        if (accepted.isPresent()) {
            throw new BusinessException("You are already matched with this user");
        }

        var toUser = userRepository.findById(dto.getToUserId())
                .orElseThrow(() -> new BusinessException("To user not found"));
        if (Boolean.TRUE.equals(toUser.getBlocked())) {
            throw new BusinessException("Interaction with this user is not available");
        }
        // Адміністратор не бере участі в знайомствах: зі стрічки він виключений,
        // але без цієї перевірки прямий виклик API все одно створював лайк на нього.
        if (toUser.getRole() == Role.ADMIN) {
            throw new BusinessException("Administrators cannot be liked");
        }

        Request r = new Request();
        r.setFromUser(userRepository.findById(fromUserId)
                .orElseThrow(() -> new BusinessException("From user not found")));
        r.setToUser(toUser);
        r.setMessage(dto.getMessage());
        r.setCreatedAt(LocalDateTime.now());
        r.setStatus(RequestStatus.PENDING);

        return requestRepository.save(r);
    }

    public Chat accept(Long requestId, Long userId) {

        Request r = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!r.getToUser().getUserId().equals(userId)) {
            throw new BusinessException("Can only accept requests sent to you");
        }

        if (r.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request already processed");
        }

        // Якщо після надсилання лайку з'явився блок — метч не створюємо
        if (blockRepository.existsBetween(r.getFromUser().getUserId(), r.getToUser().getUserId())) {
            throw new BusinessException("Interaction with this user is not available");
        }

        r.setStatus(RequestStatus.ACCEPTED);
        requestRepository.save(r);

        return chatService.createChat(
                r.getFromUser().getUserId(),
                r.getToUser().getUserId()
        );
    }

    public void reject(Long requestId, Long userId) {

        Request r = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!r.getToUser().getUserId().equals(userId)) {
            throw new BusinessException("Can only reject requests sent to you");
        }

        if (r.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request already processed");
        }

        r.setStatus(RequestStatus.REJECTED);
        requestRepository.save(r);
    }

    public List<Request> incoming(Long userId) {
        // Лайки від заблокованих (у будь-якому напрямку) та від забанених адміном
        // акаунтів у список не потрапляють — вони мають зникнути з поля зору.
        return requestRepository.findByToUserUserIdAndStatus(userId, RequestStatus.PENDING).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getFromUser().getBlocked()))
                .filter(r -> !blockRepository.existsBetween(userId, r.getFromUser().getUserId()))
                .collect(Collectors.toList());
    }

    public List<Request> sent(Long userId) {
        // «Надіслані лайки» — усі ініційовані запити, окрім пропусків (SKIPPED)
        return requestRepository.findByFromUserUserIdAndStatusNot(userId, RequestStatus.SKIPPED);
    }

    public void skip(Long userId, Long skippedUserId) {
        if (userId.equals(skippedUserId)) {
            throw new BusinessException("You cannot skip yourself");
        }

        var alreadySkipped = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                userId, skippedUserId, RequestStatus.SKIPPED);
        if (alreadySkipped.isPresent()) {
            return; // ідемпотентно: вже пропущено — нового рядка не створюємо
        }

        Request skip = new Request();
        skip.setFromUser(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found")));
        skip.setToUser(userRepository.findById(skippedUserId)
                .orElseThrow(() -> new BusinessException("Skipped user not found")));
        skip.setStatus(RequestStatus.SKIPPED);
        skip.setCreatedAt(LocalDateTime.now());
        requestRepository.save(skip);
    }

    public List<Request> getSkipped(Long userId) {
        return requestRepository.findByFromUserUserIdAndStatus(userId, RequestStatus.SKIPPED);
    }

    public RequestResponseDTO toResponseDTO(Request request) {
        return new RequestResponseDTO(
                request.getRequestId(),
                request.getFromUser().getUserId(),
                request.getFromUser().getName(),
                request.getToUser().getUserId(),
                request.getToUser().getName(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
