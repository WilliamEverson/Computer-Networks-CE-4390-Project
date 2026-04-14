import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class UDPServer {
    private static final int PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("UDP Math Server is running on port " + PORT + "...");
        System.out.println("Protocol:");
        System.out.println("  JOIN|<clientName>");
        System.out.println("  CALC|<clientName>|<number>|<operator>|<number>");
        System.out.println("  QUIT|<clientName>");

        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            while (true) {
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                String message = new String(
                        receivePacket.getData(),
                        0,
                        receivePacket.getLength(),
                        StandardCharsets.UTF_8
                ).trim();

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                String response = processMessage(message, clientAddress, clientPort);
                byte[] sendBuffer = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);
            }
        } catch (SocketException e) {
            System.err.println("Could not bind to port " + PORT + ": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Server I/O error: " + e.getMessage());
        }
    }

    private static String processMessage(String message, InetAddress address, int port) {
        if (message.isEmpty()) {
            return "ERROR|Empty request";
        }

        String[] parts = message.split("\\|");
        String command = parts[0].trim().toUpperCase();

        switch (command) {
            case "JOIN":
                return handleJoin(parts, address, port);
            case "CALC":
                return handleCalc(parts, address, port);
            case "QUIT":
                return handleQuit(parts, address, port);
            default:
                log("Unknown request from " + address.getHostAddress() + ":" + port + " -> " + message);
                return "ERROR|Unknown command";
        }
    }

    private static String handleJoin(String[] parts, InetAddress address, int port) {
        if (parts.length != 2 || parts[1].trim().isEmpty()) {
            return "ERROR|JOIN format: JOIN|<clientName>";
        }

        String clientName = parts[1].trim();
        String clientKey = buildClientKey(clientName, address, port);
        ClientInfo existing = clients.get(clientKey);

        if (existing == null) {
            ClientInfo info = new ClientInfo(clientName, address.getHostAddress(), port);
            clients.put(clientKey, info);
            log(String.format("JOIN  | client=%s | ip=%s | port=%d | attached=%s",
                    clientName, info.ipAddress, info.port, info.connectedAt.format(TS_FORMAT)));
        } else {
            existing.lastSeen = LocalDateTime.now();
            log(String.format("REJOIN| client=%s | ip=%s | port=%d", clientName, existing.ipAddress, existing.port));
        }

        return "ACK|Welcome " + clientName;
    }

    private static String handleCalc(String[] parts, InetAddress address, int port) {
        if (parts.length != 5) {
            return "ERROR|CALC format: CALC|<clientName>|<number>|<operator>|<number>";
        }

        String clientName = parts[1].trim();
        String clientKey = buildClientKey(clientName, address, port);
        ClientInfo info = clients.get(clientKey);

        if (info == null) {
            return "ERROR|Client must JOIN before sending calculations";
        }

        try {
            double left = Double.parseDouble(parts[2].trim());
            String operator = parts[3].trim();
            double right = Double.parseDouble(parts[4].trim());

            double result;
            switch (operator) {
                case "+":
                    result = left + right;
                    break;
                case "-":
                    result = left - right;
                    break;
                case "*":
                    result = left * right;
                    break;
                case "/":
                    if (right == 0) {
                        return "ERROR|Division by zero";
                    }
                    result = left / right;
                    break;
                default:
                    return "ERROR|Unsupported operator. Use +, -, *, /";
            }

            info.requestCount++;
            info.lastSeen = LocalDateTime.now();

            String expression = formatNumber(left) + " " + operator + " " + formatNumber(right);
            log(String.format("CALC  | client=%s | request=%s | result=%s | count=%d",
                    clientName, expression, formatNumber(result), info.requestCount));

            return "RESULT|" + expression + " = " + formatNumber(result);
        } catch (NumberFormatException e) {
            return "ERROR|Operands must be numeric";
        }
    }

    private static String handleQuit(String[] parts, InetAddress address, int port) {
        if (parts.length != 2 || parts[1].trim().isEmpty()) {
            return "ERROR|QUIT format: QUIT|<clientName>";
        }

        String clientName = parts[1].trim();
        String clientKey = buildClientKey(clientName, address, port);
        ClientInfo info = clients.remove(clientKey);

        if (info == null) {
            return "ERROR|Client was not registered";
        }

        info.lastSeen = LocalDateTime.now();
        long secondsConnected = Duration.between(info.connectedAt, info.lastSeen).getSeconds();

        log(String.format("QUIT  | client=%s | ip=%s | port=%d | connectedFor=%ds | totalRequests=%d",
                clientName, info.ipAddress, info.port, secondsConnected, info.requestCount));

        return "BYE|Goodbye " + clientName;
    }

    private static String buildClientKey(String clientName, InetAddress address, int port) {
        return clientName + "@" + address.getHostAddress() + ":" + port;
    }

    private static void log(String entry) {
        System.out.println("[" + LocalDateTime.now().format(TS_FORMAT) + "] " + entry);
    }

    private static String formatNumber(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        }
        return String.format("%.4f", value);
    }

    private static class ClientInfo {
        String clientName;
        String ipAddress;
        int port;
        LocalDateTime connectedAt;
        LocalDateTime lastSeen;
        int requestCount;

        ClientInfo(String clientName, String ipAddress, int port) {
            this.clientName = clientName;
            this.ipAddress = ipAddress;
            this.port = port;
            this.connectedAt = LocalDateTime.now();
            this.lastSeen = this.connectedAt;
            this.requestCount = 0;
        }
    }
}
