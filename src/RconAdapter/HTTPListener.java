package RconAdapter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HTTPListener {
    private HttpServer http;
    private final Map<String, RconTarget> rconTargets = new HashMap<String, RconTarget>();
    
    private static class RconTarget {
        public final Thread thread;
        public final RconSender sender;
        
        public RconTarget(Thread t, RconSender s) {
            thread = t;
            sender = s;
        }
    }

    public HTTPListener(String bind, Map<String, String> confTargets, String defKey) {
        try {
            URI bindURI = new URI("http://" + bind);
            
            if (bindURI.getHost() == null || bindURI.getPort() == -1) {
                throw new URISyntaxException(bindURI.toString(), "The bind address must specify both a host and port!");
            } else {
                http = HttpServer.create(new InetSocketAddress(bindURI.getHost(), bindURI.getPort()), 10);
                http.setExecutor(Executors.newSingleThreadExecutor());
            }
        } catch (IOException ex) {
            Logger.getLogger(HTTPListener.class.getName()).log(Level.SEVERE, null, ex);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HTTPListener.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        for (Map.Entry<String, String> targ : confTargets.entrySet()) {
            try {
                URI u = new URI("rcon://" + targ.getValue());
                System.out.println("rcon://" + targ.getValue() + "###" + u.getHost() + "###" + u.getPort() + "###" + u.getRawUserInfo());
                RconSender rs = new RconSender(u.getHost(), u.getPort(), u.getRawUserInfo());
                Thread t = new Thread(rs, "rcon");

                RconTarget rt = new RconTarget(t, rs);
                RconHandler rh = new RconHandler(rt);
                
                rconTargets.put(targ.getKey(), rt);
                http.createContext("/" + targ.getKey(), rh);
                
                if (defKey.equals(targ.getKey())) {
                    http.createContext("/", rh);
                }
            } catch (URISyntaxException e) {
                Logger.getLogger(HTTPListener.class.getName()).log(Level.SEVERE, "Failed to parse rcon target: " + targ.getKey(), e);
                continue;
            }
        }
    }

    public void start() {
        for (RconTarget t : rconTargets.values()) {
            t.thread.start();
        }
        this.http.start();
    }

    private String getKey(String path) {
        return path.replace('/', ' ').replace("+", " ").trim();
    }

    private class RconHandler implements HttpHandler {
        private final RconTarget rcon;
        
        RconHandler(RconTarget rcon) {
            this.rcon = rcon;
        }
        
        @Override
        public void handle(HttpExchange he) throws IOException {
            if (he.getRequestMethod().equalsIgnoreCase("GET") && he.getRequestURI().getQuery() != null && he.getRequestURI().getQuery().startsWith("cmd=")) {
                String command = getCmd(he.getRequestURI().getQuery());
                String key = getKey(he.getRequestURI().getPath());
                byte[] packet = rcon.sender.makePacket(command);

                Headers respHeaders = he.getResponseHeaders();
                respHeaders.set("Content-Type", "text/plain");
                he.sendResponseHeaders(200, 0);
                
                OutputStream respBody = he.getResponseBody();

                byte[] rconResp = rcon.sender.sendRequest(packet);

                if (rconResp.length > 12) {
                    for (int i = 12; i < rconResp.length; i++) {
                        if (rconResp[i] == (byte) 0xA7) {
                            // 0xA7 is the simoleon/section char - colour code.
                            // Skip this byte, and the following byte.
                            i++;
                        } else if (rconResp[i] != (byte) 0) {
                            // Assume no null bytes in string.
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

            if (cmds.length > 1) {
                // Java doesn't interpret '+' as a space
                return cmds[1].replace("+", " ").trim();
            } else {
                return "";
            }
        }
    }
}
