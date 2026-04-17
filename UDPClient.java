import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
class UDPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        System.out.println("UDP Math Client is running!");
        System.out.println("Server: " + SERVER_IP + ":" + SERVER_PORT);

        try (DatagramSocket clientSocket = new DatagramSocket();
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
            Random random = new Random();

            System.out.print("Enter your client name: ");
            String clientName = userInput.readLine().trim();
            while (clientName.isEmpty()) {
                System.out.print("Name cannot be empty. Enter your client name: ");
                clientName = userInput.readLine().trim();
            }

            String joinResponse = sendAndReceive(clientSocket, serverAddress, "JOIN|" + clientName);
            System.out.println("SERVER -> " + joinResponse);

            if (!joinResponse.startsWith("CONNECTED|")) {
                System.out.println("Could not join server. Exiting.");
                return;
            }

            System.out.println("\nEnter calculations in this format: <number> <operator> <number>");
            System.out.println("Example: 5 + 3");
            System.out.println("Type 'quit' to disconnect.\n");

            int successfulRequests = 0;
            while (true) {
                System.out.print("calc> ");
                String input = userInput.readLine();
                if (input == null) {
                    break;
                }

                input = input.trim();
                if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                    String quitResponse = sendAndReceive(clientSocket, serverAddress, "QUIT|" + clientName);
                    System.out.println("SERVER -> " + quitResponse);
                    break;
                }

                String[] tokens = input.replaceAll("\\s+", "").split(""); //remove all tabs, spaces, and new lines and split into array
                if (tokens.length < 3) {
                    System.out.println("Invalid format. Use: <number> <operator> <number>");
                    continue;
                }

                String left = tokens[0];
                String operator = tokens[1];
                String right = tokens[2];

                String request = String.format("CALC|%s|%s|%s|%s", clientName, left, operator, right);

                int delayMs = 10 + random.nextInt(1000);
                System.out.println("Waiting " + delayMs + " ms before sending request...");
                Thread.sleep(delayMs);

                String response = sendAndReceive(clientSocket, serverAddress, request);
                System.out.println("SERVER -> " + response);

                if (response.startsWith("RESULT|")) {
                    successfulRequests++;
                }
            }
        } catch (Exception e) { //if user input a char that isn't a number, send error msg
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static String sendAndReceive(DatagramSocket socket, InetAddress serverAddress, String message)
            throws IOException {
        //send packet
        byte[] sendBuffer = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);
        socket.send(sendPacket);

        //receive packet
        byte[] receiveBuffer = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);

        return new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8).trim();
    }
}
