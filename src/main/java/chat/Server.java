package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server extends Thread {
    private final int serverPort;
    private ArrayList<ClientHandler> workerList = new ArrayList<>();

    public Server(int serverPort) {
        this.serverPort = serverPort;
    }

    public List<ClientHandler> getWorkerList() {
        return workerList;
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            while(true) {
                System.out.println("Ready to accept client connection...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket);
                ClientHandler worker = new ClientHandler(this, clientSocket);
                workerList.add(worker);
                worker.start();
            }
        } catch (IOException e) {
            System.out.println("Something went wrong");
        }
    }

    public void removeWorker(ClientHandler serverWorker) {
        workerList.remove(serverWorker);
    }
}
