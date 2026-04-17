import java.io.*;
import java.net.*;
import java.util.Random;

class TCPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 9876;

    public static void main(String[] args) {
        System.out.println("TCP Client is running!");

        try (
            Socket clientSocket = new Socket(SERVER_IP, SERVER_PORT);
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter outToServer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            Random random = new Random();

            System.out.print("Enter client name: ");
            String clientName = inFromUser.readLine().trim();

            outToServer.println("JOIN|" + clientName);
            String response = inFromServer.readLine();
            System.out.println("FROM SERVER: " + response);

            if (response == null || !response.startsWith("ACK|")) {
                System.out.println("Could not join server. Exiting.");
                return;
            }

            System.out.println("Enter at least 3 math expressions in the format: number operator number");
            System.out.println("Examples: 5 + 3, 10 * 4, 9 / 2");
            System.out.println("Type 'quit' when finished.");

            int sentCount = 0;
            while (true) {
                System.out.print("> ");
                String userInput = inFromUser.readLine();
                if (userInput == null) {
                    break;
                }
                userInput = userInput.trim();

                if (userInput.equalsIgnoreCase("quit")) {
                    if (sentCount < 3) {
                        System.out.println("Please send at least 3 calculation requests before quitting.");
                        continue;
                    }
                    outToServer.println("QUIT|" + clientName);
                    System.out.println("FROM SERVER: " + inFromServer.readLine());
                    break;
                }

                String[] tokens = userInput.split("\\s+");
                if (tokens.length != 3) {
                    System.out.println("Invalid input. Use: number operator number");
                    continue;
                }

                String calcMessage = "CALC|" + clientName + "|" + tokens[0] + "|" + tokens[1] + "|" + tokens[2];
                outToServer.println(calcMessage);
                System.out.println("FROM SERVER: " + inFromServer.readLine());
                sentCount++;

                int delay = 1000 + random.nextInt(3000);
                System.out.println("Waiting " + delay + " ms before next request...");
                Thread.sleep(delay);
            }
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Client interrupted.");
        }
    }
}
