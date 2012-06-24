package RconAdapter;

public class Main {
    public static void main(String[] args) {
        if (args.length == 3) {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String password = args[2];
        
        HTTPListener http = new HTTPListener(host, port, password);
        http.start();
        }
    }
}