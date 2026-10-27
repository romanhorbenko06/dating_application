package com.example.dating_application.Service;

import com.example.dating_application.DTO.Request.UserPatchDTO;
import com.example.dating_application.Entity.ChildrenStatus;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.EducationLevel;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Temperament;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.ComplaintRepository;
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.IslandGameRepository;
import com.example.dating_application.Repo.NotificationRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
import com.example.dating_application.Repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** PATCH: ключа немає — не чіпаємо, ключ із null — очищаємо. */
@ExtendWith(MockitoExtension.class)
class UserProfilePatchTest {

    private static final long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private PhotoStorageService photoStorageService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private IslandGameRepository islandGameRepository;

    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, complaintRepository, requestRepository,
                chatRepository, messageRepository, blockRepository, photoRepository,
                photoStorageService, notificationRepository, islandGameRepository);

        user = new User();
        user.setUserId(USER_ID);
        user.setName("Оленка");
        user.setGender(Gender.FEMALE);
        user.setDateOfBirth(LocalDate.of(1995, 5, 5));
        user.setCity("Київ");
        user.setCharacterisation("Люблю каву");
        user.setDatingGoal(DatingGoal.FRIENDSHIP);
        user.setEducationLevel(EducationLevel.MASTER);
        user.setTemperament(Temperament.SANGUINE);
        user.setChildrenStatus(ChildrenStatus.UNDECIDED);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // lenient: тести на відмову до save не доходять.
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("поля, яких немає в тілі, лишаються недоторканими")
    void absentFieldsAreLeftAlone() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setCity("Львів");

        service.patchProfile(USER_ID, dto);

        assertThat(user.getCity()).isEqualTo("Львів");
        assertThat(user.getCharacterisation()).as("це поле навіть не згадувалось").isEqualTo("Люблю каву");
        assertThat(user.getEducationLevel()).isEqualTo(EducationLevel.MASTER);
        assertThat(user.getName()).isEqualTo("Оленка");
    }

    @Test
    @DisplayName("явний null ОЧИЩАЄ необов'язковий енум")
    void explicitNullClearsOptionalEnum() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setEducationLevel(null);

        service.patchProfile(USER_ID, dto);

        assertThat(user.getEducationLevel()).isNull();
    }

    @Test
    @DisplayName("явний null очищає текстове поле")
    void explicitNullClearsText() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setCharacterisation(null);

        service.patchProfile(USER_ID, dto);

        assertThat(user.getCharacterisation()).isNull();
    }

    @Test
    @DisplayName("рядок із самих пробілів зводиться до null, а не зберігається як є")
    void blankStringBecomesNull() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setCity("   ");

        service.patchProfile(USER_ID, dto);

        assertThat(user.getCity())
                .as("«не заповнено» має виглядати в базі однаково")
                .isNull();
    }

    @Test
    @DisplayName("значення обрізається від пробілів по краях")
    void valuesAreTrimmed() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setCity("  Одеса  ");

        service.patchProfile(USER_ID, dto);

        assertThat(user.getCity()).isEqualTo("Одеса");
    }

    @Test
    @DisplayName("PREFER_NOT_TO_SAY — це значення, а не порожнеча")
    void preferNotToSayIsARealValue() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setTemperament(Temperament.PREFER_NOT_TO_SAY);

        service.patchProfile(USER_ID, dto);

        assertThat(user.getTemperament())
                .as("«волію не вказувати» відрізняється від «не вказано»")
                .isEqualTo(Temperament.PREFER_NOT_TO_SAY);
    }

    @Test
    @DisplayName("обов'язкове поле не можна очистити явним null")
    void requiredFieldCannotBeCleared() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setDatingGoal(null);

        assertThatThrownBy(() -> service.patchProfile(USER_ID, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be null");
        assertThat(user.getDatingGoal()).isEqualTo(DatingGoal.FRIENDSHIP);
    }

    @Test
    @DisplayName("ім'я з самих пробілів відхиляється")
    void blankNameIsRejected() {
        UserPatchDTO dto = new UserPatchDTO();
        dto.setName("   ");

        assertThatThrownBy(() -> service.patchProfile(USER_ID, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be blank");
        assertThat(user.getName()).isEqualTo("Оленка");
    }

    @Test
    @DisplayName("порожній PATCH нічого не змінює")
    void emptyPatchChangesNothing() {
        service.patchProfile(USER_ID, new UserPatchDTO());

        assertThat(user.getName()).isEqualTo("Оленка");
        assertThat(user.getCity()).isEqualTo("Київ");
        assertThat(user.getCharacterisation()).isEqualTo("Люблю каву");
        assertThat(user.getEducationLevel()).isEqualTo(EducationLevel.MASTER);
        assertThat(user.getTemperament()).isEqualTo(Temperament.SANGUINE);
        assertThat(user.getChildrenStatus()).isEqualTo(ChildrenStatus.UNDECIDED);
    }
}
