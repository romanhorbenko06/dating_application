package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByUserUserId(Long userId);

    /**
     * Усі фото користувача одним запитом, головне — першим.
     * Саме звідси береться список імен файлів для пакетного читання з диска.
     */
    List<Photo> findByUserUserIdOrderByIsMainDescPhotoIdAsc(Long userId);

    long countByUserUserId(Long userId);
}

