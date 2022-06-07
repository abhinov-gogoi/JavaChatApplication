package chat;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws UnknownHostException {
        int port = 9091;
        MeterServer server = new MeterServer(port);
        server.start();
        System.out.println("Server started on port : "+port);
        System.out.println("IP Address : " + InetAddress.getLocalHost().getHostAddress());
    }
}
