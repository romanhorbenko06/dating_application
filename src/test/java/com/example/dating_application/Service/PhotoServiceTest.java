package com.example.dating_application.Service;

import com.example.dating_application.Entity.Photo;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Правила завантаження фото: ліміт на людину, головна світлина, відмови на
 * сміття. Обробку пікселів перевіряє ImageProcessingServiceTest — тут саме
 * рішення «приймати чи ні» й «що стає головним».
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    private static final long USER_ID = 1L;
    private static final int MAX_PHOTOS = 6;

    @Mock private PhotoRepository photoRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private PhotoStorageService photoStorageService;

    private PhotoService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PhotoService(photoRepository, requestRepository, userRepository,
                blockRepository, photoStorageService,
                new ImageProcessingService(1080, 0.85f, 50_000_000L), MAX_PHOTOS);

        user = new User();
        user.setUserId(USER_ID);
        user.setName("Власниця фото");

        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(photoStorageService.save(any(), any())).thenReturn("saved-file.jpg");
        lenient().when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(photoRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("порожній файл відхиляється")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.addPhoto(USER_ID, new byte[0], false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> service.addPhoto(USER_ID, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("файл, що не є зображенням, відхиляється до всякої обробки")
    void rejectsNonImage() {
        byte[] pdf = "%PDF-1.4 насправді не картинка".getBytes();

        assertThatThrownBy(() -> service.addPhoto(USER_ID, pdf, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported image format");
        verify(photoStorageService, never()).save(any(), any());
    }

    @Test
    @DisplayName("сьома світлина не приймається — ліміт шість")
    void enforcesPhotoLimit() throws Exception {
        when(photoRepository.countByUserUserId(USER_ID)).thenReturn((long) MAX_PHOTOS);

        assertThatThrownBy(() -> service.addPhoto(USER_ID, jpeg(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Photo limit reached");
        verify(photoStorageService, never()).save(any(), any());
    }

    @Test
    @DisplayName("перша світлина стає головною сама, без прохання")
    void firstPhotoBecomesMainAutomatically() throws Exception {
        when(photoRepository.countByUserUserId(USER_ID)).thenReturn(0L);

        Photo photo = service.addPhoto(USER_ID, jpeg(), false);

        assertThat(photo.getMain())
                .as("інакше анкета лишилась би без аватарки")
                .isTrue();
    }

    @Test
    @DisplayName("наступні світлини головними не стають")
    void laterPhotosAreNotMainByDefault() throws Exception {
        when(photoRepository.countByUserUserId(USER_ID)).thenReturn(3L);

        Photo photo = service.addPhoto(USER_ID, jpeg(), false);

        assertThat(photo.getMain()).isFalse();
    }

    @Test
    @DisplayName("нова головна світлина знімає позначку з попередньої")
    void newMainPhotoClearsThePreviousOne() throws Exception {
        Photo previous = new Photo();
        previous.setPhotoId(10L);
        previous.setMain(true);
        previous.setUser(user);

        when(photoRepository.countByUserUserId(USER_ID)).thenReturn(1L);
        when(photoRepository.findByUserUserId(USER_ID)).thenReturn(List.of(previous));

        Photo added = service.addPhoto(USER_ID, jpeg(), true);

        assertThat(added.getMain()).isTrue();
        assertThat(previous.getMain())
                .as("двох головних бути не може")
                .isFalse();
    }

    @Test
    @DisplayName("на диск лягає оброблений файл, а не той, що надіслали")
    void storesProcessedBytes() throws Exception {
        when(photoRepository.countByUserUserId(USER_ID)).thenReturn(0L);
        byte[] original = png();

        service.addPhoto(USER_ID, original, true);

        verify(photoStorageService).save(
                org.mockito.ArgumentMatchers.argThat(bytes ->
                        bytes.length > 0
                                && ImageFormat.detect(bytes) == ImageFormat.JPEG
                                && !java.util.Arrays.equals(bytes, original)),
                any());
    }

    @Test
    @DisplayName("чуже фото видалити не можна")
    void cannotDeleteSomeoneElsesPhoto() {
        User stranger = new User();
        stranger.setUserId(99L);
        Photo theirs = new Photo();
        theirs.setPhotoId(5L);
        theirs.setUser(stranger);
        when(photoRepository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.deletePhoto(5L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete other user's photo");
        verify(photoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("чуже фото не можна зробити головним")
    void cannotSetSomeoneElsesPhotoAsMain() {
        User stranger = new User();
        stranger.setUserId(99L);
        Photo theirs = new Photo();
        theirs.setPhotoId(5L);
        theirs.setUser(stranger);
        when(photoRepository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.setMainPhoto(5L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot modify other user's photo");
    }

    @Test
    @DisplayName("дії з неіснуючим фото дають зрозумілу помилку")
    void missingPhotoIsReported() {
        when(photoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePhoto(404L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Photo not found");
        assertThatThrownBy(() -> service.setMainPhoto(404L, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("фото неіснуючого користувача не завантажується")
    void rejectsUnknownUser() throws Exception {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPhoto(77L, jpeg(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private static byte[] jpeg() throws IOException {
        return image("jpg");
    }

    private static byte[] png() throws IOException {
        return image("png");
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
