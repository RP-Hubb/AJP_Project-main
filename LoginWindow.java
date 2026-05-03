import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * LoginWindow.java
 * ────────────────
 * The first window the user sees.
 * A simple, clean login panel where they enter a username and click "Join Chat".
 *
 * When the user successfully logs in, this window closes and ChatWindow opens.
 * If the server is unreachable, an error message is shown here.
 */
public class LoginWindow extends JFrame {

    // ── UI Components ────────────────────────────────────────
    private JTextField  usernameField;
    private JButton     joinButton;
    private JLabel      statusLabel;

    // ── Colors & Fonts ───────────────────────────────────────
    // We define a consistent dark theme here so both windows look the same
    static final Color BG_DARK    = new Color(18, 18, 28);
    static final Color BG_PANEL   = new Color(28, 30, 48);
    static final Color BG_INPUT   = new Color(38, 40, 62);
    static final Color ACCENT     = new Color(99, 102, 241);   // indigo
    static final Color ACCENT_H   = new Color(129, 132, 255);  // lighter indigo
    static final Color TEXT_MAIN  = new Color(228, 230, 240);
    static final Color TEXT_MUTED = new Color(120, 125, 155);
    static final Color SUCCESS    = new Color(62, 207, 142);
    static final Color DANGER     = new Color(240, 98, 146);

    // ── Constructor ──────────────────────────────────────────
    public LoginWindow() {
        setTitle("WebChat — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(400, 320);
        setLocationRelativeTo(null); // centre on screen
        getContentPane().setBackground(BG_DARK);

        buildUI();
    }

    /** Assembles all the UI components. */
    private void buildUI() {
        // ── Root layout ──────────────────────────────────────
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 40, 6, 40);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.gridx  = 0;

        // ── 💬 Icon label ─────────────────────────────────────
        JLabel icon = new JLabel("💬", SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        gbc.gridy = 0;
        gbc.insets = new Insets(28, 40, 4, 40);
        add(icon, gbc);

        // ── Title ─────────────────────────────────────────────
        JLabel title = new JLabel("WebChat", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_MAIN);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 40, 2, 40);
        add(title, gbc);

        // ── Subtitle ──────────────────────────────────────────
        JLabel subtitle = new JLabel("Real-time group chat", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(TEXT_MUTED);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 40, 18, 40);
        add(subtitle, gbc);

        // ── Username field ────────────────────────────────────
        usernameField = new JTextField();
        styleTextField(usernameField, "Enter your username…");
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 40, 10, 40);
        add(usernameField, gbc);

        // ── Join button ───────────────────────────────────────
        joinButton = new JButton("Join Chat →");
        styleButton(joinButton, ACCENT);
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 40, 10, 40);
        add(joinButton, gbc);

        // ── Status / error label ──────────────────────────────
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(DANGER);
        gbc.gridy = 5;
        gbc.insets = new Insets(0, 40, 16, 40);
        add(statusLabel, gbc);

        // ── Event listeners ───────────────────────────────────
        // Click button OR press Enter in the text field → attempt login
        ActionListener loginAction = e -> attemptLogin();
        joinButton.addActionListener(loginAction);
        usernameField.addActionListener(loginAction);

        // Focus username field on startup
        SwingUtilities.invokeLater(() -> usernameField.requestFocusInWindow());
    }

    /**
     * Called when the user clicks "Join Chat" or presses Enter.
     * Validates input, then tries to connect.
     */
    private void attemptLogin() {
        String username = usernameField.getText().trim();

        // Validate
        if (username.isEmpty()) {
            showError("Please enter a username.");
            return;
        }
        if (username.length() > 20) {
            showError("Username must be 20 characters or fewer.");
            return;
        }

        // Disable UI while connecting
        setStatus("Connecting…", TEXT_MUTED);
        joinButton.setEnabled(false);
        usernameField.setEnabled(false);

        // Connect in a background thread so the UI doesn't freeze
        new Thread(() -> {
            ChatClient client = new ChatClient(null); // placeholder — ChatWindow will own the real listener
            try {
                // We create the real ChatWindow (which owns the client) here
                ChatWindow chatWindow = new ChatWindow(username);

                // Connection succeeded — switch windows on the EDT
                SwingUtilities.invokeLater(() -> {
                    dispose();             // close login window
                    chatWindow.setVisible(true);
                });

            } catch (Exception ex) {
                // Connection failed — show error on the EDT
                SwingUtilities.invokeLater(() -> {
                    showError("Could not connect. Is the server running?");
                    joinButton.setEnabled(true);
                    usernameField.setEnabled(true);
                });
            }
        }).start();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setForeground(DANGER);
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    /** Applies dark-theme styling to a text field and adds placeholder text. */
    static void styleTextField(JTextField field, String placeholder) {
        field.setPreferredSize(new Dimension(0, 38));
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_MAIN);
        field.setCaretColor(TEXT_MAIN);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(8, new Color(55, 58, 85), 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        // Placeholder effect
        field.setText(placeholder);
        field.setForeground(TEXT_MUTED);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(TEXT_MAIN);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(TEXT_MUTED);
                }
            }
        });
    }

    /** Applies accent-colour styling to a button with a hover effect. */
    static void styleButton(JButton btn, Color color) {
        btn.setPreferredSize(new Dimension(0, 38));
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        // Hover effect
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(ACCENT_H); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(color);    }
        });
    }

    // ── Entry Point ───────────────────────────────────────────

    public static void main(String[] args) {
        // Use system look-and-feel as a base, then override with our colors
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Always create/modify Swing components on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }
}
