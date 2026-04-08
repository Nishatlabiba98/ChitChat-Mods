import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
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

    // user list sidebar
    DefaultListModel<String> userListModel = new DefaultListModel<>();
    JList<String> userList = new JList<>(userListModel);

    // --- ADDED: max image size we allow to send ---
    static final int MAX_IMG_BYTES = 500_000; // 500 KB
    static final int MAX_IMG_WIDTH = 300;     // scale down if wider than this
    // --- end ADDED ---

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

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem update = new JMenuItem("Update Information");
        JMenuItem connect_List = new JMenuItem("Visitor List");
        update.addActionListener(e -> showHelp());
        helpMenu.add(update);
        helpMenu.add(connect_List);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // user list sidebar
        userList.setBackground(new Color(20, 20, 30));
        userList.setForeground(new Color(140, 230, 160));
        userList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        userList.setFixedCellHeight(22);

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(140, 0));
        userScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 90)),
                " Online ",
                0, 0,
                new Font("Monospaced", Font.BOLD, 11),
                new Color(100, 180, 255)));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(jp, BorderLayout.CENTER);
        mainPanel.add(userScroll, BorderLayout.EAST);
        getContentPane().add(mainPanel, "Center");

        input_Text.setText("Enter your Message:");
        input_Text.setToolTipText("Enter your Message");
        input_Text.setForeground(new Color(0, 0, 0));
        input_Text.setFont(new Font("Tahoma", Font.BOLD, 11));
        input_Text.setBackground(new Color(230, 230, 250));

        // --- ADDED: south panel = input field + send image button ---
        JButton imgButton = new JButton("Send Image");
        imgButton.setFont(new Font("Tahoma", Font.PLAIN, 11));
        imgButton.setToolTipText("Send an image to all users (max 500 KB)");
        imgButton.addActionListener(e -> sendImage());

        JPanel southPanel = new JPanel(new BorderLayout(4, 0));
        southPanel.add(input_Text, BorderLayout.CENTER);
        southPanel.add(imgButton,  BorderLayout.EAST);
        getContentPane().add(southPanel, "South");
        // --- end ADDED ---

        setSize(480, 411);
        setVisible(true);

        input_Text.requestFocus();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        input_Text.addActionListener(this);

        showHelp();
    }

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
            "IMAGES\n" +
            "  - Click 'Send Image' to share an image (max 500 KB).\n" +
            "  - Images appear inline in the chat for everyone.\n\n" +
            "BOT COMMANDS\n" +
            "  - /bot weather [city]\n" +
            "  - /bot stock [TICKER]\n" +
            "  - /bot sports\n\n" +
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

    public void serverConnection() {
        try {
            String IP = JOptionPane.showInputDialog(this, "Please enter a server IP.", JOptionPane.INFORMATION_MESSAGE);
            sk = new Socket(IP, 1234);

            String name = JOptionPane.showInputDialog(this, "Please enter a nickname", JOptionPane.INFORMATION_MESSAGE);

            br = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            pw = new PrintWriter(sk.getOutputStream(), true);
            pw.println(name);

            new Thread(this).start();

        } catch (Exception e) {
            System.out.println(e + " Socket Connection error");
        }
    }

    public static void main(String[] args) {
        new SocketClient().serverConnection();
    }

    @Override
    public void run() {
        String data = null;
        try {
            while ((data = br.readLine()) != null) {

                if (data.startsWith("USERLIST:")) {
                    updateUserList(data.substring(9));

                // --- ADDED: handle incoming image packets ---
                } else if (data.startsWith("IMAGE:")) {
                    // format from server: IMAGE:<sender>:<base64>
                    int secondColon = data.indexOf(":", 6); // skip "IMAGE:"
                    String sender = data.substring(6, secondColon);
                    String b64    = data.substring(secondColon + 1);
                    appendToChat("-- " + sender + " sent an image --\n");
                    displayImage(b64);
                // --- end ADDED ---

                } else {
                    textArea.append(data + "\n");
                    textArea.setCaretPosition(textArea.getText().length());
                }
            }
        } catch (Exception e) {
            System.out.println(e + "--> Client run fail");
        }
    }

    // --- ADDED: pick a file, encode it, send to server ---
    private void sendImage() {
        if (pw == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select an image to send (max 500 KB)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length > MAX_IMG_BYTES) {
                JOptionPane.showMessageDialog(this,
                        "File is " + bytes.length / 1024 + " KB.\nPlease choose a file under 500 KB.",
                        "Image Too Large", JOptionPane.WARNING_MESSAGE);
                return;
            }
            pw.println("IMAGE:" + Base64.getEncoder().encodeToString(bytes));
        } catch (Exception ex) {
            appendToChat("[Error reading image: " + ex.getMessage() + "]\n");
        }
    }

    // --- ADDED: decode base64 image and display it inline in the chat ---
    private void displayImage(String base64Data) {
        SwingUtilities.invokeLater(() -> {
            try {
                byte[] bytes        = Base64.getDecoder().decode(base64Data);
                BufferedImage img   = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) { appendToChat("[Could not decode image]\n"); return; }

                // scale down if wider than MAX_IMG_WIDTH
                if (img.getWidth() > MAX_IMG_WIDTH) {
                    int w = MAX_IMG_WIDTH;
                    int h = (int) (img.getHeight() * ((double) w / img.getWidth()));
                    Image scaled      = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    tmp.createGraphics().drawImage(scaled, 0, 0, null);
                    img = tmp;
                }

                // wrap the image in a label and add it to a vertical box
                JLabel imgLabel = new JLabel(new ImageIcon(img));
                imgLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));

                // on first image: replace the plain textArea with a Box
                // that stacks the textArea + image labels vertically
                Component current = jp.getViewport().getView();
                if (current instanceof Box) {
                    ((Box) current).add(imgLabel);
                } else {
                    Box box = Box.createVerticalBox();
                    box.setBackground(Color.BLACK);
                    box.add(current);
                    box.add(imgLabel);
                    jp.setViewportView(box);
                }

                jp.revalidate();
                jp.repaint();
                JScrollBar vsb = jp.getVerticalScrollBar();
                vsb.setValue(vsb.getMaximum());

            } catch (Exception ex) {
                appendToChat("[Image display error: " + ex.getMessage() + "]\n");
            }
        });
    }
    // --- end ADDED ---

    private void updateUserList(String csv) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (!csv.isBlank()) {
                for (String name : csv.split(",")) {
                    userListModel.addElement("● " + name.trim());
                }
            }
        });
    }

    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(text);
            textArea.setCaretPosition(textArea.getText().length());
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String data = input_Text.getText();
        pw.println(data);
        input_Text.setText("");
    }
}