package com.example.dating_application.Websocket;

/**
 * Конверт WebSocket-події: {type, payload}.
 * Дозволяє клієнту розрізняти типи подій (нове повідомлення, квитанція прочитання тощо).
 */
public class WsEvent {
    private String type;
    private Object payload;

    public WsEvent() {
    }

    public WsEvent(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}