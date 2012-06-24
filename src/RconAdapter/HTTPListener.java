package RconAdapter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HTTPListener {
    private HttpServer http;
    private final Thread rconThread;
    private final RconSender rconSender;

    public HTTPListener(String host, int port, String password) {
        this.rconSender = new RconSender(host, port, password);
        this.rconThread = new Thread(this.rconSender, "rcon");

        try {
            http = HttpServer.create(new InetSocketAddress("localhost", 8181), 5);
            http.createContext("/", new RconHandler(this.rconSender));
            http.setExecutor(Executors.newSingleThreadExecutor());
        } catch (IOException ex) {
            Logger.getLogger(HTTPListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void start() {
        this.http.start();
        this.rconThread.start();
    }

    private class RconHandler implements HttpHandler {
        private final RconSender rcon;

        public RconHandler(RconSender rcon) {
            this.rcon = rcon;
        }

        public void handle(HttpExchange he) throws IOException {
            if (he.getRequestMethod().equalsIgnoreCase("GET")) {
                String command = getCmd(he.getRequestURI().getQuery());
                byte[] packet = this.rcon.makePacket(command);

                Headers respHeaders = he.getResponseHeaders();
                respHeaders.set("Content-Type", "text/plain");
                he.sendResponseHeaders(200, 0);
                
                OutputStream respBody = he.getResponseBody();

                byte[] rconResp = this.rcon.sendRequest(packet);

                for (int i = 0; i < rconResp.length; i++) {
                    if (i >= 12) {
                        // Assume no null bytes in string.
                        if (rconResp[i] != (byte) 0) {
                            respBody.write(rconResp[i]);
                        } else {
                            break;
                        }
                    }
                }

                respBody.flush();

                // input was not received in time, send error message. Command may have executed.
                //respBody.write("[ERROR] Rcon command timed out.".getBytes());
            }

            // Empty the request body.
            he.getRequestBody().skip(he.getRequestBody().available());

            he.close();
        }

        private String getCmd(String query) {
            String[] cmds = query.split("cmd=");
            return cmds[1];
        }
    }
}
