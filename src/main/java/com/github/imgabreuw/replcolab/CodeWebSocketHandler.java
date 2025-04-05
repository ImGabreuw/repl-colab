package com.github.imgabreuw.replcolab;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.ArrayList;
import java.util.List;

public class CodeWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new ArrayList<>();
    private String currentCode = "";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Envia o código atual ao novo usuário
        sessions.add(session);
        session.sendMessage(new TextMessage(currentCode));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        currentCode = message.getPayload(); // Atualiza o código
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.equals(session)) {
                s.sendMessage(message); // Envia a atualização para todos os outros usuários
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

}
