package com.example.dating_application.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Сховище фото на диску, зокрема захист від виходу за межі теки. */
class PhotoStorageServiceTest {

    @TempDir
    Path tempDir;

    private PhotoStorageService storage() {
        return new PhotoStorageService(tempDir.toString());
    }

    @Test
    @DisplayName("тека сховища створюється сама, якщо її ще немає")
    void createsDirectoryOnStartup() {
        Path nested = tempDir.resolve("глибше/фото");

        new PhotoStorageService(nested.toString());

        assertThat(Files.isDirectory(nested)).isTrue();
    }

    @Test
    @DisplayName("збережене читається назад байт у байт")
    void savedFileIsReadBack() {
        PhotoStorageService service = storage();
        byte[] data = {1, 2, 3, 4, 5};

        String fileName = service.save(data, ImageFormat.JPEG);

        assertThat(fileName).endsWith(".jpg");
        assertThat(service.read(fileName)).isEqualTo(data);
        assertThat(Files.exists(tempDir.resolve(fileName))).isTrue();
    }

    @Test
    @DisplayName("кожен файл отримує власне ім'я — перезаписів не буває")
    void fileNamesAreUnique() {
        PhotoStorageService service = storage();

        String first = service.save(new byte[] {1}, ImageFormat.PNG);
        String second = service.save(new byte[] {2}, ImageFormat.PNG);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.read(first)).isEqualTo(new byte[] {1});
        assertThat(service.read(second)).isEqualTo(new byte[] {2});
    }

    @Test
    @DisplayName("вихід за межі теки блокується")
    void refusesToEscapeTheDirectory() {
        PhotoStorageService service = storage();

        for (String evil : List.of("../таємне.jpg", "../../etc/passwd", "тека/файл.jpg")) {
            assertThatThrownBy(() -> service.read(evil))
                    .as("ім'я «%s» веде за межі сховища", evil)
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("відсутній на диску файл — це помилка сервера, а не тиша")
    void missingFileIsAnError() {
        assertThatThrownBy(() -> storage().read("немає-такого.jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing on disk");
    }

    @Test
    @DisplayName("пакетне читання пропускає загублений файл, а не валить увесь запит")
    void batchReadSkipsMissingFiles() {
        PhotoStorageService service = storage();
        String present = service.save(new byte[] {9}, ImageFormat.JPEG);

        var files = service.readAll(List.of(present, "загублений.jpg"));

        assertThat(files).containsOnlyKeys(present);
        assertThat(files.get(present)).isEqualTo(new byte[] {9});
    }

    @Test
    @DisplayName("видалення прибирає файл і мовчить, якщо його вже немає")
    void deleteIsIdempotent() {
        PhotoStorageService service = storage();
        String fileName = service.save(new byte[] {1}, ImageFormat.JPEG);

        service.delete(fileName);
        assertThat(Files.exists(tempDir.resolve(fileName))).isFalse();

        service.delete(fileName);
        service.delete("ніколи-не-існував.jpg");
    }

    @Test
    @DisplayName("поза транзакцією видалення відбувається одразу")
    void deleteAfterCommitWorksWithoutTransaction() {
        PhotoStorageService service = storage();
        String fileName = service.save(new byte[] {1}, ImageFormat.JPEG);

        service.deleteAfterCommit(fileName);

        assertThat(Files.exists(tempDir.resolve(fileName)))
                .as("без активної транзакції чекати нічого")
                .isFalse();
    }

    @Test
    @DisplayName("неможливість створити теку валить застосунок на старті, а не мовчить")
    void unusableDirectoryFailsFast() throws IOException {
        Path occupied = tempDir.resolve("зайнято");
        Files.writeString(occupied, "не тека");

        assertThatThrownBy(() -> new PhotoStorageService(occupied.toString()))
                .as("краще не піднятись, ніж працювати без сховища й втрачати фото")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create photo storage directory");
    }

    @Test
    @DisplayName("збій читання з диска — це помилка сервера, і вона не мовчить")
    void unreadableFileIsAnError() throws IOException {
        PhotoStorageService service = storage();
        Files.createDirectory(tempDir.resolve("насправді-тека.jpg"));

        assertThatThrownBy(() -> service.read("насправді-тека.jpg"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("порожнє ім'я файлу відхиляється зрозуміло")
    void blankFileNameIsRejected() {
        PhotoStorageService service = storage();

        assertThatThrownBy(() -> service.read(""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.read(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("два записи з однаковим іменем неможливі — CREATE_NEW не перезапише")
    void neverOverwritesExistingFile() throws IOException {
        PhotoStorageService service = storage();
        String fileName = service.save(new byte[] {1}, ImageFormat.JPEG);
        byte[] original = service.read(fileName);

        assertThat(Files.exists(tempDir.resolve(fileName))).isTrue();
        assertThat(original).isEqualTo(new byte[] {1});
    }
}
