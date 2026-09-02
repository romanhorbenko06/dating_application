package com.example.dating_application.Сontroller;

import com.example.dating_application.DTO.Response.PhotoContentDTO;
import com.example.dating_application.DTO.Response.PhotoResponseDTO;
import com.example.dating_application.Entity.Photo;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Service.PhotoBinary;
import com.example.dating_application.Service.PhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PhotoResponseDTO>> getUserPhotos(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        List<PhotoResponseDTO> photos = photoService.getAccessiblePhotos(currentUserId, userId).stream()
                .map(photoService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(photos);
    }

    @GetMapping("/user/{userId}/content")
    public ResponseEntity<List<PhotoContentDTO>> getUserPhotoContents(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(photoService.getAccessiblePhotoContents(currentUserId, userId));
    }

    @GetMapping("/{photoId}/content")
    public ResponseEntity<byte[]> getPhotoContent(@PathVariable Long photoId) {
        Long currentUserId = getCurrentUserId();
        PhotoBinary binary = photoService.getPhotoBinary(currentUserId, photoId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(binary.contentType()))
                // Приватний контент: доступ може зникнути після блокування,
                // тож проміжним кешам віддавати його не можна.
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(binary.data());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponseDTO> uploadPhoto(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "isMain", required = false) Boolean isMain) {

        Long currentUserId = getCurrentUserId();

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }

        Photo photo = photoService.addPhoto(currentUserId, data, isMain);
        return ResponseEntity.ok(photoService.toResponseDTO(photo));
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("@accessControlService.canDeletePhoto(#photoId)")
    public ResponseEntity<Map<String, Object>> deletePhoto(@PathVariable Long photoId) {
        Long currentUserId = getCurrentUserId();
        photoService.deletePhoto(photoId, currentUserId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Photo deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{photoId}/main")
    @PreAuthorize("@accessControlService.canSetMainPhoto(#photoId)")
    public ResponseEntity<PhotoResponseDTO> setMainPhoto(@PathVariable Long photoId) {
        Long currentUserId = getCurrentUserId();
        Photo photo = photoService.setMainPhoto(photoId, currentUserId);
        return ResponseEntity.ok(photoService.toResponseDTO(photo));
    }
}
