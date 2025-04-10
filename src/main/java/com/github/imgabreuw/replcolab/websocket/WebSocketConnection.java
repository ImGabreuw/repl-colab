package com.github.imgabreuw.replcolab.websocket;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketConnection implements Runnable {
    private final Socket socket;
    private final WebSocketServer server;
    private boolean handshakeComplete = false;
    private InputStream inputStream;
    private OutputStream outputStream;

    private static final String WEBSOCKET_KEY_CONSTANT = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public WebSocketConnection(Socket socket, WebSocketServer server) {
        this.socket = socket;
        this.server = server;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            if (!performHandshake()) {
                close();
                return;
            }

            // Enviar o código atual para o novo cliente
            sendMessage(server.getCurrentCode());

            // Loop principal para receber mensagens
            while (isConnected()) {
                try {
                    String message = receiveMessage();
                    if (message == null) {
                        break;
                    }
                    server.broadcastMessage(message, this);
                } catch (IOException e) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    private boolean performHandshake() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            String key = null;

            // Ler o request HTTP e encontrar o Sec-WebSocket-Key
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                Pattern keyPattern = Pattern.compile("Sec-WebSocket-Key: (.*)");
                Matcher matcher = keyPattern.matcher(line);
                if (matcher.find()) {
                    key = matcher.group(1);
                }
            }

            if (key == null) {
                return false;
            }

            // Gerar a resposta do handshake
            String acceptKey = generateAcceptKey(key);
            String response =
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

            outputStream.write(response.getBytes());
            handshakeComplete = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String generateAcceptKey(String key) {
        try {
            String concatenated = key + WEBSOCKET_KEY_CONSTANT;
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(concatenated.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public String receiveMessage() throws IOException {
        // Leitura de frame WebSocket
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            return null;
        }

        // Segundo byte contém a máscara e o tamanho do payload
        int secondByte = inputStream.read();
        boolean isMasked = (secondByte & 0x80) != 0;
        int length = secondByte & 0x7F;

        // Determinar o tamanho real do payload
        if (length == 126) {
            length = inputStream.read() << 8 | inputStream.read();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | inputStream.read();
            }
        }

        // Ler as máscaras se os dados estiverem mascarados
        byte[] mask = new byte[4];
        if (isMasked) {
            inputStream.read(mask, 0, 4);
        }

        // Ler o payload
        byte[] payload = new byte[length];
        int read = 0;
        while (read < length) {
            int count = inputStream.read(payload, read, length - read);
            if (count == -1) {
                break;
            }
            read += count;
        }

        // Desmascarar o payload
        if (isMasked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return new String(payload);
    }

    public void sendMessage(String message) throws IOException {
        byte[] payload = message.getBytes();

        // Preparar o cabeçalho do frame
        int headerLength = 2;
        if (payload.length > 125 && payload.length < 65536) {
            headerLength += 2;
        } else if (payload.length >= 65536) {
            headerLength += 8;
        }

        byte[] frame = new byte[headerLength + payload.length];

        // Primeiro byte: FIN bit + opcode (text = 0x01)
        frame[0] = (byte) 0x81;

        // Segundo byte + extensão: tamanho do payload
        if (payload.length <= 125) {
            frame[1] = (byte) payload.length;
        } else if (payload.length < 65536) {
            frame[1] = (byte) 126;
            frame[2] = (byte) (payload.length >>> 8);
            frame[3] = (byte) payload.length;
        } else {
            frame[1] = (byte) 127;
            int offset = 2;
            for (int i = 7; i >= 0; i--) {
                frame[offset++] = (byte) (payload.length >>> (i * 8));
            }
        }

        // Copiar o payload para o frame
        System.arraycopy(payload, 0, frame, headerLength, payload.length);

        // Enviar o frame
        outputStream.write(frame);
        outputStream.flush();
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            server.removeConnection(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return handshakeComplete && !socket.isClosed();
    }
}