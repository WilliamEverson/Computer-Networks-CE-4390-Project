### Overview
This project implements a UDP-based client-server application for performing basic math operations.
The server handles multiple clients, tracks their activity, and processes calculation requests.
### Protocol Design
The following message formats were used:  
JOIN|  
CALC|#|#|#|#  
QUIT|  
Server responses: ACK|Welcome  
RESULT| = BYE|Goodbye ERROR|
### Implementation Details
The server uses DatagramSocket to listen for UDP packets and processes requests in a loop. Client
information is stored in memory to track connection time and activity. The client sends a JOIN
message, followed by multiple CALC requests at random intervals, and finally a QUIT message to
terminate the session.
### How to Compile and Run
Compile: javac UDPServer.java UDPClient.java  
Run Server: java UDPServer  
Run Client: java UDPClient  
### Challenges
Handling UDP's connectionless nature required implementing our own protocol for tracking clients.
Ensuring correct parsing of messages and maintaining logs for multiple clients were key challenges.
### Conclusion
The project demonstrates how UDP can be used to build a lightweight client-server system with a
custom protocol.