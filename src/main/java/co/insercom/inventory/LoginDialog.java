package co.insercom.inventory;

import static co.insercom.inventory.Models.Session;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Authentication entry point shared by the demo and the configured MySQL installation. */
public final class LoginDialog extends JDialog {
    private static final Color NAVY = new Color(22, 43, 70);
    private static final Color BLUE = new Color(32, 95, 167);
    private static final Color TEXT = new Color(34, 49, 66);
    private static final Color MUTED = new Color(98, 113, 130);
    private static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    private final InventoryService service;
    private final JTextField username = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);
    private final JLabel feedback = new JLabel(" ");
    private final JButton login = new JButton("Iniciar sesión");
    private Session result;
    private boolean busy;

    private LoginDialog(InventoryService service, String databaseLabel) {
        super((java.awt.Frame) null, "INSERCOM | Iniciar sesión", true);
        this.service = java.util.Objects.requireNonNull(service);
        boolean demo = databaseLabel != null && databaseLabel.toLowerCase(java.util.Locale.ROOT).contains("demostraci");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Color.WHITE);

        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBackground(NAVY);
        brand.setBorder(BorderFactory.createEmptyBorder(37, 31, 35, 31));
        brand.setPreferredSize(new Dimension(258, 0));
        brand.add(label("INSERCOM", new Font("Segoe UI", Font.BOLD, 27), Color.WHITE));
        brand.add(Box.createVerticalStrut(4));
        brand.add(label("INGENIERÍA", new Font("Segoe UI", Font.PLAIN, 12), new Color(170, 193, 221)));
        brand.add(Box.createVerticalStrut(57));
        brand.add(label("<html>Control de recursos<br>para cada proyecto</html>",
                new Font("Segoe UI", Font.BOLD, 23), Color.WHITE));
        brand.add(Box.createVerticalStrut(19));
        brand.add(label("<html><div style='width:143px'>Herramientas, materiales y trazabilidad en un solo lugar.</div></html>",
                NORMAL, new Color(202, 217, 235)));
        brand.add(Box.createVerticalGlue());
        brand.add(label("Inventario · Préstamos", new Font("Segoe UI", Font.PLAIN, 11),
                new Color(170, 193, 221)));
        body.add(brand, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(32, 33, 24, 33));
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(label("Bienvenido", new Font("Segoe UI", Font.BOLD, 27), TEXT));
        title.add(Box.createVerticalStrut(6));
        title.add(label("Ingresa con tu cuenta de trabajo.", NORMAL, MUTED));
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 7, 0);
        addRow(form, constraints, 0, label("Usuario", NORMAL, TEXT));
        username.setFont(NORMAL);
        username.getAccessibleContext().setAccessibleName("Usuario");
        username.setPreferredSize(new Dimension(327, 38));
        username.setToolTipText("Escribe el usuario asignado a tu cuenta");
        addRow(form, constraints, 1, username);
        addRow(form, constraints, 2, label("Contraseña", NORMAL, TEXT));
        password.setFont(NORMAL);
        password.getAccessibleContext().setAccessibleName("Contraseña");
        password.setPreferredSize(new Dimension(327, 38));
        addRow(form, constraints, 3, password);
        char echo = password.getEchoChar();
        JCheckBox reveal = new JCheckBox("Mostrar contraseña");
        reveal.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reveal.setForeground(MUTED);
        reveal.setOpaque(false);
        reveal.addActionListener(event -> password.setEchoChar(reveal.isSelected() ? (char) 0 : echo));
        addRow(form, constraints, 4, reveal);
        feedback.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        feedback.setForeground(new Color(170, 49, 49));
        addRow(form, constraints, 5, feedback);
        login.setFont(new Font("Segoe UI", Font.BOLD, 14));
        login.setForeground(Color.WHITE);
        login.setBackground(BLUE);
        login.setOpaque(true);
        login.setFocusPainted(false);
        login.setBorder(BorderFactory.createEmptyBorder(11, 12, 11, 12));
        login.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        login.addActionListener(event -> authenticate());
        addRow(form, constraints, 6, login);
        content.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        if (demo) {
            JLabel accounts = label("<html><div style='width:240px'><b>Modo demostración · datos ficticios</b><br>"
                    + "Usuarios: admin, bodega, supervisor, trabajador o invitado.<br>Contraseña: <b>Demo2026!</b></div></html>",
                    new Font("Segoe UI", Font.PLAIN, 12), BLUE);
            accounts.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(212, 227, 244)),
                    BorderFactory.createEmptyBorder(11, 12, 11, 12)));
            accounts.setOpaque(true);
            accounts.setBackground(new Color(237, 245, 253));
            footer.add(accounts, BorderLayout.CENTER);
            username.setText("admin");
        }
        JLabel database = label("<html><div style='width:240px'>" + escape(databaseLabel == null ? "Base de datos configurada" : databaseLabel)
                + "</div></html>", new Font("Segoe UI", Font.PLAIN, 11), MUTED);
        footer.add(database, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        body.add(content, BorderLayout.CENTER);
        setContentPane(body);
        getRootPane().setDefaultButton(login);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                if (username.getText().isBlank()) username.requestFocusInWindow(); else password.requestFocusInWindow();
            }
            @Override public void windowClosed(WindowEvent event) { password.setText(""); }
        });
        pack();
        setSize(Math.max(700, getWidth()), Math.max(568, getHeight()));
        setLocationRelativeTo(null);
    }

    /** Returns null when the user closes the sign-in window. */
    public static Session showLogin(InventoryService service, String databaseLabel) {
        if (SwingUtilities.isEventDispatchThread()) {
            LoginDialog dialog = new LoginDialog(service, databaseLabel);
            dialog.setVisible(true);
            return dialog.result;
        }
        AtomicReference<Session> session = new AtomicReference<>();
        try { SwingUtilities.invokeAndWait(() -> session.set(showLogin(service, databaseLabel))); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("El inicio de sesión fue interrumpido.", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No fue posible abrir el inicio de sesión.", exception.getCause());
        }
        return session.get();
    }

    private void authenticate() {
        if (busy) return;
        String user = username.getText().strip();
        char[] secret = password.getPassword();
        if (user.isBlank() || secret.length == 0) {
            feedback.setText("Completa el usuario y la contraseña.");
            Arrays.fill(secret, '\0');
            return;
        }
        busy = true;
        login.setEnabled(false);
        username.setEnabled(false);
        password.setEnabled(false);
        login.setText("Verificando…");
        feedback.setText(" ");
        new SwingWorker<Session, Void>() {
            @Override protected Session doInBackground() {
                try { return service.login(user, secret); }
                finally { Arrays.fill(secret, '\0'); }
            }
            @Override protected void done() {
                try {
                    Session authenticated = get();
                    if (!isDisplayable()) { service.logout(authenticated); return; }
                    result = authenticated;
                    dispose();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    feedback.setText("La verificación se interrumpió. Intenta de nuevo.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String message = cause instanceof IllegalArgumentException || cause instanceof SecurityException
                            || cause instanceof IllegalStateException ? cause.getMessage() : "No se pudo iniciar sesión. Intenta de nuevo.";
                    feedback.setText("<html><div style='width:240px'>" + escape(message == null ? "Revisa tus datos e intenta de nuevo." : message) + "</div></html>");
                    password.setText("");
                } finally {
                    busy = false;
                    login.setText("Iniciar sesión");
                    login.setEnabled(true);
                    username.setEnabled(true);
                    password.setEnabled(true);
                    if (isDisplayable()) { pack(); setSize(Math.max(700, getWidth()), Math.max(568, getHeight())); password.requestFocusInWindow(); }
                }
            }
        }.execute();
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row, JComponent component) {
        constraints.gridy = row;
        panel.add(component, constraints);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
