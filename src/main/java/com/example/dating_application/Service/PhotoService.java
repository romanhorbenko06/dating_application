package com.example.dating_application.Service;

import com.example.dating_application.DTO.Response.PhotoContentDTO;
import com.example.dating_application.DTO.Response.PhotoResponseDTO;
import com.example.dating_application.Entity.Photo;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Фотографії профілю.
 *
 * Розподіл обов'язків: цей сервіс відповідає за ПРАВА і записи в БД,
 * {@link PhotoStorageService} — за байти на диску. Ім'я файлу — єдине, що їх
 * пов'язує, і воно ніколи не виходить назовні через API.
 */
@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final PhotoStorageService photoStorageService;
    private final ImageProcessingService imageProcessingService;
    private final int maxPhotosPerUser;

    public PhotoService(PhotoRepository photoRepository, RequestRepository requestRepository,
                        UserRepository userRepository, BlockRepository blockRepository,
                        PhotoStorageService photoStorageService,
                        ImageProcessingService imageProcessingService,
                        @Value("${app.photos.max-per-user:6}") int maxPhotosPerUser) {
        this.photoRepository = photoRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
        this.photoStorageService = photoStorageService;
        this.imageProcessingService = imageProcessingService;
        this.maxPhotosPerUser = maxPhotosPerUser;
    }

    public List<Photo> getUserPhotos(Long userId) {
        return photoRepository.findByUserUserIdOrderByIsMainDescPhotoIdAsc(userId);
    }

    /**
     * Завантаження фото: перевіряємо формат за сигнатурою, кладемо байти на диск,
     * зберігаємо в БД лише ім'я файлу.
     *
     * Перше фото автоматично стає головним — інакше профіль без явного вибору
     * лишався б без аватарки.
     */
    @Transactional
    public Photo addPhoto(Long userId, byte[] data, Boolean isMain) {
        if (data == null || data.length == 0) {
            throw new BusinessException("Photo file is empty");
        }

        ImageFormat format = ImageFormat.detect(data);
        if (format == null) {
            throw new BusinessException("Unsupported image format. Allowed: " + ImageFormat.allowed());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        long existingCount = photoRepository.countByUserUserId(userId);
        if (existingCount >= maxPhotosPerUser) {
            throw new BusinessException("Photo limit reached (max " + maxPhotosPerUser + ")");
        }

        // На диск лягає НЕ те, що надіслав клієнт: метадані зрізані (там бувають GPS-координати),
        // поворот застосований до пікселів, розмір обмежений. Формат на виході завжди один.
        byte[] processed = imageProcessingService.process(data, format);
        String fileName = photoStorageService.save(processed, ImageProcessingService.OUTPUT_FORMAT);

        boolean main = Boolean.TRUE.equals(isMain) || existingCount == 0;
        if (main) {
            clearMainFlag(userId);
        }

        Photo photo = new Photo();
        photo.setFileName(fileName);
        photo.setMain(main);
        photo.setUser(user);

        return photoRepository.save(photo);
    }

    /**
     * Видалення: спершу запис у БД, потім файл. Якщо видалили головне фото —
     * головним стає наступне, щоб профіль не лишився без аватарки.
     */
    @Transactional
    public void deletePhoto(Long photoId, Long userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new BusinessException("Photo not found"));

        if (!photo.getUser().getUserId().equals(userId)) {
            throw new BusinessException("Cannot delete other user's photo");
        }

        boolean wasMain = Boolean.TRUE.equals(photo.getMain());
        String fileName = photo.getFileName();

        photoRepository.delete(photo);

        if (wasMain) {
            List<Photo> rest = photoRepository.findByUserUserIdOrderByIsMainDescPhotoIdAsc(userId);
            if (!rest.isEmpty()) {
                Photo next = rest.get(0);
                next.setMain(true);
                photoRepository.save(next);
            }
        }

        photoStorageService.deleteAfterCommit(fileName);
    }

    @Transactional
    public Photo setMainPhoto(Long photoId, Long userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new BusinessException("Photo not found"));

        if (!photo.getUser().getUserId().equals(userId)) {
            throw new BusinessException("Cannot modify other user's photo");
        }

        clearMainFlag(photo.getUser().getUserId());

        photo.setMain(true);
        return photoRepository.save(photo);
    }

    private void clearMainFlag(Long userId) {
        List<Photo> userPhotos = photoRepository.findByUserUserId(userId);
        userPhotos.forEach(p -> p.setMain(false));
        photoRepository.saveAll(userPhotos);
    }

    public boolean canViewPhotos(Long viewerId, Long targetUserId) {
        // Власник завжди бачить свої фото
        if (viewerId.equals(targetUserId)) {
            return true;
        }

        // Адміністратор (глядач) бачить будь-чиї фото
        User viewer = userRepository.findById(viewerId).orElse(null);
        if (viewer != null && viewer.getRole() == Role.ADMIN) {
            return true;
        }

        // Забанений адміном акаунт зникає з поля зору повністю — як профіль і чат
        User target = userRepository.findById(targetUserId).orElse(null);
        if (target == null || Boolean.TRUE.equals(target.getBlocked())) {
            return false;
        }

        // Блок у будь-якому напрямку скасовує доступ до фото навіть після метчу
        if (blockRepository.existsBetween(viewerId, targetUserId)) {
            return false;
        }

        // Метч = прийнятий лайк у БУДЬ-ЯКОМУ напрямку між двома користувачами.
        // (у цій моделі лайк A→B, який B прийняв, дає один ACCEPTED-запис)
        boolean matched =
                requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                        viewerId, targetUserId, RequestStatus.ACCEPTED).isPresent()
                || requestRepository.findByFromUserUserIdAndToUserUserIdAndStatus(
                        targetUserId, viewerId, RequestStatus.ACCEPTED).isPresent();

        return matched;
    }

    public List<Photo> getAccessiblePhotos(Long viewerId, Long targetUserId) {
        if (canViewPhotos(viewerId, targetUserId)) {
            return getUserPhotos(targetUserId);
        }

        return List.of();
    }

    /**
     * Пакетна видача: один запит у БД по імена файлів, одне читання пачки з диска,
     * на виході — готові картинки. Без метчу повертається порожній список
     * (а не 403): для стрічки це нормальний стан «фото ще закриті».
     */
    @Transactional(readOnly = true)
    public List<PhotoContentDTO> getAccessiblePhotoContents(Long viewerId, Long targetUserId) {
        List<Photo> photos = getAccessiblePhotos(viewerId, targetUserId);
        if (photos.isEmpty()) {
            return List.of();
        }

        List<String> fileNames = photos.stream().map(Photo::getFileName).toList();
        Map<String, byte[]> files = photoStorageService.readAll(fileNames);

        List<PhotoContentDTO> result = new ArrayList<>();
        for (Photo photo : photos) {
            byte[] data = files.get(photo.getFileName());
            if (data == null) {
                continue; // файл загубився на диску — решту фото все одно віддаємо
            }
            String contentType = contentTypeOf(photo);
            result.add(new PhotoContentDTO(
                    photo.getPhotoId(),
                    photo.getMain(),
                    contentType,
                    "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(data)
            ));
        }
        return result;
    }

    /**
     * Одне фото сирими байтами. На відміну від пакетної видачі, тут закритий
     * доступ — це саме 403: клієнт просив конкретний файл і має знати, що йому відмовлено.
     */
    @Transactional(readOnly = true)
    public PhotoBinary getPhotoBinary(Long viewerId, Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new BusinessException("Photo not found"));

        if (!canViewPhotos(viewerId, photo.getUser().getUserId())) {
            throw new AccessDeniedException("Photos are visible only after a mutual like");
        }

        return new PhotoBinary(contentTypeOf(photo), photoStorageService.read(photo.getFileName()));
    }

    public PhotoResponseDTO toResponseDTO(Photo photo) {
        return new PhotoResponseDTO(
                photo.getPhotoId(),
                "/api/photos/" + photo.getPhotoId() + "/content",
                contentTypeOf(photo),
                photo.getMain(),
                photo.getUser().getUserId()
        );
    }

    /** Тип визначаємо за розширенням збереженого файлу — воно проставлене за сигнатурою при завантаженні. */
    private String contentTypeOf(Photo photo) {
        String fileName = photo.getFileName();
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        ImageFormat format = dot < 0 ? null : ImageFormat.byExtension(fileName.substring(dot + 1));
        return format == null ? "application/octet-stream" : format.getContentType();
    }
}
