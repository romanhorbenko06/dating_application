package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByUserUserId(Long userId);

    List<Photo> findByUserUserIdOrderByIsMainDescPhotoIdAsc(Long userId);

    long countByUserUserId(Long userId);
}

