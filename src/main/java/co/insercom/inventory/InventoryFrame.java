package co.insercom.inventory;

import static co.insercom.inventory.Models.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

/** Desktop interface. All permissions and inventory rules are enforced by the service. */
public final class InventoryFrame extends JFrame {
    private static final Color NAVY = new Color(22, 43, 70);
    private static final Color BLUE = new Color(32, 95, 167);
    private static final Color LIGHT_BLUE = new Color(232, 241, 251);
    private static final Color BACKGROUND = new Color(242, 245, 249);
    private static final Color BORDER = new Color(218, 225, 233);
    private static final Color TEXT = new Color(34, 49, 66);
    private static final Color MUTED = new Color(98, 113, 130);
    private static final Color GREEN = new Color(28, 119, 88);
    private static final Color RED = new Color(170, 49, 49);
    private static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);

    private final InventoryService service;
    private final Session session;
    private final String databaseLabel;
    private final JPanel display = new JPanel(new BorderLayout());
    private final JLabel heading = new JLabel("Resumen operativo");
    private final JLabel subtitle = new JLabel("Inventario y trazabilidad de recursos por proyecto");
    private final JLabel status = new JLabel("Listo");
    private final JLabel[] indicators = new JLabel[5];
    private final Map<View, JButton> navigation = new EnumMap<>(View.class);
    private final List<ActionState> actionStates = new ArrayList<>();
    private JButton overviewButton;
    private View currentView;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField search;
    private JLabel rowCount;
    private boolean loggedOut;
    private boolean endSessionAfterForm;

    public InventoryFrame(InventoryService service, Session session, String databaseLabel) {
        super("INSERCOM | Gestión de inventario");
        this.service = java.util.Objects.requireNonNull(service);
        this.session = java.util.Objects.requireNonNull(session);
        this.databaseLabel = databaseLabel == null ? "Base de datos" : databaseLabel;
        configureFonts();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1060, 700));
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel workspace = new JPanel(new BorderLayout(0, 18));
        workspace.setBackground(BACKGROUND);
        workspace.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JPanel top = new JPanel(new BorderLayout(0, 20));
        top.setOpaque(false);
        top.add(buildHeader(), BorderLayout.NORTH);
        top.add(buildIndicators(), BorderLayout.SOUTH);
        workspace.add(top, BorderLayout.NORTH);
        display.setOpaque(false);
        workspace.add(display, BorderLayout.CENTER);
        workspace.add(buildStatusBar(), BorderLayout.SOUTH);
        root.add(workspace, BorderLayout.CENTER);
        setContentPane(root);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { logout(); }
        });
        refreshAll();
    }

    private static void configureFonts() {
        for (String key : List.of("Label.font", "Button.font", "TextField.font", "PasswordField.font",
                "ComboBox.font", "CheckBox.font", "Spinner.font", "Table.font", "TableHeader.font",
                "TextArea.font", "OptionPane.messageFont", "OptionPane.buttonFont")) {
            UIManager.put(key, NORMAL);
        }
        UIManager.put("OptionPane.yesButtonText", "Sí");
        UIManager.put("OptionPane.noButtonText", "No");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(216, 0));
        sidebar.setBackground(NAVY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(27, 16, 20, 16));
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel name = label("INSERCOM", new Font("Segoe UI", Font.BOLD, 25), Color.WHITE);
        JLabel line = label("INGENIERÍA", new Font("Segoe UI", Font.PLAIN, 12), new Color(170, 193, 221));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(name);
        brand.add(Box.createVerticalStrut(3));
        brand.add(line);
        brand.add(Box.createVerticalStrut(29));
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel links = new JPanel();
        links.setOpaque(false);
        links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        overviewButton = navButton("Resumen", () -> {
            currentView = null;
            refreshAll();
        });
        links.add(overviewButton);
        links.add(Box.createVerticalStrut(9));
        for (View view : View.values()) {
            if ((view == View.USERS || view == View.AUDIT || view == View.DELETED) && session.role() != Role.ADMIN) continue;
            JButton button = navButton(view.toString(), () -> selectView(view));
            navigation.put(view, button);
            links.add(button);
            links.add(Box.createVerticalStrut(5));
        }
        sidebar.add(links, BorderLayout.CENTER);
        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.add(label("CONTROL Y TRAZABILIDAD", SMALL, new Color(170, 193, 221)));
        foot.add(Box.createVerticalStrut(6));
        foot.add(label("Herramientas · Materiales", SMALL, new Color(200, 212, 228)));
        sidebar.add(foot, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton navButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(new Color(217, 227, 241));
        button.setBackground(NAVY);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 13, 10, 10));
        button.setFocusPainted(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 41));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 5));
        titles.setOpaque(false);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 25));
        heading.setForeground(TEXT);
        subtitle.setFont(SMALL);
        subtitle.setForeground(MUTED);
        titles.add(heading);
        titles.add(subtitle);
        header.add(titles, BorderLayout.CENTER);
        JPanel account = new JPanel();
        account.setOpaque(false);
        account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));
        JLabel identity = label(session.name(), new Font("Segoe UI", Font.BOLD, 14), TEXT);
        JLabel role = label(session.role().toString(), SMALL, MUTED);
        identity.setAlignmentX(Component.RIGHT_ALIGNMENT);
        role.setAlignmentX(Component.RIGHT_ALIGNMENT);
        account.add(identity);
        account.add(role);
        JPanel accountActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        accountActions.setOpaque(false);
        JButton password = button("Contraseña", false, this::changePassword);
        JButton exit = button("Salir", false, this::dispose);
        password.setFont(SMALL);
        exit.setFont(SMALL);
        accountActions.add(password);
        accountActions.add(exit);
        account.add(accountActions);
        header.add(account, BorderLayout.EAST);
        return header;
    }

    private JPanel buildIndicators() {
        JPanel cards = new JPanel(new GridLayout(1, 5, 12, 0));
        cards.setOpaque(false);
        String[] names = {"Recursos", "Unidades disponibles", "Solicitudes pendientes", "Préstamos activos", "Alertas"};
        Color[] colors = {BLUE, GREEN, BLUE, BLUE, new Color(180, 113, 23)};
        for (int i = 0; i < names.length; i++) {
            JPanel card = new JPanel(new BorderLayout(0, 6));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(12, 14, 12, 10)));
            indicators[i] = label("—", new Font("Segoe UI", Font.BOLD, 28), colors[i]);
            card.add(indicators[i], BorderLayout.CENTER);
            card.add(label(names[i], SMALL, MUTED), BorderLayout.SOUTH);
            cards.add(card);
        }
        return cards;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        status.setFont(SMALL);
        status.setForeground(MUTED);
        bar.add(status, BorderLayout.CENTER);
        JLabel database = label(databaseLabel, SMALL, MUTED);
        database.setToolTipText(databaseLabel);
        bar.add(database, BorderLayout.EAST);
        return bar;
    }

    /** Selects a service-backed view, including when invoked by the capture launcher. */
    public void selectView(View view) {
        java.util.Objects.requireNonNull(view);
        onEdt(() -> {
            if (!navigation.containsKey(view)) {
                showError("Esta sección requiere una cuenta de administrador.");
                return;
            }
            if (currentView != view) {
                search = null;
                table = null;
            }
            currentView = view;
            refreshAll();
        });
    }

    /** Refreshes both summary counts and the current table using the authenticated session. */
    public void refreshAll() {
        onEdt(() -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                Dashboard data = service.dashboard(session);
                long[] values = {data.resources(), data.available(), data.pending(), data.activeLoans(), data.alerts()};
                for (int i = 0; i < values.length; i++) indicators[i].setText(String.valueOf(values[i]));
                updateNavigation();
                if (currentView == null) buildOverview(); else buildTableView();
                status.setText("Información actualizada · Los datos visibles corresponden a tu perfil");
            } catch (RuntimeException error) {
                status.setText("No se pudo actualizar la información");
                if (error instanceof SecurityException) {
                    finishSession("Tu sesión ya no está activa. Vuelve a abrir la aplicación e inicia sesión con tu cuenta vigente.");
                    return;
                }
                showError(userMessage(error));
            } finally {
                setCursor(Cursor.getDefaultCursor());
                display.revalidate();
                display.repaint();
            }
        });
    }

    /** Produces a real Swing rendering; no illustration or reconstructed screen is used. */
    public BufferedImage snapshot() {
        AtomicReference<BufferedImage> output = new AtomicReference<>();
        onEdt(() -> {
            validate();
            BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(BACKGROUND);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                printAll(graphics);
            } finally { graphics.dispose(); }
            output.set(image);
        });
        return output.get();
    }

    private void updateNavigation() {
        overviewButton.setBackground(currentView == null ? BLUE : NAVY);
        overviewButton.setForeground(Color.WHITE);
        for (Map.Entry<View, JButton> entry : navigation.entrySet()) {
            entry.getValue().setBackground(entry.getKey() == currentView ? BLUE : NAVY);
            entry.getValue().setForeground(entry.getKey() == currentView ? Color.WHITE : new Color(217, 227, 241));
        }
    }

    private void buildOverview() {
        heading.setText("Resumen operativo");
        subtitle.setText("Disponibilidad, solicitudes y movimientos en un solo lugar");
        display.removeAll();
        table = null;
        actionStates.clear();
        JPanel overview = new JPanel(new BorderLayout(0, 18));
        overview.setOpaque(false);
        JPanel welcome = new JPanel(new BorderLayout(12, 12));
        welcome.setBackground(LIGHT_BLUE);
        welcome.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        welcome.add(label("Cada recurso, en el lugar correcto", new Font("Segoe UI", Font.BOLD, 22), NAVY),
                BorderLayout.NORTH);
        welcome.add(label("Consulta existencias y sigue el recorrido de las herramientas y materiales.", NORMAL, TEXT),
                BorderLayout.CENTER);
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quick.setOpaque(false);
        quick.add(button("Ver inventario", true, () -> selectView(View.RESOURCES)));
        if (canRequest()) quick.add(button("Nueva solicitud", false, this::request));
        if (isStore()) quick.add(button("Registrar entrada", false, () -> guard(() -> receiveStock(null))));
        quick.add(button("Consultar alertas", false, () -> selectView(View.ALERTS)));
        welcome.add(quick, BorderLayout.SOUTH);
        overview.add(welcome, BorderLayout.NORTH);

        JPanel recent = new JPanel(new BorderLayout(0, 12));
        recent.setBackground(Color.WHITE);
        recent.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(16, 16, 12, 16)));
        JPanel recentTitle = new JPanel(new BorderLayout());
        recentTitle.setOpaque(false);
        recentTitle.add(label("Movimientos recientes", new Font("Segoe UI", Font.BOLD, 17), TEXT), BorderLayout.WEST);
        recentTitle.add(button("Ver todos", false, () -> selectView(View.MOVEMENTS)), BorderLayout.EAST);
        recent.add(recentTitle, BorderLayout.NORTH);
        TableData all = service.table(session, View.MOVEMENTS);
        List<List<Object>> rows = all.rows().subList(0, Math.min(8, all.rows().size()));
        JTable recentTable = createTable(new TableData(all.columns(), rows));
        recent.add(tableScroll(recentTable), BorderLayout.CENTER);
        JLabel hint = label(rows.isEmpty() ? "Aún no hay movimientos disponibles para tu perfil."
                : "Los movimientos conservan el registro de entradas, despachos y devoluciones.", SMALL, MUTED);
        recent.add(hint, BorderLayout.SOUTH);
        overview.add(recent, BorderLayout.CENTER);
        display.add(overview, BorderLayout.CENTER);
    }

    private void buildTableView() {
        String previousSearch = search != null && table != null ? search.getText() : "";
        heading.setText(currentView.toString());
        subtitle.setText(description(currentView));
        display.removeAll();
        actionStates.clear();
        TableData data = service.table(session, currentView);
        table = createTable(data);
        sorter = new TableRowSorter<>((DefaultTableModel) table.getModel());
        table.setRowSorter(sorter);
        table.getSelectionModel().addListSelectionListener(event -> updateActions());

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(15, 16, 12, 16)));
        JPanel controls = new JPanel(new BorderLayout(10, 10));
        controls.setOpaque(false);
        JPanel find = new JPanel(new BorderLayout(8, 0));
        find.setOpaque(false);
        find.add(label("Buscar", NORMAL, MUTED), BorderLayout.WEST);
        search = new JTextField(previousSearch, 19);
        search.setToolTipText("Busca en todas las columnas de esta tabla");
        search.setPreferredSize(new Dimension(250, 35));
        find.add(search, BorderLayout.CENTER);
        controls.add(find, BorderLayout.WEST);
        JPanel utilities = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        utilities.setOpaque(false);
        utilities.add(button("Actualizar", false, this::refreshAll));
        utilities.add(button("Exportar CSV", false, this::exportCsv));
        controls.add(utilities, BorderLayout.EAST);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actionBar.setOpaque(false);
        addViewActions(actionBar);
        if (actionBar.getComponentCount() > 0) controls.add(actionBar, BorderLayout.SOUTH);
        card.add(controls, BorderLayout.NORTH);
        card.add(tableScroll(table), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        rowCount = label("", SMALL, MUTED);
        footer.add(rowCount, BorderLayout.WEST);
        footer.add(label("Haz clic en un encabezado para ordenar", SMALL, MUTED), BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);
        display.add(card, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { filter(); }
            public void removeUpdate(DocumentEvent event) { filter(); }
            public void changedUpdate(DocumentEvent event) { filter(); }
        });
        filter();
        updateActions();
    }

    private String description(View view) {
        return switch (view) {
            case RESOURCES -> "Catálogo y saldos de herramientas y materiales";
            case PROJECTS -> "Proyectos y frentes de trabajo disponibles para tu perfil";
            case REQUESTS -> "Solicitudes, autorizaciones y despachos por proyecto";
            case LOANS -> "Préstamos y cantidades pendientes de devolución";
            case MOVEMENTS -> "Historial de entradas, despachos y devoluciones";
            case ALERTS -> "Bajo inventario, préstamos vencidos y novedades pendientes";
            case INCIDENTS -> "Daños, pérdidas y seguimiento de novedades";
            case USERS -> "Cuentas, roles y asignación a proyectos";
            case AUDIT -> "Registro de operaciones y responsables";
            case DELETED -> "Papelera: consulta y restauración durante 15 días desde la eliminación";
        };
    }

    private void addViewActions(JPanel bar) {
        switch (currentView) {
            case RESOURCES -> {
                if (isStore()) {
                    action(bar, "Nuevo recurso", true, () -> resource(false), () -> true);
                    action(bar, "Editar recurso", false, () -> resource(true), this::hasSelection);
                    action(bar, "Registrar entrada", false, () -> receiveStock(selectedId()), this::hasSelection);
                    action(bar, "Eliminar recurso", false, this::deleteSelected, this::hasSelection);
                }
            }
            case PROJECTS -> {
                if (session.role() == Role.ADMIN) {
                    action(bar, "Nuevo proyecto", true, () -> project(false), () -> true);
                    action(bar, "Editar proyecto", false, () -> project(true), this::hasSelection);
                    action(bar, "Eliminar proyecto", false, this::deleteSelected, this::hasSelection);
                }
            }
            case REQUESTS -> {
                if (canRequest()) action(bar, "Nueva solicitud", true, this::request, () -> true);
                if (session.role() == Role.ADMIN || session.role() == Role.SUPERVISOR) {
                    action(bar, "Aprobar", false, () -> decide(true), () -> selectedState("PENDIENTE"));
                    action(bar, "Rechazar", false, () -> decide(false), () -> selectedState("PENDIENTE"));
                }
                if (isStore()) action(bar, "Despachar", false, this::dispatch, () -> selectedState("APROBADA"));
            }
            case LOANS -> {
                if (isStore()) {
                    action(bar, "Registrar devolución", true, this::returnLoan, () -> selectedState("ACTIVO"));
                }
                if (isStore() || session.role() == Role.SUPERVISOR || session.role() == Role.TRABAJADOR)
                    action(bar, "Reportar novedad", !isStore(), this::reportIncident, this::hasSelection);
            }
            case INCIDENTS -> {
                if (isStore()) action(bar, "Resolver novedad", true, this::resolveIncident,
                        () -> selectedState("ABIERTA"));
            }
            case USERS -> {
                if (session.role() == Role.ADMIN) {
                    action(bar, "Nuevo usuario", true, () -> user(false), () -> true);
                    action(bar, "Editar usuario", false, () -> user(true), this::hasSelection);
                    action(bar, "Eliminar usuario", false, this::deleteSelected, this::hasSelection);
                }
            }
            case DELETED -> {
                if (session.role() == Role.ADMIN)
                    action(bar, "Restaurar seleccionado", true, this::restoreSelected, this::hasSelection);
            }
            default -> { }
        }
    }

    private void deleteSelected() {
        if (!hasSelection()) return;
        long id = selectedId();
        String name = selected(1) + " · " + selected(2);
        JTextArea reason = note();
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><b>Eliminar: " + escape(name) + "</b><br>Se retirará del catálogo. Un administrador podrá restaurarlo durante 15 días.<br>El historial de operaciones se conserva. Escriba el motivo:</html>"), BorderLayout.NORTH);
        panel.add(new JScrollPane(reason), BorderLayout.CENTER);
        while (JOptionPane.showConfirmDialog(this, panel, "Confirmar eliminación", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            if (reason.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Escriba el motivo de eliminación.", "Motivo obligatorio", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            switch (currentView) {
                case RESOURCES -> service.deleteResource(session, id, reason.getText());
                case PROJECTS -> service.deleteProject(session, id, reason.getText());
                case USERS -> service.deleteUser(session, id, reason.getText());
                default -> throw new IllegalStateException("Este registro no admite eliminación.");
            }
            refreshAll();
            status.setText("Registro enviado a la papelera por 15 días.");
            return;
        }
    }

    private void restoreSelected() {
        if (!hasSelection()) return;
        long id = selectedId();
        if (JOptionPane.showConfirmDialog(this, "¿Restaurar " + selected(2) + " al catálogo con su estado anterior?", "Restaurar registro", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        service.restoreDeleted(session, id);
        refreshAll();
        status.setText("Registro restaurado. Puede consultarlo en su catálogo.");
    }

    private void action(JPanel bar, String text, boolean primary, Runnable handler, BooleanSupplier enabled) {
        JButton button = button(text, primary, () -> guard(handler));
        actionStates.add(new ActionState(button, enabled));
        bar.add(button);
    }

    private void updateActions() {
        for (ActionState action : actionStates) action.button().setEnabled(action.enabled().getAsBoolean());
    }

    private void filter() {
        String text = search.getText().strip();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?iu)" + Pattern.quote(text)));
        rowCount.setText(table.getRowCount() + " de " + table.getModel().getRowCount() + " registros"
                + (table.getModel().getRowCount() == 0 ? " · No hay datos disponibles" : ""));
        updateActions();
    }

    private JTable createTable(TableData data) {
        DefaultTableModel model = new DefaultTableModel(data.columns().toArray(), 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) {
                for (int row = 0; row < getRowCount(); row++) {
                    Object value = getValueAt(row, column);
                    if (value != null) return value.getClass();
                }
                return Object.class;
            }
        };
        for (List<Object> row : data.rows()) model.addRow(row.toArray());
        JTable result = new JTable(model);
        result.setRowHeight(35);
        result.setFont(NORMAL);
        result.setForeground(TEXT);
        result.setSelectionBackground(new Color(219, 234, 250));
        result.setSelectionForeground(NAVY);
        result.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        result.setShowVerticalLines(false);
        result.setShowHorizontalLines(true);
        result.setGridColor(new Color(236, 240, 245));
        result.setIntercellSpacing(new Dimension(12, 1));
        result.setFillsViewportHeight(true);
        result.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        result.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        result.getTableHeader().setBackground(new Color(237, 242, 248));
        result.getTableHeader().setForeground(NAVY);
        result.getTableHeader().setPreferredSize(new Dimension(100, 38));
        result.getTableHeader().setReorderingAllowed(false);
        CellRenderer renderer = new CellRenderer();
        result.setDefaultRenderer(Object.class, renderer);
        result.setDefaultRenderer(String.class, renderer);
        result.setDefaultRenderer(Number.class, renderer);
        result.setDefaultRenderer(Boolean.class, renderer);
        result.setDefaultRenderer(LocalDate.class, renderer);
        for (int column = 0; column < model.getColumnCount(); column++) {
            String name = model.getColumnName(column);
            int width = switch (name) {
                case "ID" -> 49;
                case "Mínimo", "Dañado", "Activo", "Devuelto" -> 72;
                case "Cantidad", "Pendiente", "Disponible", "Unidad", "Proyecto ID", "Préstamo ID" -> 98;
                case "Código", "Tipo", "Estado", "Condición" -> 125;
                case "Fecha", "Fecha devolución" -> 155;
                case "Nota", "Resolución", "Detalle", "Acción" -> 290;
                case "Nombre", "Recurso", "Proyecto", "Ubicación" -> 215;
                case "Solicitante", "Prestatario", "Responsable", "Usuario", "Rol" -> 175;
                default -> 160;
            };
            TableColumn tableColumn = result.getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(width);
            tableColumn.setMinWidth(Math.min(width, 65));
        }
        // The complete resource catalog fits the default window without hiding stock columns.
        int[] widths = null;
        if (data.columns().equals(List.of("ID", "Código", "Nombre", "Tipo", "Unidad", "Ubicación", "Mínimo", "Disponible", "Dañado", "Activo"))) {
            widths = new int[]{49, 88, 172, 116, 73, 124, 68, 85, 72, 68};
        } else if (data.columns().equals(List.of("ID", "Recurso", "Proyecto", "Solicitante", "Cantidad", "Devolución", "Estado", "Nota", "Creada"))) {
            widths = new int[]{49, 185, 155, 145, 80, 112, 120, 290, 160};
        } else if (data.columns().equals(List.of("ID", "Recurso", "Proyecto", "Responsable", "Cantidad", "Devuelto", "Pendiente", "Vencimiento", "Estado"))) {
            widths = new int[]{49, 170, 145, 140, 75, 80, 85, 110, 105};
        }
        if (widths != null) {
            for (int column = 0; column < widths.length; column++)
                result.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        return result;
    }

    private JScrollPane tableScroll(JTable target) {
        JScrollPane scroll = new JScrollPane(target);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        return scroll;
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable target, Object value, boolean selected,
                boolean focused, int row, int column) {
            Object displayValue = value instanceof Boolean bool ? (bool ? "Sí" : "No") : value;
            super.getTableCellRendererComponent(target, displayValue, selected, focused, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 7));
            setFont(NORMAL);
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!selected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 253));
                setForeground(TEXT);
                String cell = value == null ? "" : value.toString();
                if (List.of("APROBADA", "BUENO", "CERRADO", "RESUELTA").contains(cell)) setForeground(GREEN);
                if (List.of("RECHAZADA", "DANADO", "PERDIDO", "VENCIDO").contains(cell)) setForeground(RED);
                if (List.of("PENDIENTE", "ABIERTA").contains(cell)) setForeground(new Color(155, 100, 12));
            }
            setToolTipText(value == null ? null : "<html>" + escape(value.toString()) + "</html>");
            return this;
        }
    }

    private void resource(boolean edit) {
        Long id = edit ? selectedId() : null;
        JTextField code = field(edit ? selected(1) : "", 22);
        JTextField name = field(edit ? selected(2) : "", 28);
        JComboBox<String> type = options(new String[]{"HERRAMIENTA", "MATERIAL"},
                new String[]{"Herramienta · retornable", "Material · consumo"});
        if (edit) type.setSelectedItem(selected(3));
        JTextField unit = field(edit ? selected(4) : "unidad", 22);
        JTextField location = field(edit ? selected(5) : "", 22);
        JSpinner minimum = quantity(edit ? intValue(selectedObject(6)) : 0, 0, 1_000_000);
        JCheckBox active = new JCheckBox("Recurso activo", !edit || boolValue(selectedObject(9)));
        Form form = new Form();
        form.add("Código *", code);
        form.add("Nombre *", name);
        form.add("Tipo *", type);
        form.add("Unidad *", unit);
        form.add("Ubicación *", location);
        form.add("Inventario mínimo *", minimum);
        form.add("Estado", active);
        showForm(edit ? "Editar recurso" : "Nuevo recurso",
                "Las existencias cambian mediante entradas, despachos y devoluciones. Desactiva los recursos que ya no se utilicen.",
                form, "Guardar recurso", () -> service.saveResource(session, id, new ResourceInput(
                        required(code, "el código"), required(name, "el nombre"), (String) type.getSelectedItem(),
                        required(unit, "la unidad"), required(location, "la ubicación"), number(minimum), active.isSelected())));
    }

    private void receiveStock(Long preferredId) {
        JComboBox<Choice> resource = choices(service.resources(session), preferredId, false);
        JSpinner amount = quantity(1, 1, 1_000_000);
        JTextArea note = note();
        Form form = new Form();
        form.add("Recurso *", resource);
        form.add("Cantidad recibida *", amount);
        form.add("Observación", new JScrollPane(note));
        showForm("Registrar entrada", "Registra las unidades recibidas en bodega. La entrada aumentará la cantidad disponible.",
                form, "Registrar entrada", () -> service.receiveStock(session, choiceId(resource, "un recurso"),
                        number(amount), note.getText().strip()));
    }

    private void project(boolean edit) {
        Long id = edit ? selectedId() : null;
        JTextField code = field(edit ? selected(1) : "", 22);
        JTextField name = field(edit ? selected(2) : "", 28);
        JCheckBox active = new JCheckBox("Proyecto activo", !edit || boolValue(selectedObject(3)));
        Form form = new Form();
        form.add("Código *", code);
        form.add("Nombre *", name);
        form.add("Estado", active);
        showForm(edit ? "Editar proyecto" : "Nuevo proyecto", "Los proyectos permiten vincular solicitudes, recursos y responsables.",
                form, "Guardar proyecto", () -> service.saveProject(session, id, required(code, "el código"),
                        required(name, "el nombre"), active.isSelected()));
    }

    private void user(boolean edit) {
        Long id = edit ? selectedId() : null;
        JTextField username = field(edit ? selected(1) : "", 22);
        JTextField name = field(edit ? selected(2) : "", 28);
        JComboBox<Role> role = new JComboBox<>(Role.values());
        if (edit) role.setSelectedItem(Role.valueOf(selected(3)));
        Long assigned = edit && selectedObject(4) != null ? longValue(selectedObject(4)) : null;
        JComboBox<Choice> project = choices(service.projects(session), assigned, true);
        JCheckBox active = new JCheckBox("Cuenta activa", !edit || boolValue(selectedObject(5)));
        JPasswordField password = new JPasswordField(22);
        JPasswordField confirmation = new JPasswordField(22);
        Runnable projectState = () -> project.setEnabled(needsProject((Role) role.getSelectedItem()));
        role.addActionListener(event -> projectState.run());
        projectState.run();
        Form form = new Form();
        form.add("Usuario *", username);
        form.add("Nombre completo *", name);
        form.add("Rol *", role);
        form.add("Proyecto asignado", project);
        form.add(edit ? "Nueva contraseña" : "Contraseña *", password);
        form.add(edit ? "Confirmar contraseña" : "Confirmar contraseña *", confirmation);
        form.add("Estado", active);
        try {
            showForm(edit ? "Editar usuario" : "Nuevo usuario",
                    "Supervisor, trabajador e invitado requieren proyecto. Contraseña de al menos 8 caracteres."
                            + (edit ? " Déjala vacía para conservar la actual." : "")
                            + (edit && id.longValue() == session.id()
                                    ? " Al guardar tu propia cuenta se cerrará la sesión; vuelve a abrir la aplicación para entrar con los datos actualizados." : ""),
                    form, "Guardar usuario", () -> {
                        char[] first = password.getPassword();
                        char[] second = confirmation.getPassword();
                        try {
                            if ((!edit || first.length > 0) && first.length < 8)
                                throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
                            if (!Arrays.equals(first, second))
                                throw new IllegalArgumentException("Las contraseñas no coinciden.");
                            Role selectedRole = (Role) role.getSelectedItem();
                            Long projectId = needsProject(selectedRole) ? choiceId(project, "un proyecto") : null;
                            service.saveUser(session, id, new UserInput(required(username, "el usuario"),
                                    required(name, "el nombre"), selectedRole, projectId, active.isSelected()),
                                    first.length == 0 ? null : first);
                            if (id != null && id.longValue() == session.id()) endSessionAfterForm = true;
                        } finally {
                            Arrays.fill(first, '\0');
                            Arrays.fill(second, '\0');
                        }
                    });
        } finally {
            password.setText("");
            confirmation.setText("");
        }
    }

    private void request() {
        guard(() -> {
            JComboBox<Choice> resource = choices(service.resources(session), null, false);
            JComboBox<Choice> project = choices(service.projects(session), session.projectId(), false);
            if (session.role() != Role.ADMIN) project.setEnabled(false);
            JSpinner amount = quantity(1, 1, 1_000_000);
            JTextField due = field(LocalDate.now().plusDays(7).toString(), 18);
            due.setToolTipText("Formato AAAA-MM-DD. Obligatoria para herramientas, opcional para materiales.");
            JTextArea note = note();
            Form form = new Form();
            form.add("Recurso *", resource);
            form.add("Proyecto *", project);
            form.add("Cantidad *", amount);
            form.add("Fecha de devolución", due);
            form.add("Motivo u observación", new JScrollPane(note));
            form.addHelp("Fecha en formato AAAA-MM-DD. Para herramientas usa hoy o una fecha posterior. Los materiales se registran como consumo al despachar.");
            showForm("Nueva solicitud", "La solicitud deberá aprobarse antes de su despacho. Nadie puede aprobar su propia solicitud.",
                    form, "Crear solicitud", () -> {
                        LocalDate date = null;
                        if (!due.getText().isBlank()) {
                            try { date = LocalDate.parse(due.getText().strip()); }
                            catch (DateTimeParseException error) {
                                throw new IllegalArgumentException("La fecha debe tener formato AAAA-MM-DD; por ejemplo, 2026-10-30.");
                            }
                        }
                        service.request(session, new RequestInput(choiceId(resource, "un recurso"),
                                choiceId(project, "un proyecto"), number(amount), date, note.getText().strip()));
                    });
        });
    }

    private void decide(boolean approve) {
        long id = selectedId();
        JTextArea note = note();
        Form form = new Form();
        form.add("Solicitud", valueLabel("#" + id + " · " + selected(1)));
        form.add(approve ? "Observación" : "Motivo del rechazo *", new JScrollPane(note));
        showForm(approve ? "Aprobar solicitud" : "Rechazar solicitud",
                approve ? "La aprobación autoriza el despacho; todavía no modifica las existencias."
                        : "Registra el motivo para que el solicitante pueda conocer la decisión.",
                form, approve ? "Aprobar solicitud" : "Rechazar solicitud", () -> {
                    String text = note.getText().strip();
                    if (!approve && text.isEmpty()) throw new IllegalArgumentException("Escribe el motivo del rechazo.");
                    service.decide(session, id, approve, text);
                });
    }

    private void dispatch() {
        long id = selectedId();
        String message = "Se registrará el despacho de la solicitud #" + id + ".\n"
                + "Recurso: " + selected(1) + "\nProyecto: " + selected(2) + "\nCantidad: " + selected(4)
                + "\n\nLas herramientas generarán un préstamo y los materiales un consumo.";
        if (JOptionPane.showConfirmDialog(this, message, "Confirmar despacho", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
            service.dispatch(session, id);
            refreshAll();
            status.setText("Despacho registrado correctamente");
        }
    }

    private void returnLoan() {
        long id = selectedId();
        int pending = intValue(selectedObject(6));
        if (pending < 1) throw new IllegalArgumentException("Este préstamo no tiene unidades pendientes.");
        JSpinner amount = quantity(pending, 1, pending);
        JComboBox<String> condition = options(new String[]{"BUENO", "DANADO", "PERDIDO"},
                new String[]{"Buen estado", "Dañado", "Perdido"});
        JTextArea note = note();
        Form form = new Form();
        form.add("Préstamo", valueLabel("#" + id + " · " + selected(1)));
        form.add("Pendiente", valueLabel(pending + " unidades"));
        form.add("Cantidad a registrar *", amount);
        form.add("Condición *", condition);
        form.add("Observación", new JScrollPane(note));
        showForm("Registrar devolución", "Puedes registrar una devolución parcial. Solo las unidades en buen estado vuelven al disponible; daños y pérdidas generan una novedad.",
                form, "Registrar devolución", () -> {
                    String state = (String) condition.getSelectedItem();
                    if (!"BUENO".equals(state) && note.getText().isBlank())
                        throw new IllegalArgumentException("Describe el daño o la pérdida en la observación.");
                    service.returnLoan(session, id, number(amount), state, note.getText().strip());
                });
    }

    private void reportIncident() {
        long id = selectedId();
        JComboBox<String> type = options(new String[]{"DANADO", "PERDIDO", "RETRASO", "OTRO"},
                new String[]{"Daño", "Pérdida", "Retraso", "Otra novedad"});
        JTextArea note = note();
        Form form = new Form();
        form.add("Préstamo", valueLabel("#" + id + " · " + selected(1)));
        form.add("Tipo de novedad *", type);
        form.add("Descripción *", new JScrollPane(note));
        showForm("Reportar novedad", "Describe lo ocurrido con el préstamo. El reporte no modifica cantidades; bodega registra la devolución, el daño o la pérdida de unidades.",
                form, "Reportar novedad", () -> {
                    if (note.getText().isBlank()) throw new IllegalArgumentException("Describe la novedad.");
                    service.reportIncident(session, id, (String) type.getSelectedItem(), note.getText().strip());
                });
    }

    private void resolveIncident() {
        long id = selectedId();
        JTextArea note = note();
        Form form = new Form();
        form.add("Novedad", valueLabel("#" + id));
        form.add("Acción o solución *", new JScrollPane(note));
        showForm("Resolver novedad", "Describe la gestión realizada. Resolver la novedad conserva su historial y no repone unidades en inventario.",
                form, "Resolver novedad", () -> {
                    if (note.getText().isBlank()) throw new IllegalArgumentException("Describe la acción o solución aplicada.");
                    service.resolveIncident(session, id, note.getText().strip());
                });
    }

    private void exportCsv() {
        if (currentView == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar " + currentView.toString().toLowerCase(Locale.ROOT));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
        chooser.setSelectedFile(new java.io.File("insercom-" + currentView.name().toLowerCase(Locale.ROOT)
                + "-" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
            target = target.resolveSibling(target.getFileName() + ".csv");
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                "Ya existe un archivo con ese nombre. ¿Deseas reemplazarlo?", "Reemplazar archivo",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Path finalTarget = target;
        guard(() -> {
            service.exportCsv(session, currentView, finalTarget);
            status.setText("Exportación guardada: " + finalTarget.getFileName());
            JOptionPane.showMessageDialog(this,
                    "El archivo CSV se guardó correctamente.\nIncluye los registros autorizados para tu perfil de esta sección."
                            + "\nLa búsqueda de pantalla no limita esta exportación.",
                    "Exportación completada", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void changePassword() {
        JPasswordField oldPassword = new JPasswordField(22);
        JPasswordField newPassword = new JPasswordField(22);
        JPasswordField confirmation = new JPasswordField(22);
        Form form = new Form();
        form.add("Contraseña actual *", oldPassword);
        form.add("Nueva contraseña *", newPassword);
        form.add("Confirmar contraseña *", confirmation);
        try {
            showForm("Cambiar contraseña", "Usa al menos 8 caracteres y una contraseña que no compartas con otras personas.",
                    form, "Cambiar contraseña", () -> {
                        char[] current = oldPassword.getPassword();
                        char[] next = newPassword.getPassword();
                        char[] confirm = confirmation.getPassword();
                        try {
                            if (current.length == 0) throw new IllegalArgumentException("Escribe tu contraseña actual.");
                            if (next.length < 8) throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
                            if (!Arrays.equals(next, confirm)) throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
                            service.changePassword(session, current, next);
                        } finally {
                            Arrays.fill(current, '\0');
                            Arrays.fill(next, '\0');
                            Arrays.fill(confirm, '\0');
                        }
                    });
        } finally {
            oldPassword.setText("");
            newPassword.setText("");
            confirmation.setText("");
        }
    }

    private void showForm(String title, String introduction, Form form, String action, Runnable submit) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel body = new JPanel(new BorderLayout(0, 17));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        JLabel description = new JLabel("<html><div style='width:450px'>" + escape(introduction) + "</div></html>");
        description.setFont(NORMAL);
        description.setForeground(MUTED);
        body.add(description, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(form.panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        body.add(scroll, BorderLayout.CENTER);
        JLabel error = new JLabel(" ");
        error.setForeground(RED);
        error.setFont(SMALL);
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.setOpaque(false);
        footer.add(error, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = button("Cancelar", false, dialog::dispose);
        JButton save = button(action, true, () -> {
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                for (JSpinner spinner : form.spinners) spinner.commitEdit();
                submit.run();
                dialog.dispose();
                if (endSessionAfterForm) {
                    endSessionAfterForm = false;
                    finishSession("Los cambios de tu cuenta se guardaron correctamente. Vuelve a abrir la aplicación e inicia sesión con los datos actualizados.");
                    return;
                }
                refreshAll();
                status.setText(title + ": operación registrada correctamente");
            } catch (java.text.ParseException exception) {
                error.setText("<html>Escribe una cantidad entera válida.</html>");
                dialog.pack();
            } catch (RuntimeException exception) {
                error.setText("<html><div style='width:440px'>" + escape(userMessage(exception)) + "</div></html>");
                dialog.pack();
            } finally { dialog.setCursor(Cursor.getDefaultCursor()); }
        });
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.getRootPane().setDefaultButton(save);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setSize(Math.max(600, dialog.getWidth()), Math.min(740, dialog.getHeight()));
        dialog.setMinimumSize(new Dimension(570, Math.min(430, dialog.getHeight())));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final List<JSpinner> spinners = new ArrayList<>();
        private int row;
        private Form() { panel.setBackground(Color.WHITE); }
        private void add(String title, JComponent component) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.NORTHWEST;
            labelConstraints.insets = new Insets(8, 0, 8, 14);
            JLabel label = new JLabel(title);
            label.setFont(NORMAL);
            label.setForeground(TEXT);
            label.setLabelFor(component);
            panel.add(label, labelConstraints);
            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row++;
            fieldConstraints.weightx = 1;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
            fieldConstraints.insets = new Insets(3, 0, 8, 0);
            if (!(component instanceof JScrollPane)) component.setPreferredSize(new Dimension(300, 34));
            panel.add(component, fieldConstraints);
            if (component instanceof JSpinner spinner) spinners.add(spinner);
        }
        private void addHelp(String text) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = row++;
            constraints.gridwidth = 2;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(8, 0, 6, 0);
            JLabel hint = label("<html><div style='width:430px'>" + escape(text) + "</div></html>", SMALL, MUTED);
            panel.add(hint, constraints);
        }
    }

    private JComboBox<Choice> choices(List<Choice> choices, Long preferredId, boolean emptyOption) {
        JComboBox<Choice> combo = new JComboBox<>();
        if (emptyOption) combo.addItem(new Choice(0, "Sin proyecto"));
        for (Choice choice : choices) combo.addItem(choice);
        if (preferredId != null) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).id() == preferredId) { combo.setSelectedIndex(i); break; }
            }
        }
        combo.setMaximumRowCount(12);
        return combo;
    }

    private static JComboBox<String> options(String[] values, String[] labels) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                for (int i = 0; i < values.length; i++) if (values[i].equals(value)) setText(labels[i]);
                return this;
            }
        });
        return combo;
    }

    private static JTextField field(String value, int columns) { return new JTextField(value, columns); }
    private static JTextArea note() {
        JTextArea area = new JTextArea(4, 27);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(NORMAL);
        area.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        return area;
    }
    private static JLabel valueLabel(String text) {
        JLabel value = label(text, NORMAL, TEXT);
        value.setToolTipText(text);
        return value;
    }
    private static JSpinner quantity(int initial, int minimum, int maximum) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, minimum, maximum, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0"));
        return spinner;
    }
    private static int number(JSpinner spinner) { return ((Number) spinner.getValue()).intValue(); }
    private static long choiceId(JComboBox<Choice> combo, String label) {
        Choice choice = (Choice) combo.getSelectedItem();
        if (choice == null || choice.id() == 0) throw new IllegalArgumentException("Selecciona " + label + ".");
        return choice.id();
    }
    private static String required(JTextField field, String name) {
        String text = field.getText().strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Completa " + name + ".");
        return text;
    }
    private boolean hasSelection() { return table != null && table.getSelectedRow() >= 0; }
    private long selectedId() {
        if (!hasSelection()) throw new IllegalArgumentException("Selecciona un registro de la tabla.");
        return longValue(selectedObject(0));
    }
    private Object selectedObject(int column) {
        if (!hasSelection()) throw new IllegalArgumentException("Selecciona un registro de la tabla.");
        return table.getModel().getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), column);
    }
    private String selected(int column) {
        Object value = selectedObject(column);
        return value == null ? "" : value.toString();
    }
    private boolean selectedState(String expected) {
        if (!hasSelection()) return false;
        for (int column = 0; column < table.getModel().getColumnCount(); column++) {
            if ("Estado".equalsIgnoreCase(table.getModel().getColumnName(column)))
                return expected.equalsIgnoreCase(selected(column));
        }
        return false;
    }
    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
    private static boolean boolValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value)) || "Sí".equalsIgnoreCase(String.valueOf(value));
    }
    private static boolean needsProject(Role role) {
        return role == Role.SUPERVISOR || role == Role.TRABAJADOR || role == Role.INVITADO;
    }
    private boolean isStore() { return session.role() == Role.ADMIN || session.role() == Role.BODEGA; }
    private boolean canRequest() {
        return session.role() == Role.ADMIN || session.role() == Role.SUPERVISOR || session.role() == Role.TRABAJADOR;
    }
    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }
    private static JButton button(String text, boolean primary, Runnable handler) {
        JButton button = new JButton(text);
        button.setFont(NORMAL);
        button.setForeground(primary ? Color.WHITE : BLUE);
        button.setBackground(primary ? BLUE : Color.WHITE);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? BLUE : BORDER),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> handler.run());
        return button;
    }
    private void guard(Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException error) { showError(userMessage(error)); }
    }
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, "<html><div style='width:420px'>" + escape(message) + "</div></html>",
                "No se pudo completar la operación", JOptionPane.WARNING_MESSAGE);
    }
    private static String userMessage(RuntimeException error) {
        if (error instanceof IllegalArgumentException || error instanceof SecurityException
                || error instanceof IllegalStateException)
            return error.getMessage() == null ? "Revisa los datos y vuelve a intentarlo." : error.getMessage();
        return "No se pudo completar la operación. Actualiza la información y vuelve a intentarlo.";
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
    private void logout() {
        if (!loggedOut) {
            loggedOut = true;
            try { service.logout(session); } catch (RuntimeException ignored) { }
        }
    }
    private void finishSession(String message) {
        JOptionPane.showMessageDialog(this, "<html><div style='width:420px'>" + escape(message) + "</div></html>",
                "Sesión finalizada", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
    private static void onEdt(Runnable operation) {
        if (SwingUtilities.isEventDispatchThread()) { operation.run(); return; }
        try { SwingUtilities.invokeAndWait(operation); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La actualización de la ventana fue interrumpida.", error);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudo actualizar la ventana.", error.getCause());
        }
    }
    private record ActionState(JButton button, BooleanSupplier enabled) { }
}
