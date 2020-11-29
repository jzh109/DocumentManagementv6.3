package cs.whut.common;

import java.io.*;
import java.sql.Timestamp;

public class Doc implements Serializable{
    private String ID;
    private String creator;
    private Timestamp timestamp;
    private String description;
    private String filename;

    public Doc(String ID, String creator, Timestamp timestamp, String description, String filename) {
        this.ID = ID;
        this.creator = creator;
        this.timestamp = timestamp;
        this.description = description;
        this.filename = filename;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(description);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        description = (String) in.readObject();
    }

    public String toString() {
        return "[" + ID + "," + creator + "," + timestamp + "," + description + "," + filename + "]";
    }

    public String getID() {
        return ID;
    }

    public String getCreator() {
        return creator;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public String getFilename() {
        return filename;
    }


}