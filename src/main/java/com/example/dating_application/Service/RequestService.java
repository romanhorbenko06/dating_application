package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.SendRequestDTO;
import com.example.dating_application.DTO.Response.RequestResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.UserTagsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final BlockRepository blockRepository;
    private final PhotoRepository photoRepository;
    private final UserTagsRepository userTagsRepository;

    public RequestService(RequestRepository requestRepository,
                          UserRepository userRepository,
                          ChatService chatService,
                          BlockRepository blockRepository,
                          PhotoRepository photoRepository,
                          UserTagsRepository userTagsRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.blockRepository = blockRepository;
        this.photoRepository = photoRepository;
        this.userTagsRepository = userTagsRepository;
    }

    /** Заповнений chat означає, що лайк був зустрічним і став метчем. */
    public record SendResult(Request request, Chat chat) {
    }

    @Transactional
    public SendResult send(Long fromUserId, SendRequestDTO dto) {
        if (dto.getToUserId() == null) {
            throw new BusinessException("toUserId is required");
        }
        if (fromUserId.equals(dto.getToUserId())) {
            throw new BusinessException("You cannot like yourself");
        }

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

        // Метч міг виникнути з їхнього лайку, тоді ACCEPTED-рядок належить їм.
        var acceptedByThem = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                dto.getToUserId(), fromUserId, RequestStatus.ACCEPTED);
        if (acceptedByThem.isPresent()) {
            throw new BusinessException("You are already matched with this user");
        }

        // Вони лайкнули першими: приймаємо їхній лайк замість другого рядка назустріч.
        var pendingFromThem = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                dto.getToUserId(), fromUserId, RequestStatus.PENDING);
        if (pendingFromThem.isPresent()) {
            Request theirs = pendingFromThem.get();
            Chat chat = accept(theirs.getRequestId(), fromUserId);
            return new SendResult(theirs, chat);
        }

        var toUser = userRepository.findById(dto.getToUserId())
                .orElseThrow(() -> new BusinessException("To user not found"));
        if (Boolean.TRUE.equals(toUser.getBlocked())) {
            throw new BusinessException("Interaction with this user is not available");
        }
        // Адміністратор у знайомствах не бере участі.
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

        return new SendResult(requestRepository.save(r), null);
    }

    @Transactional
    public Chat accept(Long requestId, Long userId) {

        Request r = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!r.getToUser().getUserId().equals(userId)) {
            throw new BusinessException("Can only accept requests sent to you");
        }

        if (r.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request already processed");
        }

        if (blockRepository.existsBetween(r.getFromUser().getUserId(), r.getToUser().getUserId())) {
            throw new BusinessException("Interaction with this user is not available");
        }

        r.setStatus(RequestStatus.ACCEPTED);
        requestRepository.save(r);

        // Наш зустрічний PENDING-лайк закриваємо тим самим метчем.
        requestRepository
                .findByFromUserUserIdAndToUserUserIdAndStatus(
                        userId, r.getFromUser().getUserId(), RequestStatus.PENDING)
                .ifPresent(mine -> {
                    mine.setStatus(RequestStatus.ACCEPTED);
                    requestRepository.save(mine);
                });

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

    public long countIncomingLikes(Long userId) {
        return requestRepository.countIncomingLikes(userId);
    }

    /** Без заблокованих і забанених; відсів у SQL, щоб не ламати пагінацію. */
    public List<Request> incoming(Long userId, int page, int size) {
        return requestRepository.findIncomingVisible(
                userId, RequestStatus.PENDING, PageRequest.of(page, size));
    }

    public List<Request> sent(Long userId, int page, int size) {
        return requestRepository.findByFromUserUserIdAndStatusNotOrderByCreatedAtDescRequestIdDesc(
                userId, RequestStatus.SKIPPED, PageRequest.of(page, size));
    }

    public void skip(Long userId, Long skippedUserId) {
        if (userId.equals(skippedUserId)) {
            throw new BusinessException("You cannot skip yourself");
        }

        var alreadySkipped = requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                userId, skippedUserId, RequestStatus.SKIPPED);
        if (alreadySkipped.isPresent()) {
            return;
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

    public List<Request> getSkipped(Long userId, int page, int size) {
        return requestRepository.findByFromUserUserIdAndStatusOrderByCreatedAtDescRequestIdDesc(
                userId, RequestStatus.SKIPPED, PageRequest.of(page, size));
    }

    /** Разом із симпатією віддаємо дані для картки, щоб клієнт не добирав їх окремо. */
    public RequestResponseDTO toResponseDTO(Request request) {
        var from = request.getFromUser();

        return new RequestResponseDTO(
                request.getRequestId(),
                from.getUserId(),
                from.getName(),
                request.getToUser().getUserId(),
                request.getToUser().getName(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt(),
                from.getDateOfBirth(),
                from.getCity(),
                photoRepository.countByUserUserId(from.getUserId()),
                userTagsRepository.countSharedTags(from.getUserId(), request.getToUser().getUserId())
        );
    }
}
