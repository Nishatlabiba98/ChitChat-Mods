import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SocketServer {
    ServerSocket server;
    Socket sk;
    InetAddress addr;

    ArrayList<ServerThread> list = new ArrayList<ServerThread>();

    static Logger logger = Logger.getLogger("ChitChatLogger");

    static final String WEATHER_API_KEY = "YOUR_OPENWEATHERMAP_KEY";
    static final String STOCK_API_KEY   = "YOUR_ALPHAVANTAGE_KEY";

    public SocketServer() {
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
                logger.info("CLIENT CONNECTED: " + sk.getInetAddress());

                ServerThread st = new ServerThread(this);
                addThread(st);
                st.start();
            }
        } catch(IOException e) {
            System.out.println(e + "-> ServerSocket failed");
        }
    }

    private void setupLogger() {
        try {
            FileHandler fh = new FileHandler("chitchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.out.println("Logger setup failed: " + e);
        }
    }

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

    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERLIST:");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).name);
        }
        String packet = sb.toString();
        for (ServerThread st : list) {
            st.pw.println(packet);
        }
    }

    public String handleBotCommand(String message) {
        if (!message.startsWith("/bot ")) return null;

        String command = message.substring(5).trim();

        if (command.startsWith("weather ")) {
            String city = command.substring(8).trim();
            return fetchWeather(city);
        } else if (command.startsWith("stock ")) {
            String ticker = command.substring(6).trim().toUpperCase();
            return fetchStock(ticker);
        } else if (command.equals("sports")) {
            return fetchSports();
        } else {
            return "[ChitBot] Unknown command. Try: /bot weather [city] | /bot stock [TICKER] | /bot sports";
        }
    }

    private String fetchWeather(String city) {
        try {
            String urlStr = "https://api.openweathermap.org/data/2.5/weather?q="
                    + city.replace(" ", "%20")
                    + "&appid=" + WEATHER_API_KEY
                    + "&units=imperial";
            String json  = httpGet(urlStr);
            String desc  = extractJson(json, "description");
            String temp  = extractJson(json, "temp");
            String feels = extractJson(json, "feels_like");
            return "[ChitBot] Weather in " + city + ": "
                    + desc + ", " + temp + "°F (feels like " + feels + "°F)";
        } catch (Exception e) {
            return "[ChitBot] Could not fetch weather. Check your API key or city name.";
        }
    }

    private String fetchStock(String ticker) {
        try {
            String urlStr = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE"
                    + "&symbol=" + ticker
                    + "&apikey=" + STOCK_API_KEY;
            String json   = httpGet(urlStr);
            String price  = extractJson(json, "05. price");
            String change = extractJson(json, "09. change");
            String pct    = extractJson(json, "10. change percent");
            return "[ChitBot] " + ticker + " — $" + price
                    + "  change: " + change + " (" + pct + ")";
        } catch (Exception e) {
            return "[ChitBot] Could not fetch stock data. Check your API key or ticker symbol.";
        }
    }

    private String fetchSports() {
        try {
            String urlStr = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard";
            String json   = httpGet(urlStr);
            StringBuilder sb = new StringBuilder("[ChitBot] NBA scores: ");
            int idx = 0;
            int count = 0;
            while ((idx = json.indexOf("\"shortName\"", idx)) != -1 && count < 5) {
                int start = json.indexOf("\"", idx + 12) + 1;
                int end   = json.indexOf("\"", start);
                sb.append(json, start, end);
                sb.append("  |  ");
                idx = end;
                count++;
            }
            if (count == 0) return "[ChitBot] No NBA games found right now.";
            return sb.toString();
        } catch (Exception e) {
            return "[ChitBot] Could not fetch sports data.";
        }
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "N/A";
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (json.charAt(start) == '"') {
            int end = json.indexOf("\"", start + 1);
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && ",}".indexOf(json.charAt(end)) == -1) end++;
            return json.substring(start, end).trim();
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
            BufferedReader br = new BufferedReader(new InputStreamReader(server.sk.getInputStream()));
            pw = new PrintWriter(server.sk.getOutputStream(), true);
            name = br.readLine();
            SocketServer.logger.info("NICKNAME SET: " + name);

            server.broadCast("**[" + name + "] Entered**");
            server.broadcastUserList();

            String data;
            while((data = br.readLine()) != null) {

                // --- ADDED: relay image packets with sender name prefixed ---
                if (data.startsWith("IMAGE:")) {
                    SocketServer.logger.info("IMAGE from [" + name + "]");
                    server.broadCast("IMAGE:" + name + ":" + data.substring(6));

                } else {
                    String botReply = server.handleBotCommand(data);
                    if (botReply != null) {
                        SocketServer.logger.info("BOT COMMAND from [" + name + "]: " + data);
                        server.broadCast(botReply);
                    } else {
                        SocketServer.logger.info("MESSAGE [" + name + "]: " + data);
                        server.broadCast("[" + name + "] " + data);
                    }
                }
                // --- end ADDED ---
            }
        } catch (Exception e) {
            server.removeThread(this);
            server.broadCast("**[" + name + "] Left**");
            server.broadcastUserList();
            SocketServer.logger.info("CLIENT DISCONNECTED: " + name);
            System.out.println(server.sk.getInetAddress() + " - [" + name + "] Exit");
            System.out.println(e + "---->");
        }
    }
}