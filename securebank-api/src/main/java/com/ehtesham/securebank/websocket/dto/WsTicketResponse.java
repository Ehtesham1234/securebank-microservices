package com.ehtesham.securebank.websocket.dto;

public record WsTicketResponse(String ticket, long expiresInSeconds) {
}
