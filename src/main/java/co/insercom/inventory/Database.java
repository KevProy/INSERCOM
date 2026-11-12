package co.insercom.inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JDBC connection factory. There is no cross-database fallback. */
public final class Database implements AutoCloseable {
    private final String url;
    private final String user;
    private final String password;
    private volatile boolean closed;

    public Database(String url, String user, String password) {
        if (url == null || (!url.startsWith("jdbc:h2:") && !url.startsWith("jdbc:mysql:")))
            throw new IllegalArgumentException("Se requiere una URL JDBC de H2 o MySQL.");
        this.url = url;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
    }

    public Connection connect() throws SQLException {
        if (closed) throw new SQLException("La conexión de la aplicación está cerrada.");
        Connection c = DriverManager.getConnection(url, user, password);
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return c;
    }

    public String description() { return url.startsWith("jdbc:mysql:") ? "MySQL" : "H2 local"; }

    public synchronized void initialize(boolean demo) {
        try (Connection c = connect()) {
            try (InputStream source = Database.class.getResourceAsStream("/schema.sql")) {
                if (source == null) throw new IllegalStateException("Falta el recurso schema.sql en la aplicación.");
                String schema = new String(source.readAllBytes(), StandardCharsets.UTF_8);
                for (String sql : schema.split(";")) {
                    if (!sql.isBlank()) try (Statement s = c.createStatement()) { s.execute(sql); }
                }
            }
            migrateDeletionColumns(c);
            if (url.startsWith("jdbc:h2:")) migrateH2Checks(c);
            // A single durable row serializes both first-admin provisioning and demo seed.
            try (PreparedStatement p = c.prepareStatement("INSERT INTO app_meta(id,schema_version) VALUES(1,2)")) {
                p.executeUpdate();
            } catch (SQLException e) {
                if (!"23505".equals(e.getSQLState()) && e.getErrorCode() != 1062) throw e;
            }
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE app_meta SET schema_version=2 WHERE id=1 AND schema_version<2");
                s.executeUpdate("DELETE FROM deleted_records WHERE expires_at<=CURRENT_TIMESTAMP");
            }
            if (demo) seed(c);
        } catch (SQLException e) {
            throw failure(e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el esquema incluido en la aplicación.", e);
        }
    }

    /** Version 1 databases keep their rows and foreign keys; only nullable markers are added. */
    private static void migrateDeletionColumns(Connection c) throws SQLException {
        for (String table : List.of("projects", "app_users", "resources")) {
            if (hasDeletedColumn(c, table)) continue;
            try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN deleted_at TIMESTAMP NULL");
            } catch (SQLException e) {
                // Another initializer can add the same column between metadata lookup and ALTER.
                if (!hasDeletedColumn(c, table)) throw e;
            }
        }
    }

    private static boolean hasDeletedColumn(Connection c, String table) throws SQLException {
        for (String pattern : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet columns = c.getMetaData().getColumns(c.getCatalog(), null, pattern, null)) {
                while (columns.next()) if ("deleted_at".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    private void seed(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try {
            try (PreparedStatement p = c.prepareStatement("SELECT id FROM app_meta WHERE id=1 FOR UPDATE")) {
                try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Falta el control de esquema."); }
            }
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM app_users")) {
                r.next();
                if (r.getLong(1) != 0) { c.commit(); return; }
            }
            // Do not seed over any partially configured production database.
            for (String table : List.of("projects", "resources", "requests", "loans", "movements", "incidents", "audit_log")) {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    r.next();
                    if (r.getLong(1) != 0) { c.commit(); return; }
                }
            }
            long p1 = insert(c, "INSERT INTO projects(code,name,active) VALUES(?,?,TRUE)", "OBR-001", "Obra demostración Centro");
            long p2 = insert(c, "INSERT INTO projects(code,name,active) VALUES(?,?,TRUE)", "OBR-002", "Obra demostración Norte");
            char[] pass = "Demo2026!".toCharArray();
            long admin;
            try {
                admin = demoUser(c, "admin", "Administración Demo", "ADMIN", null, pass);
                demoUser(c, "bodega", "Bodega Demo", "BODEGA", null, pass);
                demoUser(c, "supervisor", "Supervisión Demo", "SUPERVISOR", p1, pass);
                demoUser(c, "trabajador", "Trabajador Demo", "TRABAJADOR", p1, pass);
                demoUser(c, "invitado", "Invitado Demo", "INVITADO", p1, pass);
                demoUser(c, "trabajador2", "Trabajador Norte Demo", "TRABAJADOR", p2, pass);
            } finally { Arrays.fill(pass, '\0'); }
            long tool = insert(c, "INSERT INTO resources(code,name,type,unit,location,minimum_quantity,available_quantity,damaged_quantity,active) VALUES(?,?,?,?,?,5,20,0,TRUE)",
                    "TAL-001", "Taladro percutor", "HERRAMIENTA", "unidad", "Bodega central · Estante A");
            long material = insert(c, "INSERT INTO resources(code,name,type,unit,location,minimum_quantity,available_quantity,damaged_quantity,active) VALUES(?,?,?,?,?,15,80,0,TRUE)",
                    "CEM-001", "Cemento gris", "MATERIAL", "bulto", "Bodega central · Zona B");
            insert(c, "INSERT INTO movements(resource_id,actor_id,type,quantity,note) VALUES(?,?,'ENTRADA',20,?)", tool, admin, "Carga inicial ficticia de demostración");
            insert(c, "INSERT INTO movements(resource_id,actor_id,type,quantity,note) VALUES(?,?,'ENTRADA',80,?)", material, admin, "Carga inicial ficticia de demostración");
            insert(c, "INSERT INTO audit_log(actor_id,action,entity_type,entity_id,detail) VALUES(?,'DEMO_INICIAL','SISTEMA',1,?)", admin, "Datos ficticios para demostración");
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    /** H2 2.4.240 retains a closed initializer session in constant-set IN checks.
     * Equivalent CASE checks also repair databases created by the first schema.
     * Add the replacement before removing the original so data always stays constrained. */
    private static void migrateH2Checks(Connection c) throws SQLException {
        record Check(String table, String name, String expression) {}
        List<Check> checks = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT t.table_name,t.constraint_name,k.check_clause FROM information_schema.table_constraints t JOIN information_schema.check_constraints k ON k.constraint_catalog=t.constraint_catalog AND k.constraint_schema=t.constraint_schema AND k.constraint_name=t.constraint_name WHERE t.constraint_type='CHECK' AND t.table_schema=SCHEMA()"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String table = r.getString(1);
                String expression = r.getString(3).toUpperCase(Locale.ROOT).replaceAll("[\\s\"()]", "");
                String replacement = switch (table.toLowerCase(Locale.ROOT) + ":" + expression) {
                    case "app_users:ROLEIN'ADMIN','BODEGA'ORPROJECT_IDISNOTNULL" -> "project_id IS NOT NULL OR CASE role WHEN 'ADMIN' THEN 1 WHEN 'BODEGA' THEN 1 ELSE 0 END=1";
                    case "app_users:ROLEIN'ADMIN','BODEGA','SUPERVISOR','TRABAJADOR','INVITADO'" -> "CASE role WHEN 'ADMIN' THEN 1 WHEN 'BODEGA' THEN 1 WHEN 'SUPERVISOR' THEN 1 WHEN 'TRABAJADOR' THEN 1 WHEN 'INVITADO' THEN 1 ELSE 0 END=1";
                    case "resources:TYPEIN'HERRAMIENTA','MATERIAL'" -> "CASE type WHEN 'HERRAMIENTA' THEN 1 WHEN 'MATERIAL' THEN 1 ELSE 0 END=1";
                    case "requests:STATUSIN'PENDIENTE','APROBADA','RECHAZADA','DESPACHADA'" -> "CASE status WHEN 'PENDIENTE' THEN 1 WHEN 'APROBADA' THEN 1 WHEN 'RECHAZADA' THEN 1 WHEN 'DESPACHADA' THEN 1 ELSE 0 END=1";
                    case "loans:STATUSIN'ACTIVO','CERRADO'" -> "CASE status WHEN 'ACTIVO' THEN 1 WHEN 'CERRADO' THEN 1 ELSE 0 END=1";
                    default -> null;
                };
                if (replacement != null) checks.add(new Check(table, r.getString(2), replacement));
            }
        }
        for (Check check : checks) {
            String replacementName = check.name() + "_CASE";
            boolean exists;
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=SCHEMA() AND constraint_name=?")) {
                p.setString(1, replacementName);
                try (ResultSet r = p.executeQuery()) { r.next(); exists = r.getInt(1) > 0; }
            }
            try (Statement s = c.createStatement()) {
                if (!exists) s.execute("ALTER TABLE " + quoted(check.table()) + " ADD CONSTRAINT " + quoted(replacementName) + " CHECK (" + check.expression() + ")");
                s.execute("ALTER TABLE " + quoted(check.table()) + " DROP CONSTRAINT " + quoted(check.name()));
            }
        }
    }

    private static String quoted(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }

    private static long demoUser(Connection c, String username, String name, String role, Long project, char[] password) throws SQLException {
        return insert(c, "INSERT INTO app_users(username,display_name,role,project_id,active,password_hash) VALUES(?,?,?,?,TRUE,?)",
                username, name, role, project, Passwords.hash(password));
    }

    static long insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(p, values);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("La base de datos no devolvió el ID creado.");
                return r.getLong(1);
            }
        }
    }

    static void bind(PreparedStatement p, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof java.time.LocalDate date) p.setDate(i + 1, java.sql.Date.valueOf(date));
            else p.setObject(i + 1, value);
        }
    }

    static IllegalStateException failure(SQLException e) {
        String state = e.getSQLState();
        String message;
        if (state != null && state.startsWith("23"))
            message = "No se guardó la operación: código o usuario duplicado, o relación de datos no válida. Revise los valores.";
        else if (state != null && (state.startsWith("40") || state.equals("HYT00")))
            message = "La operación coincidió con otra actualización. Intente de nuevo; no se guardaron cambios parciales.";
        else message = "No se pudo completar la operación de base de datos. Revise la conexión y la configuración del servidor.";
        // Do not attach JDBC exceptions: drivers can include credentials or bound values.
        return new IllegalStateException(message + " (SQLState " + (state == null ? "desconocido" : state) + ")");
    }

    @Override public void close() { closed = true; }
}
