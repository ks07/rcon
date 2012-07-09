package RconAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        if (args.length == 3) {
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            String password = args[2];

            savePID("rcon.pid");

            HTTPListener http = new HTTPListener(host, port, password);
            http.start();
        } else {
            System.err.println("Invalid parameters.");
            System.err.println("Usage: java -jar RconAdapter.jar <dest host> <dest port> <password>");
        }
    }

    private static void savePID(String filename) {
        Integer pid = -1;

        try {
            pid = Integer.parseInt((new File("/proc/self")).getCanonicalFile().getName());
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        FileWriter fw = null;

        try {
            File pidFile = new File(filename);
            pidFile.createNewFile();
            fw = new FileWriter(pidFile, false);
            fw.write(pid.toString());
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ex) {}
            }
        }
    }

    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}