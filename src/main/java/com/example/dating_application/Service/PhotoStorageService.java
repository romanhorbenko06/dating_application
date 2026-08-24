package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Файлове сховище фотографій.
 *
 * У БД лежить лише ІМ'Я файлу, самі байти — на диску, у теці поруч із jar-ником
 * (шлях налаштовується через app.photos.dir). Тека створюється в конструкторі,
 * тож застосунок або стартує з робочим сховищем, або падає одразу, а не через
 * тиждень на першому завантаженні фото.
 *
 * Тека НЕ віддається статикою: єдиний шлях до пікселів — через PhotoService,
 * який перевіряє метч. Інакше ключова механіка застосунку (фото лише після
 * взаємної симпатії) трималася б тільки на тому, що ніхто не вгадав посилання.
 */
@Service
public class PhotoStorageService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoStorageService.class);

    private final Path root;

    public PhotoStorageService(@Value("${app.photos.dir:photos}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create photo storage directory: " + root, e);
        }
        logger.info("Photo storage directory: {}", root);
    }

    /**
     * Записує байти у новий файл і повертає згенероване ім'я.
     *
     * Ім'я — UUID: воно унікальне без походу в БД, не залежить від того, як файл
     * називався на пристрої користувача, і його не можна перебрати ззовні.
     * Розширення лишаємо реальне (визначене за сигнатурою) — щоб потім знати,
     * який Content-Type віддавати.
     */
    public String save(byte[] data, ImageFormat format) {
        String fileName = UUID.randomUUID() + "." + format.getExtension();
        Path target = resolveSafely(fileName);
        try {
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            // Проблема сервера, не користувача → 500 із логом, а не 400
            throw new IllegalStateException("Failed to store photo file " + fileName, e);
        }
        return fileName;
    }

    /** Читає один файл. Відсутній файл — це неузгодженість БД і диска, тобто помилка сервера. */
    public byte[] read(String fileName) {
        Path file = resolveSafely(fileName);
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new IllegalStateException("Photo file is missing on disk: " + fileName, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read photo file " + fileName, e);
        }
    }

    /**
     * Пакетне читання: на вхід — список імен із БД, на вихід — байти в тому ж порядку.
     *
     * Файл, якого немає на диску, ПРОПУСКАЄМО з попередженням у лог: краще показати
     * решту фотографій профілю, ніж завалити весь запит через один загублений файл.
     */
    public Map<String, byte[]> readAll(Collection<String> fileNames) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String fileName : fileNames) {
            try {
                result.put(fileName, read(fileName));
            } catch (RuntimeException e) {
                logger.warn("Skipping unreadable photo file {}", fileName, e);
            }
        }
        return result;
    }

    /** Видаляє файл. Відсутність файлу не вважається помилкою — запис у БД усе одно прибираємо. */
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolveSafely(fileName));
        } catch (IOException e) {
            logger.warn("Failed to delete photo file {}", fileName, e);
        }
    }

    /**
     * Видаляє файл ТІЛЬКИ якщо транзакція успішно закомітилась.
     *
     * Якщо видаляти одразу, а транзакція потім відкотиться, ми втратимо файл
     * користувача, який насправді нікуди не подівся — і відновити його вже нізвідки.
     * Зворотний ризик (транзакція пройшла, а файл лишився) дешевший: це просто
     * зайві байти на диску.
     */
    public void deleteAfterCommit(String fileName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(fileName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(fileName);
            }
        });
    }

    /**
     * Захист від виходу за межі теки: ім'я на кшталт "../../application.properties"
     * не повинно дати прочитати чужий файл. Імена генеруємо ми самі, але перевірка
     * лишається — вона коштує нічого, а помилка тут коштувала б усім вмістом диска.
     */
    private Path resolveSafely(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("Photo file name is required");
        }
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root)) {
            throw new BusinessException("Invalid photo file name");
        }
        return file;
    }
}
