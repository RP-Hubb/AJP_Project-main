import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * ChatWindow.java  [UPDATED]
 * ──────────────────────────
 * Changes from original:
 *
 *   1. Layout changed from BorderLayout(NORTH/CENTER/SOUTH) to a
 *      split layout: CENTER = message area, EAST = online sidebar.
 *
 *   2. Sidebar contains:
 *        • "N online" count label  (onlineCountLabel)
 *        • JList<String>           (userListModel / userJList)
 *        The current user is highlighted with "(You)" appended.
 *
 *   3. ChatClient.MessageListener now has two extra methods:
 *        onOnlineCountReceived(int)    → updates onlineCountLabel
 *        onUserListReceived(String[])  → rebuilds userListModel
 *      Both use SwingUtilities.invokeLater() for thread safety.
 *
 *   4. onMessageReceived() is unchanged — still calls appendMessage().
 *
 * No existing chat logic was removed or broken.
 */
public class ChatWindow extends JFrame implements ChatClient.MessageListener {

    // ── Theme (inherited from LoginWindow) ────────────────────
    private static final Color BG_DARK    = LoginWindow.BG_DARK;
    private static final Color BG_PANEL   = LoginWindow.BG_PANEL;
    private static final Color BG_INPUT   = LoginWindow.BG_INPUT;
    private static final Color ACCENT     = LoginWindow.ACCENT;
    private static final Color ACCENT_H   = LoginWindow.ACCENT_H;
    private static final Color TEXT_MAIN  = LoginWindow.TEXT_MAIN;
    private static final Color TEXT_MUTED = LoginWindow.TEXT_MUTED;
    private static final Color SUCCESS    = LoginWindow.SUCCESS;
    private static final Color DANGER     = LoginWindow.DANGER;

    // ── Components ────────────────────────────────────────────
    private JTextPane            messagePane;
    private JTextField           inputField;
    private JButton              sendButton;
    private JButton              leaveButton;

    // ── NEW: sidebar widgets ──────────────────────────────────
    private JLabel               onlineCountLabel; // "3 online"
    private DefaultListModel<String> userListModel;   // data model for the JList
    private JList<String>        userJList;         // the visible list component

    // ── Networking ────────────────────────────────────────────
    private final ChatClient client;
    private final String     username;

    // ── Styled document ───────────────────────────────────────
    private StyledDocument doc;
    private Style styleTime, styleUser, styleMessage, styleSystem;

    // ── Constructor ───────────────────────────────────────────
    public ChatWindow(String username) throws IOException {
        this.username = username;

        this.client = new ChatClient(this);
        this.client.connect(username);

        setTitle("WebChat — " + username);
        setSize(800, 540);                         // wider to fit sidebar
        setMinimumSize(new Dimension(560, 400));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        buildUI();
        setupStyles();
        setupWindowClose();
    }

    // ── UI Construction ───────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildHeader(),   BorderLayout.NORTH);
        add(buildCenter(),   BorderLayout.CENTER); // message area + sidebar side by side
        add(buildInputBar(), BorderLayout.SOUTH);
    }

    /** Header: icon, title, current user label, leave button */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(45, 48, 75)),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel iconLbl  = new JLabel("💬");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        JLabel titleLbl = new JLabel("WebChat");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(TEXT_MAIN);
        left.add(iconLbl);
        left.add(titleLbl);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JLabel headerLabel = new JLabel("You: " + username);
        headerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        headerLabel.setForeground(TEXT_MUTED);

        leaveButton = new JButton("Leave Chat");
        leaveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leaveButton.setForeground(DANGER);
        leaveButton.setBackground(new Color(60, 20, 35));
        leaveButton.setBorder(new RoundBorder(6, new Color(100, 40, 60), 1));
        leaveButton.setOpaque(true);
        leaveButton.setBorderPainted(true);
        leaveButton.setFocusPainted(false);
        leaveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        leaveButton.setPreferredSize(new Dimension(100, 28));
        leaveButton.addActionListener(e -> leaveChat());

        right.add(headerLabel);
        right.add(leaveButton);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    /**
     * NEW: center area = message pane (grows) + sidebar (fixed 180px wide).
     * Previously the message pane was added directly to CENTER.
     */
    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG_DARK);
        center.add(buildMessages(), BorderLayout.CENTER);
        center.add(buildSidebar(),  BorderLayout.EAST);   // ← NEW
        return center;
    }

    /** Scrollable message pane (unchanged from original). */
    private JScrollPane buildMessages() {
        messagePane = new JTextPane();
        messagePane.setEditable(false);
        messagePane.setBackground(BG_DARK);
        messagePane.setForeground(TEXT_MAIN);
        messagePane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messagePane.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        doc = messagePane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(messagePane);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.getVerticalScrollBar().setBackground(BG_DARK);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * NEW: right-side panel with "N online" count and a JList of usernames.
     *
     * Structure:
     * ┌─────────────────┐
     * │  ONLINE  3 ●    │  ← onlineCountLabel
     * ├─────────────────┤
     * │  Alice (You)    │  ← JList rows (DefaultListModel)
     * │  Bob            │
     * │  Charlie        │
     * └─────────────────┘
     */
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_PANEL);
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                new Color(45, 48, 75)));

        // ── Top: section title + live count ───────────────────
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setBackground(BG_PANEL);
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        JLabel sectionTitle = new JLabel("ONLINE");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        sectionTitle.setForeground(TEXT_MUTED);

        // This label is updated by onOnlineCountReceived()
        onlineCountLabel = new JLabel("0 ●");
        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        onlineCountLabel.setForeground(SUCCESS);

        topBar.add(sectionTitle,    BorderLayout.WEST);
        topBar.add(onlineCountLabel, BorderLayout.EAST);

        // ── User list (JList backed by DefaultListModel) ───────
        userListModel = new DefaultListModel<>();
        userJList     = new JList<>(userListModel);

        userJList.setBackground(BG_PANEL);
        userJList.setForeground(TEXT_MAIN);
        userJList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userJList.setSelectionBackground(new Color(45, 48, 75));
        userJList.setSelectionForeground(TEXT_MAIN);
        userJList.setFixedCellHeight(30);
        userJList.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        userJList.setFocusable(false); // no keyboard selection needed

        // Custom cell renderer: adds a green dot and highlights "(You)"
        userJList.setCellRenderer(new UserCellRenderer(username));

        JScrollPane listScroll = new JScrollPane(userJList);
        listScroll.setBorder(null);
        listScroll.setBackground(BG_PANEL);
        listScroll.getViewport().setBackground(BG_PANEL);
        listScroll.getVerticalScrollBar().setBackground(BG_PANEL);

        sidebar.add(topBar,     BorderLayout.NORTH);
        sidebar.add(listScroll, BorderLayout.CENTER);
        return sidebar;
    }

    /** Input bar (unchanged from original). */
    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 48, 75)),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        inputField = new JTextField();
        LoginWindow.styleTextField(inputField, "Type a message…");

        sendButton = new JButton("Send");
        LoginWindow.styleButton(sendButton, ACCENT);
        sendButton.setPreferredSize(new Dimension(80, 38));

        ActionListener sendAction = e -> sendMessage();
        inputField.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        bar.add(inputField, BorderLayout.CENTER);
        bar.add(sendButton, BorderLayout.EAST);
        return bar;
    }

    // ── Text Styles ───────────────────────────────────────────

    private void setupStyles() {
        styleTime = messagePane.addStyle("time", null);
        StyleConstants.setForeground(styleTime, new Color(90, 96, 130));
        StyleConstants.setFontSize(styleTime, 12);
        StyleConstants.setFontFamily(styleTime, "Segoe UI");

        styleUser = messagePane.addStyle("user", null);
        StyleConstants.setForeground(styleUser, ACCENT_H);
        StyleConstants.setBold(styleUser, true);
        StyleConstants.setFontSize(styleUser, 14);
        StyleConstants.setFontFamily(styleUser, "Segoe UI");

        styleMessage = messagePane.addStyle("msg", null);
        StyleConstants.setForeground(styleMessage, TEXT_MAIN);
        StyleConstants.setFontSize(styleMessage, 14);
        StyleConstants.setFontFamily(styleMessage, "Segoe UI");

        styleSystem = messagePane.addStyle("system", null);
        StyleConstants.setForeground(styleSystem, TEXT_MUTED);
        StyleConstants.setItalic(styleSystem, true);
        StyleConstants.setFontSize(styleSystem, 12);
        StyleConstants.setFontFamily(styleSystem, "Segoe UI");
        StyleConstants.setAlignment(styleSystem, StyleConstants.ALIGN_CENTER);
    }

    // ── Message Rendering (unchanged) ─────────────────────────

    private void appendMessage(String rawText) {
        try {
            boolean isSystem = rawText.contains("***") ||
                               rawText.startsWith("---") ||
                               rawText.startsWith(" Welcome");

            if (isSystem) {
                doc.setParagraphAttributes(doc.getLength(), 1,
                        messagePane.getStyle("system"), false);
                doc.insertString(doc.getLength(), "  " + rawText + "\n", styleSystem);

            } else {
                String timestamp = "";
                String user      = "";
                String body      = rawText;

                if (rawText.startsWith("[") && rawText.contains("]")) {
                    int end = rawText.indexOf(']');
                    timestamp = rawText.substring(0, end + 1) + " ";
                    body      = rawText.substring(end + 1).trim();
                }

                int colonIdx = body.indexOf(':');
                if (colonIdx > 0) {
                    user = body.substring(0, colonIdx + 1) + " ";
                    body = body.substring(colonIdx + 1).trim();
                }

                doc.setParagraphAttributes(doc.getLength(), 1,
                        messagePane.getStyle("msg"), false);
                doc.insertString(doc.getLength(), timestamp, styleTime);
                doc.insertString(doc.getLength(), user,      styleUser);
                doc.insertString(doc.getLength(), body + "\n", styleMessage);
            }
            scrollToBottom();

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> messagePane.setCaretPosition(doc.getLength()));
    }

    // ── Actions ───────────────────────────────────────────────

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || text.equals("Type a message…")) return;
        client.sendMessage(text);
        inputField.setText("");
        inputField.setForeground(TEXT_MAIN);
        inputField.requestFocusInWindow();
    }

    private void leaveChat() {
        client.disconnect();
        dispose();
        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }

    private void setupWindowClose() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(ChatWindow.this,
                        "Leave the chat and close?", "Confirm Exit",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    client.disconnect();
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    // ── ChatClient.MessageListener ────────────────────────────

    /** Normal chat message — append to the message pane. (unchanged) */
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> appendMessage(message));
    }

    /**
     * NEW — server sent ONLINE_COUNT:<n>.
     * Update the "N ●" label in the sidebar header.
     * Must run on the EDT → invokeLater.
     */
    @Override
    public void onOnlineCountReceived(int count) {
        SwingUtilities.invokeLater(() ->
            onlineCountLabel.setText(count + " ●")
        );
    }

    /**
     * NEW — server sent USER_LIST:<comma-separated names>.
     * Rebuild the JList model with the latest snapshot.
     * Appends "(You)" to the current user's entry.
     * Must run on the EDT → invokeLater.
     */
    @Override
    public void onUserListReceived(String[] usernames) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String name : usernames) {
                if (name.equals(this.username)) {
                    userListModel.addElement(name + " (You)");
                } else {
                    userListModel.addElement(name);
                }
            }
        });
    }

    /** Connection dropped. Show error and return to login. (unchanged) */
    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Disconnected from server.\n" + reason,
                    "Connection Lost", JOptionPane.ERROR_MESSAGE);
            dispose();
            new LoginWindow().setVisible(true);
        });
    }

    // ── Inner class: custom JList cell renderer ───────────────

    /**
     * UserCellRenderer
     * ────────────────
     * Draws each row in the user list with:
     *   • A filled green circle (●) on the left
     *   • The username in normal text
     *   • "(You)" highlighted in accent colour if it's the local user
     *
     * Extending DefaultListCellRenderer is the standard Swing way
     * to customise how JList rows look.
     */
    private static class UserCellRenderer extends DefaultListCellRenderer {

        private final String currentUser;

        UserCellRenderer(String currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            // Let the default renderer set up the label first
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            String text = value.toString();

            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));

            if (text.endsWith(" (You)")) {
                // Current user row — accent colour, slightly bold
                label.setForeground(LoginWindow.ACCENT_H);
                label.setFont(new Font("Segoe UI", Font.BOLD, 13));
                label.setText("● " + text);
            } else {
                label.setForeground(LoginWindow.TEXT_MAIN);
                label.setText("● " + text);
            }

            // Keep background consistent with our dark theme
            if (!isSelected) {
                label.setBackground(LoginWindow.BG_PANEL);
            }

            return label;
        }
    }
}
