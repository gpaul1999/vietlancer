package com.vietlancer.chat;

import java.security.Principal;

/** Principal gắn với phiên WebSocket sau khi xác thực JWT ở bước CONNECT. */
public record StompPrincipal(String name, Long userId) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
