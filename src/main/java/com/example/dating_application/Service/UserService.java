package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.UserUpdateDTO;
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
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.UserRepository;
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

    public UserService(UserRepository userRepository,
                       ComplaintRepository complaintRepository,
                       RequestRepository requestRepository,
                       ChatRepository chatRepository,
                       MessageRepository messageRepository,
                       BlockRepository blockRepository,
                       PhotoRepository photoRepository,
                       PhotoStorageService photoStorageService) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.requestRepository = requestRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.photoRepository = photoRepository;
        this.photoStorageService = photoStorageService;
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

        //  Spring Security

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

    /**
     * Адмін-бан: постійне блокування акаунта. Знімати його не передбачено —
     * рішення модератора остаточне (на відміну від блоку між дейтерами,
     * який власник може зняти сам).
     *
     * Дані користувача лишаються в БД (потрібні для історії скарг),
     * але акаунт перестає автентифікуватися й зникає з усіх видач.
     */
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

    public List<User> getBlockedUsers() {
        return userRepository.findByIsBlockedTrueOrderByBlockedAtDesc();
    }

    @Transactional
    public void deleteUser(Long userId) {
        User u = getById(userId);

        // Прибираємо залежні записи, які посилаються на користувача (FK),
        // інакше видалення впаде на обмеженні цілісності.

        // 1. Скарги, де користувач — автор або об'єкт
        complaintRepository.deleteByReporterUserIdOrReportedUserUserId(userId, userId);

        // 2. Лайки/пропуски, надіслані або отримані користувачем
        requestRepository.deleteByFromUserUserIdOrToUserUserId(userId, userId);

        // 3. Блокування, поставлені користувачем або на нього
        blockRepository.deleteByBlockerUserIdOrBlockedUserId(userId, userId);

        // 4. Чати за участю користувача разом з їхніми повідомленнями
        List<Chat> chats = chatRepository.findByUser1UserIdOrUser2UserId(userId, userId);
        for (Chat chat : chats) {
            messageRepository.deleteByChatChatId(chat.getChatId());
        }
        chatRepository.deleteAll(chats);

        // 5. Файли фотографій з диска. Записи в БД підуть каскадно разом із користувачем,
        //    але каскад не знає про диск — без цього кроку файли лишалися б назавжди.
        List<Photo> photos = photoRepository.findByUserUserId(userId);

        // 6. Сам користувач (фото й теги видаляються каскадно)
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
                user.getChildrenStatus()
        );
    }
}