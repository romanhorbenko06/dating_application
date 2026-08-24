package com.example.dating_application.Service;

/**
 * Сирі байти одного фото разом із типом — для віддачі картинки як є
 * (без base64). Внутрішня структура сервісного шару, не DTO.
 */
public record PhotoBinary(String contentType, byte[] data) {
}
