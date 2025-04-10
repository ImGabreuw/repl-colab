package com.github.imgabreuw.replcolab.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Configuration
public class WebSocketConfig {

    @Value("${websocket.port:8888}")
    private int websocketPort;

    private WebSocketServer webSocketServer;

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        webSocketServer = WebSocketServer.getInstance(websocketPort);
        webSocketServer.start();
    }

    @PreDestroy
    public void onDestroy() {
        if (webSocketServer != null) {
            webSocketServer.stop();
        }
    }
}