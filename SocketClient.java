import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javax.swing.*;

@SuppressWarnings("serial")
public class SocketClient extends JFrame implements ActionListener, Runnable {
    JTextArea textArea = new JTextArea();
    JScrollPane jp = new JScrollPane(textArea);
    JTextField input_Text = new JTextField();
    JMenuBar menuBar = new JMenuBar();

    Socket sk;
    BufferedReader br;
    PrintWriter pw;

    public SocketClient() {
        super("Chit Chat");
        setFont(new Font("Arial Black", Font.PLAIN, 12));
        setForeground(new Color(0, 0, 51));
        setBackground(new Color(51, 0, 0));
        textArea.setToolTipText("Chat History");
        textArea.setForeground(new Color(50, 205, 50));
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.BOLD, 13));
        textArea.setBackground(new Color(0, 0, 0));

        // --- CHANGED: uncommented the Help menu and wired it to showHelp() ---
        JMenu helpMenu = new JMenu("Help");
        JMenuItem update = new JMenuItem("Update Information");
        JMenuItem connect_List = new JMenuItem("Visitor List");

        update.addActionListener(e -> showHelp());

        helpMenu.add(update);
        helpMenu.add(connect_List);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
        // --- end CHANGED ---

        getContentPane().add(jp, "Center");
        input_Text.setText("Enter your Message:");
        input_Text.setToolTipText("Enter your Message");
        input_Text.setForeground(new Color(0, 0, 0));
        input_Text.setFont(new Font("Tahoma", Font.BOLD, 11));
        input_Text.setBackground(new Color(230, 230, 250));

        getContentPane().add(input_Text, "South");
        setSize(325, 411);
        setVisible(true);

        input_Text.requestFocus(); //Place cursor at run time, work after screen is shown

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        input_Text.addActionListener(this); //Event registration

        // --- ADDED: show help dialog automatically on startup ---
        showHelp();
        // --- end ADDED ---
    }

    // --- ADDED: help dialog method ---
    private void showHelp() {
        String message =
            "=== ChitChat - How to Use ===\n\n" +
            "STARTING UP\n" +
            "  1. Run SocketServer first.\n" +
            "  2. Run SocketClient to open this window.\n" +
            "  3. Enter the server IP when prompted.\n" +
            "     (Use 127.0.0.1 if server is on this machine)\n" +
            "  4. Enter a nickname.\n\n" +
            "CHATTING\n" +
            "  - Type a message and press Enter to send.\n" +
            "  - All connected users will see your message.\n\n" +
            "MULTIPLE USERS\n" +
            "  - Each person runs SocketClient on their machine.\n" +
            "  - Everyone connects to the same server IP.\n\n" +
            "OVER A LAN\n" +
            "  - Use the server machine's local IP address\n" +
            "    (e.g. 192.168.1.x) instead of 127.0.0.1.\n";

        JTextArea helpText = new JTextArea(message);
        helpText.setEditable(false);
        helpText.setFont(new Font("Monospaced", Font.PLAIN, 12));
        helpText.setBackground(new Color(240, 240, 240));

        JOptionPane.showMessageDialog(
            this,
            new JScrollPane(helpText),
            "How to Use ChitChat",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    // --- end ADDED ---

    public void serverConnection() {
        try {
            String IP = JOptionPane.showInputDialog(this, "Please enter a server IP.", JOptionPane.INFORMATION_MESSAGE);
            sk = new Socket(IP, 1234);

            String name = JOptionPane.showInputDialog(this, "Please enter a nickname", JOptionPane.INFORMATION_MESSAGE);

            //read
            br = new BufferedReader(new InputStreamReader(sk.getInputStream()));

            //writing
            pw = new PrintWriter(sk.getOutputStream(), true);
            pw.println(name); // Send to server side

            new Thread(this).start();

        } catch (Exception e) {
            System.out.println(e + " Socket Connection error");
        }
    }

    public static void main(String[] args) {
        new SocketClient().serverConnection(); //Method call at the same time object creation
    }

    @Override
    public void run() {
        String data = null;
        try {
            while ((data = br.readLine()) != null) {
                textArea.append(data + "\n"); //textArea Decrease the position of the box's scroll bar by the length of the text entered
                textArea.setCaretPosition(textArea.getText().length());
            }
        } catch (Exception e) {
            System.out.println(e + "--> Client run fail");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String data = input_Text.getText();
        pw.println(data); // Send to server side
        input_Text.setText("");
    }
}