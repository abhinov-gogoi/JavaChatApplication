package chat;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class ClientHandler extends Thread {

    private final Socket clientSocket;
    private final Server server;
    private String login = null;
    private OutputStream outputStream;
    private HashSet<String> topicSet = new HashSet<>();

    private boolean connectedToUserForDirectMessaging = false;
    private String directlyConnectedUser = null;
    private boolean isConnectedMessageShown = false;

    private boolean isLoggedIn = false;
    private boolean isBroadcasting = false;

    public ClientHandler(Server server, Socket clientSocket) throws IOException {
        this.server = server;
        this.clientSocket = clientSocket;
        // for auto login with UUID
        autoLogin();
    }

    private void autoLogin() throws IOException {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String[] tokens = {"login", uuid};

        this.outputStream = clientSocket.getOutputStream();
        handleLogin(outputStream, tokens);
    }

    @Override
    public void run() {
        try {
            handleClientSocket();
        } catch (SocketException e) {
            System.out.println("User logged off in successfully: " + login);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleClientSocket() throws IOException, InterruptedException {
        InputStream inputStream = clientSocket.getInputStream();
        this.outputStream = clientSocket.getOutputStream();

        // show prompt for help
        if (!isLoggedIn) {
            showPrompt();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ( (line = reader.readLine()) != null) {
            String[] tokens = StringUtils.split(line);
            if (tokens != null && tokens.length > 0) {
                String cmd = tokens[0];
                if ("logoff".equals(cmd) || "quit".equalsIgnoreCase(cmd)) {
                    handleLogoff();
                    break;
                } else if ("login".equalsIgnoreCase(cmd)) {
                    handleLogin(outputStream, tokens);
                } else if ("msg".equalsIgnoreCase(cmd)) {
                    String[] tokensMsg = StringUtils.split(line, null, 3);
                    handleMessage(tokensMsg);
                } else if ("join".equalsIgnoreCase(cmd)) {
                    handleJoin(tokens);
                } else if ("broadcast".equalsIgnoreCase(cmd)) {
                    handleBroadcast(outputStream);
                } else if ("connect".equalsIgnoreCase(cmd)) {
                    if (connectedToUserForDirectMessaging) {
                        String msg = "You are already connected for direct messaging. Type 'disconnect' first\n";
                        outputStream.write(msg.getBytes());
                        continue;
                    }
                    String[] tokensMsg = StringUtils.split(line, null, 3);
                    try {
                        connect(outputStream, tokensMsg[1]);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        handleClientSocket();
                    }

                } else if ("disconnect".equalsIgnoreCase(cmd)) {
                    handleDisconnect(outputStream);
                } else if ("leave".equalsIgnoreCase(cmd)) {
                    handleLeave(tokens);
                } else if ("help".equalsIgnoreCase(cmd)) {
                    showPrompt();
                } else if ("users".equalsIgnoreCase(cmd)) {
                    showOnlineUsers(outputStream);
                } else {
                    if(connectedToUserForDirectMessaging) {
                        directMessage(line);
                    }
                    else if(isBroadcasting) {
                        broadcastMessage(line);
                    } else {
                        String msg = "unknown command '" + cmd + "' Type 'help' for list of commands\n";
                        outputStream.write(msg.getBytes());
                    }
                }
            }
        }
        clientSocket.close();
    }

    private void handleBroadcast(OutputStream outputStream) throws IOException {
        isBroadcasting = !isBroadcasting;
        if (isBroadcasting) {
            outputStream.write(("\nBroadcasting is ON.\n" +
                    "You are broadcasting to everyone in the chat .. \n" +
                    "Type 'broadcast' to stop broadcasting\n\n").getBytes());
        } else {
            outputStream.write("\nBroadcasting is OFF.\nType 'help' for list of commands\n".getBytes());
        }
    }

    private void broadcastMessage(String body) throws IOException {
        List<ClientHandler> clients = server.getClients();
        for(ClientHandler client : clients) {
            if (!login.equalsIgnoreCase(client.getLogin())) {
                String outMsg = "--> Broadcast received from " + login + " : " + body + "\n";
                client.send(outMsg);
            }
        }
    }

    private void handleDisconnect(OutputStream outputStream) throws IOException {
        if (connectedToUserForDirectMessaging) {
            String msg = "Disconnected from user : " + directlyConnectedUser + "\n";
            outputStream.write(msg.getBytes());
            connectedToUserForDirectMessaging = false;
            directlyConnectedUser = null;
            isConnectedMessageShown = false;
        }
        else {
            String msg = "You are not connected to any user for direct chat\n" +
                    "Please type 'connect <user>' for a direct line messaging\n";
            outputStream.write(msg.getBytes());
        }
    }

    private void connect(OutputStream outputStream, String connectedTo) throws IOException {
        if (!isConnectedMessageShown) {
            String msg = "\nYou are directly connected to '" + connectedTo + "' Start typing a message...\n" +
                    "Type 'disconnect' to end direct messaging\n\n";
            outputStream.write(msg.getBytes());

            isConnectedMessageShown = true;
            connectedToUserForDirectMessaging = true;
            directlyConnectedUser = connectedTo;
        }
    }

    private void directMessage(String body) throws IOException {

        List<ClientHandler> clients = server.getClients();
        for(ClientHandler client : clients) {
            if (directlyConnectedUser.equalsIgnoreCase(client.getLogin())) {
                // if some user1 connects with another user2, then the other user2 also directly connects with this user1
                OutputStream client_ops = client.outputStream;
                client.connect(client_ops, login);

                String outMsg = "--> Message received from " + login + " : " + body + "\n";
                client.send(outMsg);

            }
        }


    }

    private void handleLeave(String[] tokens) {
        if (tokens.length > 1) {
            String topic = tokens[1];
            topicSet.remove(topic);
        }
    }

    public boolean isMemberOfTopic(String topic) {
        return topicSet.contains(topic);
    }

    private void handleJoin(String[] tokens) {
        if (tokens.length > 1) {
            String topic = tokens[1];
            topicSet.add(topic);
        }
    }

    private void handleMessage(String[] tokens) throws IOException {
        String sendTo = tokens[1];
        directlyConnectedUser = sendTo;
        String body = tokens[2];

        boolean isTopic = sendTo.charAt(0) == '#';

        List<ClientHandler> clients = server.getClients();
        for(ClientHandler client : clients) {
            if (isTopic) {
                if (client.isMemberOfTopic(sendTo)) {
                    String outMsg = "msg " + sendTo + ":" + login + " " + body + "\n";
                    client.send(outMsg);
                }
            } else {
                if (sendTo.equalsIgnoreCase(client.getLogin())) {
                    String outMsg = "msg received from " + login + " : " + body + "\n";
                    client.send(outMsg);
                }
            }
        }
    }

    private void handleLogoff() throws IOException {
        server.removeClient(this);
        List<ClientHandler> clients = server.getClients();

        String onlineMsg = "... "+login+" has left the chat ...\n";
        for(ClientHandler client : clients) {
            if (!login.equals(client.getLogin())) {
                client.send(onlineMsg);
            }
        }
        clientSocket.close();
    }

    public String getLogin() {
        return login;
    }

    private void handleLogin(OutputStream outputStream, String[] tokens) throws IOException {
        if (isLoggedIn) {
            String msg = "You are already logged in as : "+login+"\n";
            outputStream.write(msg.getBytes());
            return;
        }
        if (tokens.length == 2) {
            String username = tokens[1];

            List<ClientHandler> clients = server.getClients();

            // check if another is logged in with same name
            for(ClientHandler client : clients) {
                if (username.equals(client.getLogin())) {
                    String exists = "A user already exists with that username\n";
                    outputStream.write(exists.getBytes());
                    showAllUsers(outputStream);
                    return;
                }
            }

            // success prompt
            String msg = "\n--- SUCCESS --- \nYou are Logged in as : "+username+"\n\n";
            outputStream.write(msg.getBytes());
            this.login = username;
            isLoggedIn = true;
            showPrompt();
            System.out.println("User logged in successfully: " + username);

            // show list of online users to this usernames
            for(ClientHandler client : clients) {
                if (client.getLogin() != null) {
                    if (!username.equals(client.getLogin())) {
                        String msg2 = "Users online : " + client.getLogin() + "\n";
                        send(msg2);
                    }
                }
            }

            String onlineMsg = "A new user joined the server : " + username + "\n";
            for(ClientHandler client : clients) {
                if (!username.equals(client.getLogin())) {
                    client.send(onlineMsg);
                }
            }
        }
    }

    private void showAllUsers(OutputStream outputStream) {
        // TODO
        // all users and online users are the same list.
        // because once user is logged off its removed from all users list also
    }

    private void send(String msg) throws IOException {
        if (login != null) {
            try {
                outputStream.write(msg.getBytes());
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    void showOnlineUsers(OutputStream outputStream) throws IOException {
        List<ClientHandler> clients = server.getClients();
        System.out.println("clients : "+clients);

        if (clients.size()<=1) {
            String msg = "No other users are connected\n";
            outputStream.write(msg.getBytes());
            return;
        }

        for(ClientHandler client : clients) {
            if (client.getLogin() != null) {
                if (!login.equals(client.getLogin())) {
                    String msg2 = "Users online : " + client.getLogin() + "\n";
                    send(msg2);
                }
            }
        }
    }

    private void showPrompt() throws IOException {
        String msg_prompt = null;
        if (!isLoggedIn) {
            msg_prompt =
                    "\n--- Welcome to Chat Server  ---\n\n" +
                    "Please login with a username. \nType 'login <any_username>'\n";
        }
        else {
            msg_prompt =
                    "\nFOR LIST OF ONLINE USERS           --> users\n" +
                    "FOR SENDING MESSAGE TO ANY USER    --> msg <user> <message_body>\n" +
                    "FOR A DIRECT MESSAGING LINE        --> connect <user_to_connect_with>\n" +
                    "FOR BROADCASTING (ON/OFF)          --> broadcast\n" +
                    "FOR LEAVING                        --> quit / logoff\n" +
                    "FOR HELP                           --> help\n\n";
        }

        outputStream.write(msg_prompt.getBytes());
    }
}
