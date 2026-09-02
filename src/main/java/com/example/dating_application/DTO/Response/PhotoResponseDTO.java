package com.example.dating_application.DTO.Response;


public class PhotoResponseDTO {
    private Long photoId;
    private String url;
    private String contentType;
    private Boolean isMain;
    private Long ownerId;

    public PhotoResponseDTO() {
    }

    public PhotoResponseDTO(Long photoId, String url, String contentType, Boolean isMain, Long ownerId) {
        this.photoId = photoId;
        this.url = url;
        this.contentType = contentType;
        this.isMain = isMain;
        this.ownerId = ownerId;
    }

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public void setIsMain(Boolean isMain) {
        this.isMain = isMain;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}
