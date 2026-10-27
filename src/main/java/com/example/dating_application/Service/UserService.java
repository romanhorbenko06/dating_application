package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.UserUpdateDTO;
import com.example.dating_application.DTO.Request.UserPatchDTO;
import com.example.dating_application.DTO.Response.BlockedUserDTO;
import com.example.dating_application.DTO.Response.PublicProfileDTO;
import com.example.dating_application.DTO.Response.UserResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.Photo;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.ComplaintRepository;
import com.example.dating_application.Repo.IslandGameRepository;
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.NotificationRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private static final String DEFAULT_BLOCK_REASON = "Violation of the terms of use";

    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final RequestRepository requestRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final PhotoRepository photoRepository;
    private final PhotoStorageService photoStorageService;
    private final NotificationRepository notificationRepository;
    private final IslandGameRepository islandGameRepository;

    public UserService(UserRepository userRepository,
                       ComplaintRepository complaintRepository,
                       RequestRepository requestRepository,
                       ChatRepository chatRepository,
                       MessageRepository messageRepository,
                       BlockRepository blockRepository,
                       PhotoRepository photoRepository,
                       PhotoStorageService photoStorageService,
                       NotificationRepository notificationRepository,
                       IslandGameRepository islandGameRepository) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.requestRepository = requestRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.photoRepository = photoRepository;
        this.photoStorageService = photoStorageService;
        this.notificationRepository = notificationRepository;
        this.islandGameRepository = islandGameRepository;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User register(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        return userRepository.save(user);
    }

    public User updateProfile(Long userId, UserUpdateDTO dto) {
        User u = getById(userId);

        u.setName(dto.getName());
        u.setGender(dto.getGender());
        u.setDateOfBirth(dto.getDateOfBirth());
        u.setCharacterisation(dto.getCharacterisation());
        u.setCity(dto.getCity());
        u.setDatingGoal(dto.getDatingGoal());
        u.setEducationLevel(dto.getEducationLevel());
        u.setTemperament(dto.getTemperament());
        u.setChildrenStatus(dto.getChildrenStatus());

        return userRepository.save(u);
    }

    /** PATCH: оновлюються лише ключі, присутні в JSON; явний null очищає необов'язкове поле. */
    @Transactional
    public User patchProfile(Long userId, UserPatchDTO dto) {
        User u = getById(userId);

        if (dto.hasName()) {
            // @Size(min=2) пропускає "  ".
            String name = dto.getName() == null ? "" : dto.getName().trim();
            if (name.isEmpty()) {
                throw new BusinessException("Name must not be blank");
            }
            u.setName(name);
        }
        if (dto.hasGender()) {
            u.setGender(required(dto.getGender(), "Gender"));
        }
        if (dto.hasDateOfBirth()) {
            u.setDateOfBirth(required(dto.getDateOfBirth(), "Date of birth"));
        }
        if (dto.hasCharacterisation()) {
            u.setCharacterisation(blankToNull(dto.getCharacterisation()));
        }
        if (dto.hasCity()) {
            u.setCity(blankToNull(dto.getCity()));
        }
        if (dto.hasDatingGoal()) {
            u.setDatingGoal(required(dto.getDatingGoal(), "Dating goal"));
        }
        if (dto.hasEducationLevel()) {
            u.setEducationLevel(dto.getEducationLevel());
        }
        if (dto.hasTemperament()) {
            u.setTemperament(dto.getTemperament());
        }
        if (dto.hasChildrenStatus()) {
            u.setChildrenStatus(dto.getChildrenStatus());
        }

        return userRepository.save(u);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new BusinessException(field + " must not be null");
        }
        return value;
    }

    @Transactional
    public User blockUserPermanently(Long userId, String reason) {
        User u = getById(userId);

        if (u.getRole() == Role.ADMIN) {
            throw new BusinessException("Cannot block an administrator");
        }
        if (Boolean.TRUE.equals(u.getBlocked())) {
            throw new BusinessException("User is already blocked");
        }

        u.setBlocked(true);
        u.setBlockedAt(LocalDateTime.now());
        u.setBlockReason(reason == null || reason.isBlank() ? DEFAULT_BLOCK_REASON : reason.trim());

        return userRepository.save(u);
    }

    public List<User> getBlockedUsers(int page, int size) {
        return userRepository.findByIsBlockedTrueOrderByBlockedAtDesc(PageRequest.of(page, size));
    }

    @Transactional
    public void deleteUser(Long userId) {
        User u = getById(userId);

        // Адміністратора не видаляє ніхто, інакше застосунок міг лишитися без модерації.
        if (u.getRole() == Role.ADMIN) {
            throw new BusinessException("Administrators cannot be deleted");
        }

        // Спершу залежні записи, інакше видалення впаде на FK.

        complaintRepository.deleteByReporterUserIdOrReportedUserUserId(userId, userId);

        requestRepository.deleteByFromUserUserIdOrToUserUserId(userId, userId);

        blockRepository.deleteByBlockerUserIdOrBlockedUserId(userId, userId);

        notificationRepository.deleteByRecipientUserIdOrActorUserId(userId, userId);

        List<Chat> chats = chatRepository.findByUser1UserIdOrUser2UserId(userId, userId);
        for (Chat chat : chats) {
            messageRepository.deleteByChatChatId(chat.getChatId());
            islandGameRepository.deleteByChatChatId(chat.getChatId());
        }
        chatRepository.deleteAll(chats);

        // Каскад не знає про диск, тож файли прибираємо окремо.
        List<Photo> photos = photoRepository.findByUserUserId(userId);

        userRepository.delete(u);

        photos.forEach(photo -> photoStorageService.deleteAfterCommit(photo.getFileName()));
    }

    public UserResponseDTO toResponseDTO(User user) {
        return new UserResponseDTO(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getCharacterisation(),
                user.getCity(),
                user.getDatingGoal(),
                user.getEducationLevel(),
                user.getTemperament(),
                user.getChildrenStatus()
        );
    }

    public BlockedUserDTO toBlockedUserDTO(User user) {
        return new BlockedUserDTO(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getBlockedAt(),
                user.getBlockReason()
        );
    }

    public PublicProfileDTO toPublicDTO(User user) {
        return new PublicProfileDTO(
                user.getUserId(),
                user.getName(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getCharacterisation(),
                user.getCity(),
                user.getDatingGoal(),
                user.getEducationLevel(),
                user.getTemperament(),
                user.getChildrenStatus(),
                photoRepository.countByUserUserId(user.getUserId())
        );
    }
}