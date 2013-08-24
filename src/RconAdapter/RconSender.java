package RconAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    @Override
    public void run() {
        try {
            this.connect();
        } catch (Exception ex) {
            Logger.getLogger(RconSender.class.getName()).log(Level.SEVERE, "Failed to connect to Rcon", ex);
            System.exit(1);
        }

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
    }

    private void connect() throws IOException {
        synchronized(this.sockLock) {
            this.rconSock = new Socket(this.rconHost, rconPort);
            this.rconSock.setSoTimeout(30000);

            this.writePacket(authPacket);
            this.writePacket(initPacket);
            System.out.println("connected");
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
                
                Logger.getLogger(RconSender.class.getName()).log(Level.INFO, "Reconnecting to server.", ex);
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
        byte[] inputBuffer = new byte[1460];

        synchronized(sockLock) {
            OutputStream out = this.rconSock.getOutputStream();
            InputStream in = this.rconSock.getInputStream();

//            System.out.println("T: " + bytesToHex(packet));
            out.write(packet);
//            System.out.println("Tc Status:" + rconSock.isClosed());
            
            in.read(inputBuffer);
//            System.out.println("Rx: " + bytesToHex(inputBuffer));
//            System.out.println("Rc Status:" + rconSock.isClosed());
        }

        return inputBuffer;
    }

    public byte[] makePacket(String command) {
        return makePacket(this.getNextID(), Command.Exec, command);
    }

    // This method will not block if used simultaneously with another thread.
    private static byte[] makePacket(int packetID, Command cmd, String param) {
        byte[] paramBytes = param.getBytes();
        int length = 10 + paramBytes.length;
        
        ByteBuffer bb = ByteBuffer.allocate(length + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(length);
        bb.putInt(packetID);
        bb.putInt(cmd.value);

        bb.put(paramBytes);
        bb.put((byte)0);
        bb.put((byte)0);

        return bb.array();
    }

    final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ',';
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
