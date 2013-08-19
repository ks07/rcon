package RconAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final File PROPERTIES_FILE = new File("config.properties");
    
    // Don't run this where it can't write. Because I'm lazy.
    public static void main(String[] args) throws IOException {
        if (System.getProperty("os.name").contains("inux")) {
            savePID("rcon.pid");
        }
        
        if (!PROPERTIES_FILE.exists()) {
            InputStream in = Main.class.getResourceAsStream(PROPERTIES_FILE.getName());
            OutputStream out = new FileOutputStream(PROPERTIES_FILE);
            byte[] buff = new byte[1024];
            int len;

            while ((len = in.read(buff)) >= 0) {
                out.write(buff, 0, len);
            }

            out.flush();
            out.close();
            in.close();
        }
        
        HashMap<String, String> servers = new HashMap<String, String>();

        BufferedReader br = new BufferedReader(new FileReader(PROPERTIES_FILE));
        Properties prop = new Properties();
        prop.load(br);

        for (Entry e : prop.entrySet()) {
            if (! (e.getKey().equals("bind") || e.getKey().equals("defkey")) ) {
                servers.put((String)e.getKey(), (String)e.getValue());
            }
        }

        HTTPListener http = new HTTPListener(prop.getProperty("bind", "localhost:8181"), servers, prop.getProperty("defkey", "survival"));
        http.start();
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