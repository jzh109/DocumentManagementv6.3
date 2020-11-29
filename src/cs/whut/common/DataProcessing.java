package cs.whut.common;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.*;
import java.util.Enumeration;
import java.util.Hashtable;

public class DataProcessing {
    private static Connection connection;
    private static Statement statement;
    private static ResultSet resultSet;
    private static boolean connectedToDatabase = false;
    public static Hashtable<String, Doc> docs;
    public static Hashtable<String, User> users;
    public static String filename;

    static {
        docs = new Hashtable<>();
        users = new Hashtable<>();
    }

    public static boolean connectToDatabase(String driverName, String url, String name, String password) throws SQLException {
        try {
            Class.forName(driverName);
            connection = DriverManager.getConnection(url, name, password);
            connectedToDatabase = true;
            statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            PrintMessage.print("Successfully connect to Database.");
        } catch (ClassNotFoundException e) {
            PrintMessage.print("Data loading error.");
            PrintMessage.print(e.getLocalizedMessage());
            return false;
        } catch (SQLException e) {
            PrintMessage.print("SQL error");
            PrintMessage.print(e.getLocalizedMessage());
            return false;
        }
        loadAllUsers();
        loadAllDoc();
        return true;
    }

    public static void disconnectFromDatabase() {
        if (connectedToDatabase) {
            try {
                resultSet.close();
                statement.close();
                connection.close();
                PrintMessage.print("Successfully disconnected from database.");
            } catch (SQLException e) {
                PrintMessage.print("Failed in disconnecting from database.");
                e.printStackTrace();
            } finally {
                connectedToDatabase = false;
            }
        }
    }

    private static void loadAllUsers() throws SQLException {
        users.clear();
        User temp;
        if (!connectedToDatabase) {
            throw new SQLException("Not connect to database.");
        }

        String sql = "select * from user_info";
        resultSet = statement.executeQuery(sql);

        while (resultSet.next()) {
            String username = resultSet.getString("username");
            String password = resultSet.getString("password");
            String role = resultSet.getString("role");
            temp = new User(username, password, role) {
            };
            users.put(username, temp);
        }
    }

    private static void loadAllDoc() throws SQLException {
        docs.clear();
        Doc temp;
        if (!connectedToDatabase) {
            throw new SQLException("Not connect to database.");
        }

        String sql = "select * from doc_info";
        resultSet = statement.executeQuery(sql);

        while (resultSet.next()) {
            String ID = resultSet.getString("Id");
            String creator = resultSet.getString("creator");
            Timestamp timestamp = resultSet.getTimestamp("timestamp");
            String description = resultSet.getString("description");
            String filename = resultSet.getString("filename");
            ID = String.format("%04d", Integer.valueOf(ID));
            temp = new Doc(ID, creator, timestamp, description, filename);
            docs.put(ID, temp);
        }
    }

    public static Enumeration<Doc> getAllDocs() {
        return docs.elements();
    }

    public static Enumeration<User> getAllUser() {
        return users.elements();
    }

    public static boolean insertDoc(String id, String creator, Timestamp timestamp, String description, String filename, File inFile) {
        Doc doc;

        if (docs.containsKey(id)) {
            PrintMessage.print("File id " + id + " has existed.");
            return false;
        } else {
            doc = new Doc(id, creator, timestamp, description, filename);
            docs.put(id, doc);
            PrintMessage.print("File " + id + " upload successfully.");
            try {
                PrintMessage.print("File " + id + " upload to database.");
                String sql = "INSERT INTO doc_info VALUES(?,?,?,?,?,?)";
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setString(1, id);
                preparedStatement.setString(2, creator);
                preparedStatement.setString(3, timestamp.toString());
                preparedStatement.setString(4, description);
                preparedStatement.setString(5, filename);
                FileInputStream fileInputStream = new FileInputStream(inFile);
                preparedStatement.setBinaryStream(6, fileInputStream, inFile.length());
                preparedStatement.executeUpdate();
                preparedStatement.close();
                PrintMessage.print("File " + id + " upload successfully to database.");
                loadAllDoc();
            } catch (SQLException | FileNotFoundException e) {
                PrintMessage.print("File " + id + " upload ERROR.");
                PrintMessage.print(e.getLocalizedMessage());
            }
            return true;
        }
    }

    public static InputStream downloadDoc(String fileID) {
        try {
            String sql = "SELECT * FROM doc_info WHERE Id='" + fileID + "'";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            DataProcessing.filename = resultSet.getString("filename");
            return resultSet.getBlob("file").getBinaryStream();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static User searchUser(String name) {
        if (users.containsKey(name)) {
            PrintMessage.print("Search user " + name + "...");
            return users.get(name);
        }
        PrintMessage.print("User " + name + " is not found.");
        return null;
    }

    public static User searchUser(String name, String password) {
        if (users.containsKey(name)) {
            User temp = users.get(name);
            PrintMessage.print("Search user [" + name + "] with password [" + password + "]...");
            if ((temp.getPassword()).equals(password))
                return temp;
        }
        PrintMessage.print("User [" + name + "] with password [" + password + "] is not found.");
        return null;
    }

    public static boolean update(String name, String password, String role) {
        User user;

        if (users.containsKey(name)) {
            users.remove(name);
            if (role.equalsIgnoreCase("administrator"))
                user = new Administrator(name, password, role);
            else if (role.equalsIgnoreCase("operator"))
                user = new Operator(name, password, role);
            else
                user = new Browser(name, password, role);
            users.put(name, user);
            PrintMessage.print("Successfully changed [" + name + "]'s password to [" + password + "].");
            try {
                String sql = "update user_info set password='" + password + "',role='" + role + "' where username='" + name + "'";
                statement.executeUpdate(sql);
                loadAllUsers();
                PrintMessage.print("Successfully changed [" + name + "]'s password to [" + password + "] in database.");
            } catch (SQLException e) {
                PrintMessage.print("Failed in changing [" + name + "]'s password to [" + password + "] in database.");
                PrintMessage.print(e.getLocalizedMessage());
            }
            return true;
        } else {
            PrintMessage.print("User [" + name + "] does not exist.");
            return false;
        }
    }

    public static boolean insert(String name, String password, String role) {
        User user;
        if (users.containsKey(name)) {
            PrintMessage.print("User [" + name + "] has existed. Add user failed.");
            return false;
        } else {
            if (role.equalsIgnoreCase("administrator"))
                user = new Administrator(name, password, role);
            else if (role.equalsIgnoreCase("operator"))
                user = new Operator(name, password, role);
            else
                user = new Browser(name, password, role);
            users.put(name, user);
            PrintMessage.print("Successfully add user name[" + name + "] password[" + password + "] role[" + role + "].");
            try {
                String sql = "insert into user_info (username,password,role) values(?,?,?)";
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setString(1, name);
                preparedStatement.setString(2, password);
                preparedStatement.setString(3, role);
                preparedStatement.executeUpdate();
                loadAllUsers();
                System.out.println(new Timestamp(System.currentTimeMillis()).toString() + "\t\t" + "Successfully add user name[" + name + "] password[" + password + "] role[" + role + "] into database.");
            } catch (SQLException e) {
                System.out.println(new Timestamp(System.currentTimeMillis()).toString() + "\t\t" + "Failed in adding user name[" + name + "] password[" + password + "] role[" + role + "] into database.");
                System.out.println(new Timestamp(System.currentTimeMillis()).toString() + "\t\t" + e.getLocalizedMessage());
                e.printStackTrace();
            }
            return true;
        }
    }

    public static boolean delete(String name) {
        if (users.containsKey(name)) {
            PrintMessage.print("Delete user [" + name + "]");
            users.remove(name);
            try {
                String sql = "delete from user_info where username='" + name + "'";
                statement.executeUpdate(sql);
                loadAllUsers();
                PrintMessage.print("Successfully delete user [" + name + "] from database.");
            } catch (SQLException e) {
                PrintMessage.print("Failed in deleting user [" + name + "] from database.");
                PrintMessage.print(e.getLocalizedMessage());
                e.printStackTrace();
            }
            return true;
        }
        PrintMessage.print("User [" + name + "] does not exist. Delete failed.");
        return false;
    }
}