package com.example.dating_application.DTO.Response;

/**
 * Фото разом із самим зображенням — для пакетної видачі.
 *
 * {@code dataUri} має вигляд "data:image/jpeg;base64,...." і вставляється
 * напряму в <img src="...">. Це обхід незручності браузера: тег <img> не вміє
 * надсилати заголовок Authorization, тож просте посилання на захищений
 * ендпоінт у ньому не спрацювало б.
 *
 * Ціна — base64 роздуває обсяг приблизно на третину. Для кількох фото профілю
 * це прийнятно; якщо фотографій стане багато, є посторінковий {@code url}
 * з {@link PhotoResponseDTO}.
 */
public class PhotoContentDTO {
    private Long photoId;
    private Boolean isMain;
    private String contentType;
    private String dataUri;

    public PhotoContentDTO() {
    }

    public PhotoContentDTO(Long photoId, Boolean isMain, String contentType, String dataUri) {
        this.photoId = photoId;
        this.isMain = isMain;
        this.contentType = contentType;
        this.dataUri = dataUri;
    }

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public void setIsMain(Boolean isMain) {
        this.isMain = isMain;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getDataUri() {
        return dataUri;
    }

    public void setDataUri(String dataUri) {
        this.dataUri = dataUri;
    }
}
