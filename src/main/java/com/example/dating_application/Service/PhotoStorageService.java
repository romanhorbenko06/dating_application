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
 * Файлове сховище фотографій: у БД лише ім'я файлу, байти на диску (app.photos.dir).
 * Тека не віддається статикою: доступ до пікселів лише через PhotoService, який перевіряє метч.
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

    /** Ім'я — UUID, розширення за реальною сигнатурою файлу. */
    public String save(byte[] data, ImageFormat format) {
        String fileName = UUID.randomUUID() + "." + format.getExtension();
        Path target = resolveSafely(fileName);
        try {
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store photo file " + fileName, e);
        }
        return fileName;
    }

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

    /** Відсутній на диску файл пропускаємо з попередженням, щоб не валити весь профіль. */
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

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolveSafely(fileName));
        } catch (IOException e) {
            logger.warn("Failed to delete photo file {}", fileName, e);
        }
    }

    /** Видаляє файл лише після коміту: при відкаті транзакції файл інакше було б втрачено. */
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

    /** Захист від "../" у імені файлу. */
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
