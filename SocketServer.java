import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SocketServer {
    ServerSocket server;
    Socket sk;
    InetAddress addr;

    ArrayList<ServerThread> list = new ArrayList<ServerThread>();

    // --- ADDED: one logger shared across the whole server ---
    static Logger logger = Logger.getLogger("ChitChatLogger");

    public SocketServer() {
        // --- ADDED: set up the log file on startup ---
        setupLogger();

        try {
            addr = InetAddress.getByName("127.0.0.1");
            //addr = InetAddress.getByName("192.168.43.1");

            server = new ServerSocket(1234, 50, addr);
            System.out.println("\n Waiting for Client connection");
            SocketClient.main(null);
            while(true) {
                sk = server.accept();
                System.out.println(sk.getInetAddress() + " connect");

                // --- ADDED: log the new connection ---
                logger.info("CLIENT CONNECTED: " + sk.getInetAddress());

                ServerThread st = new ServerThread(this);
                addThread(st);
                st.start();
            }
        } catch(IOException e) {
            System.out.println(e + "-> ServerSocket failed");
        }
    }

    // --- ADDED: configures the logger to write to chitchat.log ---
    private void setupLogger() {
        try {
            // append=true means it won't wipe the file on every restart
            FileHandler fh = new FileHandler("chitchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false); // stops duplicate output to terminal
        } catch (IOException e) {
            System.out.println("Logger setup failed: " + e);
        }
    }
    // --- end ADDED ---

    public void addThread(ServerThread st) {
        list.add(st);
    }

    public void removeThread(ServerThread st) {
        list.remove(st);
    }

    public void broadCast(String message) {
        for(ServerThread st : list) {
            st.pw.println(message);
        }
    }

    public static void main(String[] args) {
        new SocketServer();
    }
}

class ServerThread extends Thread {
    SocketServer server;
    PrintWriter pw;
    String name;

    public ServerThread(SocketServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // read
            BufferedReader br = new BufferedReader(new InputStreamReader(server.sk.getInputStream()));

            // writing
            pw = new PrintWriter(server.sk.getOutputStream(), true);
            name = br.readLine();

            // --- ADDED: log nickname ---
            SocketServer.logger.info("NICKNAME SET: " + name);

            server.broadCast("**["+name+"] Entered**");

            String data;
            while((data = br.readLine()) != null) {
                if(data == "/list") {
                    pw.println("a");
                }

                // --- ADDED: log every message ---
                SocketServer.logger.info("MESSAGE [" + name + "]: " + data);

                server.broadCast("["+name+"] "+ data);
            }
        } catch (Exception e) {
            server.removeThread(this);
            server.broadCast("**["+name+"] Left**");

            // --- ADDED: log disconnect ---
            SocketServer.logger.info("CLIENT DISCONNECTED: " + name);

            System.out.println(server.sk.getInetAddress()+" - ["+name+"] Exit");
            System.out.println(e + "---->");
        }
    }
}