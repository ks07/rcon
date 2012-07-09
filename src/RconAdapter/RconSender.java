package RconAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RconSender implements Runnable {
    private final String rconHost;
    private final int rconPort;

    private final byte[] authPacket;
    // Command to send after connect to check if connection was succesful.
    private static final byte[] initPacket = makePacket(2, Command.Exec, "ping");

    // MUST synchronize on this lock before any access to rconSock!
    private final Object sockLock = new Object();
    private Socket rconSock;

    // MUST synchronize on this lock before any access to packetID!
    private final Object IDLock = new Object();
    private int packetID = 3; // 1 and 2 used by initialisation.
    private int getNextID() {
        synchronized(IDLock) {
            return this.packetID++;
        }
    }

    public RconSender(String rconAdd, int rconPort, String password) {
        this.rconHost = rconAdd;
        this.rconPort = rconPort;
        this.authPacket = makePacket(1, Command.Auth, password);
    }

    public void run() {
        try {
            this.connect();

            while (true) {
                    try {
                        // Every 60 seconds send a simple "keepalive".
                        Thread.sleep(60000);

                        byte[] pingPacket = makePacket(this.getNextID(), Command.Exec, "ping");
                        this.sendRequest(pingPacket);
                    } catch (InterruptedException inex) {
                        continue;
                    }
            }
        } catch (Exception ex) {
            Logger.getLogger(RconSender.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("Failed to connect to Rcon: " + ex.getMessage());
            System.exit(1);
        }
    }

    private void connect() throws IOException {
        synchronized(this.sockLock) {
            this.rconSock = new Socket(this.rconHost, rconPort);
            this.rconSock.setSoTimeout(50000);

            this.writePacket(authPacket);
            this.writePacket(initPacket);
        }
    }

    private boolean reconnect() {
        boolean outcome = false;

        // Reset the packet ID.
        synchronized(this.IDLock) {
            this.packetID = 3;
        }

        synchronized(this.sockLock) {
            try {
                if (this.rconSock == null || !this.rconSock.isConnected() || this.rconSock.isClosed()) {
                    this.connect();
                }

                outcome = true;
            } catch (IOException e) {
                this.rconSock = null;
                outcome = false;
            }
        }

        return outcome;
    }

    public byte[] sendRequest(byte[] packet) {
        synchronized(sockLock) {
            try {
                return this.writePacket(packet);
            } catch (IOException ex) {
                if (this.rconSock != null && this.rconSock.isConnected()) {
                    try {
                        this.rconSock.close();
                    } catch (IOException ex1) {
                        Logger.getLogger(RconSender.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }

                Logger.getLogger(RconSender.class.getName()).log(Level.SEVERE, null, ex);
                Logger.getLogger(RconSender.class.getName()).log(Level.INFO, "Reconnecting to server.");
            }
        }
        
        boolean connected = false;
        int attempts = 0;
        int sleepTime = 0;
        
        while (!connected) {
            switch (attempts) {
                case 0:
                    break;
                case 1:
                    sleepTime = 5000;
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                    sleepTime = 30000;
                    break;
                default:
                    sleepTime = 60000;
            }

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                Logger.getLogger(RconSender.class.getName()).log(Level.SEVERE, null, ex);
                return new byte[0];
            }

            connected = this.reconnect();
            attempts++;
        }

        return this.sendRequest(packet);
    }

    private byte[] writePacket(byte[] packet) throws IOException {
        byte[] inputBuffer = new byte[2048];

        synchronized(sockLock) {
            OutputStream out = this.rconSock.getOutputStream();
            InputStream in = this.rconSock.getInputStream();

            out.write(packet);
            in.read(inputBuffer);
        }

        return inputBuffer;
    }

    public byte[] makePacket(String command) {
        return makePacket(this.getNextID(), Command.Exec, command);
    }

    // This method will not block if used simultaneously with another thread.
    private static byte[] makePacket(int packetID, Command cmd, String param1) {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(packetID);
        bb.putInt(cmd.value);

        // 26 byte minimum capacity -> 3 x 4 + 2 bytes minimum, plus ~10 character average response.
        ArrayList<Byte> bl = new ArrayList<Byte>(26);

        // Have to wrap bytes.
        for (byte b : bb.array()) {
            bl.add(new Byte(b));
        }

        // Concatenate the string param.
        for (byte b : param1.getBytes()) {
            bl.add(new Byte(b));
        }

        // Terminate with 2 null bytes.
        bl.add((byte)0);
        bl.add((byte)0);

        // Prefix the message size. Use big endian here, as the byte order is reversed by the for loop.
        bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(bl.size());
        for (byte b : bb.array()) {
            bl.add(0, new Byte(b));
        }

        byte[] ret = new byte[bl.size()];

        for (int i = 0; i < bl.size(); i++) {
            ret[i] = bl.get(i).byteValue();
        }

        return ret;
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

    private enum Command {
        Exec(2),
        Auth(3);

        public final int value;

        private Command(int value) {
            this.value = value;
        }
    }
}
