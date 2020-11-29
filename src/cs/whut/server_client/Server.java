package cs.whut.server_client;

import cs.whut.common.DataProcessing;
import cs.whut.common.Doc;
import cs.whut.common.PrintMessage;
import cs.whut.common.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Objects;

public class Server {
    private static ServerSocket server;
    private static ObjectOutputStream output;
    private static ObjectInputStream input;
    private static Socket connection;

    public static void busyWaiting() throws IOException {
        PrintMessage.print("Waiting for connection...");
        connection = server.accept();
        int counter = 1;
        PrintMessage.print("Connection " + counter + " linked:" + connection.getInetAddress().getHostName());
    }

    public static void getStreams() throws IOException {
        output = new ObjectOutputStream(connection.getOutputStream());
        output.flush();
        input = new ObjectInputStream(connection.getInputStream());
        PrintMessage.print("IO Constructed successfully");
    }

    public static void sendMessage(String message) throws IOException {
        output.writeObject(message);
        output.flush();
    }

    public static void process() throws IOException {
        String message = "";
        do {
            try {
                message = (String) input.readObject();
                PrintMessage.print("CLIENT>>> " + message);
                switch (message) {
                    case "INITIAL_SYSTEM": {
                        sendMessage("INITIAL_SYSTEM");
                        String driverName, url, user, password;
                        driverName = (String) input.readObject() ;
                        url = (String) input.readObject() ;
                        user = (String) input.readObject();
                        password = (String) input.readObject() ;
                        if (initialSystem(driverName, url, user, password)) {
                            sendMessage("CONNECT_TO_DATABASE");
                        } else {
                            sendMessage("UNCONNECTED_TO_DATABASE");
                        }
                        break;
                    }
                    case "LOGIN": {
                        sendMessage("LOGIN");
                        String name, password;
                        name = (String) input.readObject() ;
                        password = (String) input.readObject() ;
                        login(name, password);
                        break;
                    }
                    case "CHANGE": {
                        sendMessage("CHANGE");
                        String name, oldPassword, newPassword, confirmPassword, role;
                        name = (String) input.readObject() ;
                        oldPassword = (String) input.readObject() ;
                        newPassword = (String) input.readObject() ;
                        confirmPassword = (String) input.readObject() ;
                        role = (String) input.readObject() ;
                        change(name, oldPassword, newPassword, confirmPassword, role);
                        break;
                    }
                    case "ADD": {
                        sendMessage("ADD");
                        String name, password, role;
                        name = (String) input.readObject();
                        password = (String) input.readObject() ;
                        role = (String) input.readObject();
                        add(name, password, role);
                        break;
                    }
                    case "DELETE": {
                        sendMessage("DELETE");
                        String name;
                        name = (String) input.readObject();
                        delete(name);
                        break;
                    }
                    case "UPDATE": {
                        sendMessage("UPDATE");
                        String name, password, role;
                        name = (String) input.readObject() ;
                        password = (String) input.readObject() ;
                        role = (String) input.readObject();
                        update(name, password, role);
                        break;
                    }
                    case "GET_USER":
                        sendUser();
                        break;
                    case "GET_DOC":
                        sendDoc();
                        break;
                    case "UPLOAD":
                        sendMessage("UPLOAD");
                        receiveFile();
                        break;
                    case "DOWNLOAD":
                        sendMessage("DOWNLOAD");
                        message = (String) input.readObject();
                        sendFile(message);
                        break;
                }
            } catch (ClassNotFoundException | SQLException classNotFoundException) {
                PrintMessage.print("Unknown object type received");
            }
        } while (!Objects.equals(message, "EXIT") && !Objects.equals(message, "USER_LOGOUT"));
    }

    private static void update(String name, String password, String role) throws IOException, ClassNotFoundException {
        if (DataProcessing.update(name, password, role)) {
            sendMessage("USER_UPDATE");
            input.readObject();
            sendUser();
            input.readObject() ;
            sendUser();
        } else {
            sendMessage("User doesn't exist.");
        }
    }

    private static void delete(String name) throws IOException {
        if (DataProcessing.delete(name)) {
            sendMessage("USER_DELETE");
        } else {
            sendMessage("user doesn't exist");
        }
    }

    private static void add(String name, String password, String role) throws IOException {
        if (DataProcessing.insert(name, password, role)) {
            sendMessage("USER_ADD");
            PrintMessage.print("USER_ADD");
        } else {
            sendMessage("User has existed.");
        }
    }

    private static void change(String name, String oldPassword, String newPassword, String confirmPassword, String role) throws IOException {
        if (DataProcessing.searchUser(name) == null) {
            PrintMessage.print("Cannot find " + name);
            sendMessage("User don't exist.");
            return;
        }

        if (DataProcessing.searchUser(name, oldPassword) == null) {
            PrintMessage.print("The previous password is incorrect.");
            sendMessage("Old Password is incorrect.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            PrintMessage.print("Two new passwords are different.");
            sendMessage("New Passwords are different.");
            return;
        }

        if (DataProcessing.update(name, newPassword, role)) {
            sendMessage("USER_UPDATE");
        } else {
            sendMessage("Password change fail.");
        }
    }

    private static void login(String name, String password) throws IOException {
        User user=null;
        if (DataProcessing.searchUser(name) == null) {
            sendMessage("user don't exist.");
            return;
        } else if ((user = DataProcessing.searchUser(name, password)) == null) {
            sendMessage("username or password is incorrect.");
            return;
        }
        sendMessage("USER_LOGIN:" + name);
        output.writeObject(user);
        output.flush();
    }

    public static boolean initialSystem(String driverName, String url, String user, String password) throws SQLException {
        return DataProcessing.connectToDatabase(driverName, url, user, password);
    }

    public static void CloseConnection() {
        try {
            output.close();
            input.close();
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendFile(String fileID) {
        try {
            InputStream inputStream = DataProcessing.downloadDoc(fileID);
            String filename = DataProcessing.filename;
            output.writeUTF(filename);
            output.flush();

            byte[] bytes = new byte[1024];
            int length;
            long size = 0;
            while ((length = inputStream.read(bytes, 0, bytes.length)) != -1) {
                output.write(bytes, 0, length);
                size += length;
                output.flush();
            }

            inputStream.close();
            PrintMessage.print("======== File has been sent [File Name：" + fileID + "] [Size：" + size + "] ========");
            sendMessage("USER_DOWNLOAD");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendUser() throws IOException {
        Enumeration<User> users = DataProcessing.getAllUser();
        ArrayList<User> u = new ArrayList<>();

        while (users.hasMoreElements()) {
            u.add(users.nextElement());
        }

        User[] user = new User[u.size()];
        int i = 0;
        for (User temp : u) {
            user[i] = temp;
            i++;
        }
        output.writeObject(user);
        output.flush();
    }

    public static void sendDoc() throws IOException {
        Enumeration<Doc> docs = DataProcessing.getAllDocs();
        ArrayList<Doc> d = new ArrayList<>();

        while (docs.hasMoreElements()) {
            d.add(docs.nextElement());
        }
        Doc[] doc = new Doc[d.size()];
        int i = 0;
        for (Doc temp : d) {
            doc[i] = temp;
            i++;
        }
        output.writeObject(doc);
        output.flush();
    }

    public static void receiveFile() {
        try {
            String fileName = input.readUTF();
            long length = input.readLong();
            File file = (File) input.readObject();
            String id, description, creator;
            id = (String) input.readObject() ;
            description = (String) input.readObject() ;
            creator = (String) input.readObject();

            if (DataProcessing.insertDoc(id, creator, new Timestamp(System.currentTimeMillis()), description, fileName, file)) {
                sendMessage("USER_UPLOAD");
            } else {
                sendMessage("Upload failed.");
            }
            PrintMessage.print("======== File has been received [File Name：" + fileName + "] [Size：" + getFormatFileSize(length) + "] ========");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFormatFileSize(long length) {
        double size = ((double) length) / (1 << 30);
        if (size >= 1) {
            return size + "GB";
        }
        size = ((double) length) / (1 << 20);
        if (size >= 1) {
            return size + "MB";
        }
        size = ((double) length) / (1 << 10);
        if (size >= 1) {
            return size + "KB";
        }
        return length + "B";
    }

    public static void main(String[] args)  {
        try {
            server = new ServerSocket(12345);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (true) {
                busyWaiting();
                getStreams();
                process();
                CloseConnection();
                DataProcessing.disconnectFromDatabase();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
