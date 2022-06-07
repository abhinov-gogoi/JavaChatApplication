package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server extends Thread {
    private final int serverPort;
    private ArrayList<ClientHandler> clients = new ArrayList<>();

    public Server(int serverPort) {
        this.serverPort = serverPort;
    }

    public List<ClientHandler> getClients() {
        return clients;
    }

    @Override
    public void run() {
        try(ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while(true) {
                System.out.println("Ready to accept client connection...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket);
                ClientHandler new_client = new ClientHandler(this, clientSocket);
                clients.add(new_client);
                new_client.start();
            }
        } catch (Exception e) {
            System.out.println("Something went wrong"+e.getMessage());
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}
