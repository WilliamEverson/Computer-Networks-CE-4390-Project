import java.io.*;
import java.net.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class TCPServer {
    private static final int PORT = 9876;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    static class ClientSession {
        String name;
        String address;
        int port;
        LocalDateTime connectTime;
        volatile int requestCount;

        ClientSession(String name, String address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.connectTime = LocalDateTime.now();
            this.requestCount = 0;
        }
    }

    public static void main(String[] args) {
        System.out.println("TCP Math Server is running on port " + PORT + "...");

        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket connectionSocket = welcomeSocket.accept();
                Thread worker = new Thread(new ClientHandler(connectionSocket));
                worker.start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private ClientSession session;
        private String currentClientName;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();

            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String line;
                while ((line = in.readLine()) != null) {
                    String response = handleMessage(line.trim(), clientId);
                    out.println(response);
                    if (response.startsWith("BYE|")) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection error with " + clientId + ": " + e.getMessage());
            } finally {
                if (session != null && currentClientName != null && sessions.containsKey(currentClientName)) {
                    logDisconnect(session, true);
                    sessions.remove(currentClientName);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private String handleMessage(String message, String clientId) {
            if (message.isEmpty()) {
                return "ERROR|Empty message";
            }

            String[] parts = message.split("\\|");
            String type = parts[0];

            switch (type) {
                case "JOIN":
                    return handleJoin(parts, clientId);
                case "CALC":
                    return handleCalc(parts);
                case "QUIT":
                    return handleQuit(parts);
                default:
                    return "ERROR|Unknown message type";
            }
        }

        private String handleJoin(String[] parts, String clientId) {
            if (parts.length != 2) {
                return "ERROR|JOIN format: JOIN|<clientName>";
            }

            String name = parts[1].trim();
            if (name.isEmpty()) {
                return "ERROR|Client name cannot be empty";
            }

            if (sessions.containsKey(name)) {
                return "ERROR|Client name already in use";
            }

            String address = socket.getInetAddress().getHostAddress();
            int port = socket.getPort();
            session = new ClientSession(name, address, port);
            currentClientName = name;
            sessions.put(name, session);

            System.out.println("[JOIN ] " + name + " connected from " + clientId +
                    " at " + session.connectTime.format(FORMATTER));
            return "ACK|0 ";
        }

        private String handleCalc(String[] parts) {
            if (parts.length != 5) {
                return "ERROR|CALC format: CALC|<clientName>|<number>|<operator>|<number>";
            }

            String name = parts[1].trim();
            if (!validateSession(name)) {
                return "ERROR|Client must JOIN before sending calculations";
            }

            try {
                double num1 = Double.parseDouble(parts[2].trim());
                String operator = parts[3].trim();
                double num2 = Double.parseDouble(parts[4].trim());
                double result;

                switch (operator) {
                    case "+":
                        result = num1 + num2;
                        break;
                    case "-":
                        result = num1 - num2;
                        break;
                    case "*":
                        result = num1 * num2;
                        break;
                    case "/":
                        if (num2 == 0) {
                            return "ERROR|Division by zero is not allowed";
                        }
                        result = num1 / num2;
                        break;
                    default:
                        return "ERROR|Unsupported operator. Use +, -, *, /";
                }

                session.requestCount++;
                String expression = formatNumber(num1) + " " + operator + " " + formatNumber(num2);
                String answer = formatNumber(result);

                System.out.println("[CALC ] " + name + " requested: " + expression + " = " + answer);
                return "RESULT|" + expression + " = " + answer+ " |ACK|" + session.requestCount;
            } catch (NumberFormatException e) {
                return "ERROR|Invalid number format";
            }
        }

        private String handleQuit(String[] parts) {
            if (parts.length != 2) {
                return "ERROR|QUIT format: QUIT|<clientName>";
            }

            String name = parts[1].trim();
            if (!validateSession(name)) {
                return "ERROR|Unknown client session";
            }

            logDisconnect(session, false);
            sessions.remove(name);
            return "BYE|Goodbye " + name + " |ACK|" + session.requestCount;
        }

        private boolean validateSession(String name) {
            return session != null && currentClientName != null && currentClientName.equals(name) && sessions.containsKey(name);
        }

        private void logDisconnect(ClientSession clientSession, boolean unexpected) {
            LocalDateTime disconnectTime = LocalDateTime.now();
            long seconds = Duration.between(clientSession.connectTime, disconnectTime).getSeconds();
            String mode = unexpected ? "[DROP ]" : "[QUIT ]";
            System.out.println(mode + " " + clientSession.name + " disconnected at " +
                    disconnectTime.format(FORMATTER) + " | duration=" + seconds + "s | requests=" + clientSession.requestCount);
        }

        private String formatNumber(double value) {
            if (value == (long) value) {
                return String.format("%d", (long) value);
            }
            return String.format("%.4f", value);
        }
    }
}
