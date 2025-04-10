package com.github.imgabreuw.replcolab.websocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebSocketServer {
    private final int port;
    private ServerSocket serverSocket;
    private final List<WebSocketConnection> connections = new ArrayList<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private String currentCode = "";
    private boolean running = false;
    private static WebSocketServer instance;

    private WebSocketServer(int port) {
        this.port = port;
    }

    public static synchronized WebSocketServer getInstance(int port) {
        if (instance == null) {
            instance = new WebSocketServer(port);
        }
        return instance;
    }

    public synchronized void start() {
        if (running) {
            return; // Evita iniciar mais de uma vez
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Servidor WebSocket iniciado na porta " + port);

            threadPool.execute(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        System.out.println("Nova conexão: " + socket.getRemoteSocketAddress());
                        WebSocketConnection connection = new WebSocketConnection(socket, this);
                        connections.add(connection);
                        threadPool.execute(connection);
                    } catch (IOException e) {
                        if (running) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor WebSocket: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void broadcastMessage(String message, WebSocketConnection sender) {
        currentCode = message;
        for (WebSocketConnection connection : new ArrayList<>(connections)) {
            if (connection != sender && connection.isConnected()) {
                try {
                    connection.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getCurrentCode() {
        return currentCode;
    }

    public void removeConnection(WebSocketConnection connection) {
        connections.remove(connection);
        System.out.println("Conexão fechada. Conexões ativas: " + connections.size());
    }
}