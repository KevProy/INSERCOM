package co.insercom.inventory;

import static co.insercom.inventory.Models.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Transactional application boundary. Every permission is enforced here, including exports. */
public final class InventoryService {
    private final Database database;
    // Identity is intentional: copying the public record must never create a credential.
    private final Map<Session, Long> sessions = Collections.synchronizedMap(new IdentityHashMap<>());

    public InventoryService(Database database) { this.database = Objects.requireNonNull(database); }

    public boolean needsBootstrap() {
        return transaction(c -> { lockMetadata(c); return emptyDatabase(c); });
    }

    public void bootstrapAdmin(String username, String name, char[] password) {
        String login = username(username);
        String display = required(name, "Nombre", 160);
        String hash = Passwords.hash(password);
        transaction(c -> {
            lockMetadata(c);
            if (!emptyDatabase(c)) throw new IllegalArgumentException("El alta inicial sólo está disponible en una base de datos vacía.");
            long id = Database.insert(c, "INSERT INTO app_users(username,display_name,role,project_id,active,password_hash) VALUES(?,?,'ADMIN',NULL,TRUE,?)", login, display, hash);
            audit(c, id, "ALTA_INICIAL", "USUARIO", id, "Administrador inicial creado");
            return null;
        });
    }

    public Session login(String username, char[] password) {
        if (username == null || password == null) throw new SecurityException("Usuario o contraseña incorrectos.");
        return transaction(c -> {
            purgeExpiredDeleted(c);
            try (PreparedStatement p = prepared(c, "SELECT id,username,display_name,role,project_id,active,password_hash FROM app_users WHERE username=? AND deleted_at IS NULL FOR UPDATE", username.trim().toLowerCase(Locale.ROOT)); ResultSet r = p.executeQuery()) {
                if (!r.next() || !r.getBoolean("active") || !Passwords.verify(password, r.getString("password_hash")))
                    throw new SecurityException("Usuario o contraseña incorrectos.");
                Role role = Role.valueOf(r.getString("role"));
                Long project = nullableLong(r, "project_id");
                if (scoped(role)) requireProject(c, project, false);
                Session session = new Session(r.getLong("id"), r.getString("username"), r.getString("display_name"), role, project);
                sessions.put(session, deletionGeneration(c, session.id()));
                return session;
            }
        });
    }

    public void logout(Session session) { if (session != null) sessions.remove(session); }

    public void changePassword(Session session, char[] oldPassword, char[] newPassword) {
        String hash = Passwords.hash(newPassword);
        transaction(c -> {
            Session actor = authorize(c, session, true);
            try (PreparedStatement p = prepared(c, "SELECT password_hash FROM app_users WHERE id=?", actor.id()); ResultSet r = p.executeQuery()) {
                if (!r.next() || !Passwords.verify(oldPassword, r.getString(1))) throw new SecurityException("La contraseña actual no es correcta.");
            }
            update(c, "UPDATE app_users SET password_hash=? WHERE id=?", hash, actor.id());
            audit(c, actor.id(), "CAMBIO_CONTRASENA", "USUARIO", actor.id(), "Contraseña actualizada");
            return null;
        });
        synchronized (sessions) { sessions.keySet().removeIf(s -> s.id() == session.id() && s != session); }
    }

    public Dashboard dashboard(Session session) {
        return transaction(c -> {
            Session actor = authorize(c, session, false);
            Filter catalog = catalogScope(actor, "r", false);
            int count = scalarInt(c, "SELECT COUNT(*) FROM resources r WHERE 1=1" + catalog.sql(), catalog.values());
            long available = scalarLong(c, "SELECT COALESCE(SUM(r.available_quantity),0) FROM resources r WHERE 1=1" + catalog.sql(), catalog.values());
            Filter requests = scope(actor, "q.project_id", "q.requester_id");
            Filter loans = scope(actor, "l.project_id", "l.borrower_id");
            int pending = scalarInt(c, "SELECT COUNT(*) FROM requests q WHERE q.status='PENDIENTE'" + requests.sql(), requests.values());
            int active = scalarInt(c, "SELECT COUNT(*) FROM loans l WHERE l.status='ACTIVO'" + loans.sql(), loans.values());
            return new Dashboard(count, available, pending, active, alerts(c, actor).rows().size());
        });
    }

    public TableData table(Session session, View view) {
        if (view == null) throw new IllegalArgumentException("Seleccione una vista.");
        return transaction(c -> table(c, authorize(c, session, false), view));
    }

    public List<Choice> resources(Session session) {
        return transaction(c -> {
            Session actor = authorize(c, session, false);
            Filter catalog = catalogScope(actor, "r", true);
            return choices(c, "SELECT r.id,r.code,r.name FROM resources r WHERE 1=1" + catalog.sql() + " ORDER BY r.code", catalog.values());
        });
    }

    public List<Choice> projects(Session session) {
        return transaction(c -> {
            Session actor = authorize(c, session, false);
            return scoped(actor.role())
                    ? choices(c, "SELECT id,code,name FROM projects WHERE active=TRUE AND deleted_at IS NULL AND id=? ORDER BY code", actor.projectId())
                    : choices(c, "SELECT id,code,name FROM projects WHERE active=TRUE AND deleted_at IS NULL ORDER BY code");
        });
    }

    public long saveResource(Session session, Long id, ResourceInput input) {
        if (input == null) throw new IllegalArgumentException("Complete los datos del recurso.");
        String code = required(input.code(), "Código", 40);
        String name = required(input.name(), "Nombre", 160);
        String type = resourceType(input.type());
        String unit = required(input.unit(), "Unidad", 40);
        String location = required(input.location(), "Ubicación", 160);
        if (input.minimum() < 0) throw new IllegalArgumentException("El mínimo no puede ser negativo.");
        return transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            long saved;
            if (id == null) {
                saved = Database.insert(c, "INSERT INTO resources(code,name,type,unit,location,minimum_quantity,active) VALUES(?,?,?,?,?,?,?)", code, name, type, unit, location, input.minimum(), input.active());
            } else {
                Resource original = resource(c, id, true);
                if (!original.type().equals(type) && (scalarInt(c, "SELECT COUNT(*) FROM requests WHERE resource_id=?", id) > 0 || scalarInt(c, "SELECT COUNT(*) FROM movements WHERE resource_id=?", id) > 0))
                    throw new IllegalArgumentException("No puede cambiar el tipo de un recurso con historial. Cree otro recurso y desactive el anterior.");
                update(c, "UPDATE resources SET code=?,name=?,type=?,unit=?,location=?,minimum_quantity=?,active=? WHERE id=?", code, name, type, unit, location, input.minimum(), input.active(), id);
                saved = id;
            }
            audit(c, actor.id(), id == null ? "CREAR_RECURSO" : "EDITAR_RECURSO", "RECURSO", saved, code + " · " + name);
            return saved;
        });
    }

    public void receiveStock(Session session, long resourceId, int quantity, String note) {
        positive(quantity);
        String detail = optional(note, "Nota", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            Resource resource = resource(c, resourceId, true);
            if (!resource.active()) throw new IllegalArgumentException("El recurso está inactivo.");
            if ((long) resource.available() + quantity > Integer.MAX_VALUE) throw new IllegalArgumentException("La cantidad excede el límite de inventario.");
            update(c, "UPDATE resources SET available_quantity=available_quantity+? WHERE id=?", quantity, resourceId);
            movement(c, resourceId, null, null, null, actor.id(), "ENTRADA", quantity, null, detail);
            audit(c, actor.id(), "ENTRADA", "RECURSO", resourceId, "Cantidad: " + quantity + ". " + detail);
            return null;
        });
    }

    public long saveProject(Session session, Long id, String code, String name, boolean active) {
        String projectCode = required(code, "Código", 40);
        String projectName = required(name, "Nombre", 160);
        return transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN);
            long saved;
            if (id == null) saved = Database.insert(c, "INSERT INTO projects(code,name,active) VALUES(?,?,?)", projectCode, projectName, active);
            else {
                requireProjectExists(c, id, true);
                update(c, "UPDATE projects SET code=?,name=?,active=? WHERE id=?", projectCode, projectName, active, id);
                saved = id;
            }
            audit(c, actor.id(), id == null ? "CREAR_PROYECTO" : "EDITAR_PROYECTO", "PROYECTO", saved, projectCode + " · " + projectName);
            return saved;
        });
    }

    public long saveUser(Session session, Long id, UserInput input, char[] password) {
        if (input == null || input.role() == null) throw new IllegalArgumentException("Complete los datos y el rol del usuario.");
        String login = username(input.username());
        String display = required(input.name(), "Nombre", 160);
        if (scoped(input.role()) && input.projectId() == null) throw new IllegalArgumentException("Este rol requiere un proyecto asignado.");
        boolean changePassword = password != null && password.length > 0;
        if (id == null && !changePassword) throw new IllegalArgumentException("El usuario nuevo requiere contraseña.");
        String hash = changePassword ? Passwords.hash(password) : null;
        long saved = transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN);
            lockMetadata(c);
            if (input.projectId() != null) requireProject(c, input.projectId(), true);
            long result;
            if (id == null) {
                result = Database.insert(c, "INSERT INTO app_users(username,display_name,role,project_id,active,password_hash) VALUES(?,?,?,?,?,?)", login, display, input.role().name(), input.projectId(), input.active(), hash);
            } else {
                try (PreparedStatement p = prepared(c, "SELECT role,active FROM app_users WHERE id=? AND deleted_at IS NULL FOR UPDATE", id); ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("El usuario no existe.");
                    if (r.getBoolean("active") && r.getString("role").equals("ADMIN") && (!input.active() || input.role() != Role.ADMIN)
                            && scalarInt(c, "SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND active=TRUE AND deleted_at IS NULL") <= 1)
                        throw new IllegalArgumentException("Debe conservar al menos un administrador activo.");
                }
                update(c, "UPDATE app_users SET username=?,display_name=?,role=?,project_id=?,active=? WHERE id=?", login, display, input.role().name(), input.projectId(), input.active(), id);
                if (changePassword) update(c, "UPDATE app_users SET password_hash=? WHERE id=?", hash, id);
                result = id;
            }
            audit(c, actor.id(), id == null ? "CREAR_USUARIO" : "EDITAR_USUARIO", "USUARIO", result, login + " · " + input.role().name());
            return result;
        });
        if (id != null) {
            synchronized (sessions) {
                sessions.keySet().removeIf(s -> s.id() == saved && (changePassword || !input.active() || s.role() != input.role() || !Objects.equals(s.projectId(), input.projectId()) || !s.username().equals(login)));
            }
        }
        return saved;
    }

    public void deleteResource(Session session, long resourceId, String reason) {
        String detail = required(reason, "Motivo de eliminación", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            lockMetadata(c);
            Resource resource = resource(c, resourceId, true);
            ensureNoOpenWork(c, "resource_id", resourceId);
            String label = text(c, "SELECT CONCAT(code,' · ',name) FROM resources WHERE id=?", resourceId);
            markDeleted(c, actor, "resources", resource.type(), resourceId, label, detail);
            return null;
        });
    }

    public void deleteProject(Session session, long projectId, String reason) {
        String detail = required(reason, "Motivo de eliminación", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN);
            lockMetadata(c);
            requireProjectExists(c, projectId, true);
            ensureNoOpenWork(c, "project_id", projectId);
            if (scalarInt(c, "SELECT COUNT(*) FROM app_users WHERE project_id=? AND active=TRUE AND deleted_at IS NULL", projectId) > 0)
                throw new IllegalArgumentException("Primero reasigne o elimine los usuarios activos de este proyecto.");
            String label = text(c, "SELECT CONCAT(code,' · ',name) FROM projects WHERE id=?", projectId);
            markDeleted(c, actor, "projects", "PROYECTO", projectId, label, detail);
            return null;
        });
    }

    public void deleteUser(Session session, long userId, String reason) {
        String detail = required(reason, "Motivo de eliminación", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN);
            lockMetadata(c);
            if (actor.id() == userId) throw new IllegalArgumentException("No puede eliminar su propia cuenta desde una sesión abierta.");
            try (PreparedStatement p = prepared(c, "SELECT username,display_name,role,active FROM app_users WHERE id=? AND deleted_at IS NULL FOR UPDATE", userId); ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("El usuario no existe o ya fue eliminado.");
                if (r.getBoolean("active") && "ADMIN".equals(r.getString("role"))
                        && scalarInt(c, "SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND active=TRUE AND deleted_at IS NULL") <= 1)
                    throw new IllegalArgumentException("Debe conservar al menos un administrador activo.");
                if (scalarInt(c, "SELECT COUNT(*) FROM requests WHERE requester_id=? AND status IN ('PENDIENTE','APROBADA')", userId) > 0
                        || scalarInt(c, "SELECT COUNT(*) FROM loans WHERE borrower_id=? AND status='ACTIVO'", userId) > 0)
                    throw new IllegalArgumentException("El usuario tiene solicitudes pendientes o préstamos activos; cierre esas operaciones primero.");
                markDeleted(c, actor, "app_users", "USUARIO", userId, r.getString("username") + " · " + r.getString("display_name"), detail);
            }
            return null;
        });
        synchronized (sessions) { sessions.keySet().removeIf(s -> s.id() == userId); }
    }

    public void restoreDeleted(Session session, long deletionId) {
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN);
            lockMetadata(c);
            try (PreparedStatement p = prepared(c, "SELECT entity_type,entity_id,entity_label,expires_at FROM deleted_records WHERE id=? FOR UPDATE", deletionId); ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("El registro ya fue restaurado o venció su plazo de 15 días.");
                if (!r.getTimestamp("expires_at").after(databaseNow(c)))
                    throw new IllegalArgumentException("El plazo de recuperación de 15 días ya venció.");
                String type = r.getString("entity_type");
                String table = switch (type) {
                    case "PROYECTO" -> "projects";
                    case "USUARIO" -> "app_users";
                    case "HERRAMIENTA", "MATERIAL" -> "resources";
                    default -> throw new IllegalArgumentException("Tipo de registro eliminado no reconocido.");
                };
                long entity = r.getLong("entity_id");
                if (scalarInt(c, "SELECT COUNT(*) FROM " + table + " WHERE id=? AND deleted_at IS NOT NULL", entity) != 1)
                    throw new IllegalArgumentException("El registro original ya no está en la papelera.");
                if (type.equals("USUARIO")) {
                    try (PreparedStatement user = prepared(c, "SELECT project_id FROM app_users WHERE id=? FOR UPDATE", entity); ResultSet u = user.executeQuery()) {
                        u.next(); Long project = nullableLong(u, "project_id");
                        if (project != null) requireProject(c, project, true);
                    }
                }
                update(c, "UPDATE " + table + " SET deleted_at=NULL WHERE id=?", entity);
                update(c, "DELETE FROM deleted_records WHERE id=?", deletionId);
                audit(c, actor.id(), "RESTAURAR", type, entity, r.getString("entity_label"));
            }
            return null;
        });
    }

    private static void ensureNoOpenWork(Connection c, String column, long id) throws SQLException {
        if (scalarInt(c, "SELECT COUNT(*) FROM requests WHERE " + column + "=? AND status IN ('PENDIENTE','APROBADA')", id) > 0
                || scalarInt(c, "SELECT COUNT(*) FROM loans WHERE " + column + "=? AND status='ACTIVO'", id) > 0)
            throw new IllegalArgumentException("Hay solicitudes pendientes o préstamos activos vinculados. Cierre esas operaciones antes de eliminar.");
    }

    private static void markDeleted(Connection c, Session actor, String table, String type, long id, String label, String reason) throws SQLException {
        Timestamp now = databaseNow(c);
        Timestamp expiry = Timestamp.from(now.toInstant().plus(Duration.ofDays(15)));
        if (update(c, "UPDATE " + table + " SET deleted_at=? WHERE id=? AND deleted_at IS NULL", now, id) != 1)
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        Database.insert(c, "INSERT INTO deleted_records(entity_type,entity_id,entity_label,deleted_by,deleted_at,expires_at,reason) VALUES(?,?,?,?,?,?,?)", type, id, label, actor.id(), now, expiry, reason);
        String detail = label + ". Motivo: " + reason;
        audit(c, actor.id(), "ELIMINAR", type, id, detail.length() > 1000 ? detail.substring(0, 1000) : detail);
    }

    private static Timestamp databaseNow(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT CURRENT_TIMESTAMP")) {
            r.next(); return r.getTimestamp(1);
        }
    }

    private static String text(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement p = prepared(c, sql, id); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("El registro no existe.");
            return r.getString(1);
        }
    }

    private static void purgeExpiredDeleted(Connection c) throws SQLException {
        update(c, "DELETE FROM deleted_records WHERE expires_at<=CURRENT_TIMESTAMP");
    }

    public long request(Session session, RequestInput input) {
        if (input == null) throw new IllegalArgumentException("Complete la solicitud.");
        positive(input.quantity());
        String note = optional(input.note(), "Nota", 1000);
        return transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.SUPERVISOR, Role.TRABAJADOR);
            checkProject(actor, input.projectId());
            requireProject(c, input.projectId(), true);
            Resource resource = resource(c, input.resourceId(), true);
            if (!resource.active()) throw new IllegalArgumentException("El recurso está inactivo.");
            LocalDate due = resource.type().equals("HERRAMIENTA") ? input.dueDate() : null;
            if (resource.type().equals("HERRAMIENTA") && (due == null || due.isBefore(LocalDate.now())))
                throw new IllegalArgumentException("La herramienta requiere una fecha de devolución de hoy o futura.");
            long id = Database.insert(c, "INSERT INTO requests(resource_id,project_id,requester_id,quantity,due_date,note,status) VALUES(?,?,?,?,?,?,'PENDIENTE')", input.resourceId(), input.projectId(), actor.id(), input.quantity(), due, note);
            audit(c, actor.id(), "SOLICITAR", "SOLICITUD", id, "Cantidad: " + input.quantity() + ". " + note);
            return id;
        });
    }

    public void decide(Session session, long requestId, boolean approve, String note) {
        String detail = optional(note, "Nota de decisión", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.SUPERVISOR);
            Request request = request(c, requestId, true);
            checkProject(actor, request.project());
            if (!request.status().equals("PENDIENTE")) throw new IllegalArgumentException("Sólo puede decidir una solicitud PENDIENTE.");
            if (approve && request.requester() == actor.id()) throw new SecurityException("No puede aprobar su propia solicitud; debe decidir otro supervisor o administrador.");
            if (approve) {
                requireProject(c, request.project(), true);
                if (!resource(c, request.resource(), false).active()) throw new IllegalArgumentException("El recurso está inactivo.");
            }
            update(c, "UPDATE requests SET status=?,decision_note=?,decided_by=?,decided_at=CURRENT_TIMESTAMP WHERE id=?", approve ? "APROBADA" : "RECHAZADA", detail, actor.id(), requestId);
            audit(c, actor.id(), approve ? "APROBAR" : "RECHAZAR", "SOLICITUD", requestId, detail);
            return null;
        });
    }

    public void dispatch(Session session, long requestId) {
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            Request request = request(c, requestId, true);
            if (!request.status().equals("APROBADA")) throw new IllegalArgumentException("Sólo se puede despachar una solicitud APROBADA.");
            requireProject(c, request.project(), true);
            Resource resource = resource(c, request.resource(), true);
            if (!resource.active()) throw new IllegalArgumentException("El recurso está inactivo.");
            if (resource.available() < request.quantity()) throw new IllegalArgumentException("Stock insuficiente. Disponible: " + resource.available() + ".");
            update(c, "UPDATE resources SET available_quantity=available_quantity-? WHERE id=?", request.quantity(), request.resource());
            Long loan = null;
            String movementType = "CONSUMO";
            if (resource.type().equals("HERRAMIENTA")) {
                if (request.due() == null) throw new IllegalArgumentException("La solicitud de herramienta no tiene fecha de devolución.");
                loan = Database.insert(c, "INSERT INTO loans(request_id,resource_id,project_id,borrower_id,quantity,due_date,status) VALUES(?,?,?,?,?,?,'ACTIVO')", requestId, request.resource(), request.project(), request.requester(), request.quantity(), request.due());
                movementType = "PRESTAMO";
            }
            update(c, "UPDATE requests SET status='DESPACHADA',dispatched_at=CURRENT_TIMESTAMP WHERE id=?", requestId);
            movement(c, request.resource(), request.project(), requestId, loan, actor.id(), movementType, request.quantity(), null, "Despacho de solicitud #" + requestId);
            audit(c, actor.id(), "DESPACHAR", "SOLICITUD", requestId, movementType + "; cantidad: " + request.quantity());
            return null;
        });
    }

    public void returnLoan(Session session, long loanId, int quantity, String condition, String note) {
        positive(quantity);
        String state = condition == null ? "" : condition.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BUENO", "DANADO", "PERDIDO").contains(state)) throw new IllegalArgumentException("Condición válida: BUENO, DANADO o PERDIDO.");
        String detail = optional(note, "Nota", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            Loan loan = loan(c, loanId, true);
            if (!loan.status().equals("ACTIVO") || quantity > loan.quantity() - loan.returned()) throw new IllegalArgumentException("La devolución excede la cantidad pendiente del préstamo.");
            Resource resource = resource(c, loan.resource(), true);
            if (state.equals("BUENO") && (long) resource.available() + quantity > Integer.MAX_VALUE || state.equals("DANADO") && (long) resource.damaged() + quantity > Integer.MAX_VALUE)
                throw new IllegalArgumentException("La cantidad excede el límite de inventario.");
            if (state.equals("BUENO")) update(c, "UPDATE resources SET available_quantity=available_quantity+? WHERE id=?", quantity, loan.resource());
            if (state.equals("DANADO")) update(c, "UPDATE resources SET damaged_quantity=damaged_quantity+? WHERE id=?", quantity, loan.resource());
            boolean closed = loan.returned() + quantity == loan.quantity();
            update(c, "UPDATE loans SET returned_quantity=returned_quantity+?,status=?,closed_at=" + (closed ? "CURRENT_TIMESTAMP" : "NULL") + " WHERE id=?", quantity, closed ? "CERRADO" : "ACTIVO", loanId);
            movement(c, loan.resource(), loan.project(), loan.request(), loanId, actor.id(), "DEVOLUCION", quantity, state, detail);
            if (!state.equals("BUENO")) incident(c, loan, loanId, actor.id(), state, "Devolución: " + quantity + " " + state + ". " + detail);
            audit(c, actor.id(), "DEVOLVER", "PRESTAMO", loanId, "Cantidad: " + quantity + "; condición: " + state + ". " + detail);
            return null;
        });
    }

    public void reportIncident(Session session, long loanId, String type, String note) {
        String incidentType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DANADO", "PERDIDO", "RETRASO", "OTRO").contains(incidentType)) throw new IllegalArgumentException("Tipo válido: DANADO, PERDIDO, RETRASO u OTRO.");
        String detail = required(note, "Descripción de la novedad", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA, Role.SUPERVISOR, Role.TRABAJADOR);
            Loan loan = loan(c, loanId, true);
            checkLoan(actor, loan);
            incident(c, loan, loanId, actor.id(), incidentType, detail);
            return null;
        });
    }

    public void resolveIncident(Session session, long incidentId, String note) {
        String detail = required(note, "Resolución", 1000);
        transaction(c -> {
            Session actor = authorize(c, session, true, Role.ADMIN, Role.BODEGA);
            try (PreparedStatement p = prepared(c, "SELECT resolved FROM incidents WHERE id=? FOR UPDATE", incidentId); ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("La novedad no existe.");
                if (r.getBoolean(1)) throw new IllegalArgumentException("La novedad ya está resuelta.");
            }
            update(c, "UPDATE incidents SET resolved=TRUE,resolution_note=?,resolved_by=?,resolved_at=CURRENT_TIMESTAMP WHERE id=?", detail, actor.id(), incidentId);
            audit(c, actor.id(), "RESOLVER_NOVEDAD", "NOVEDAD", incidentId, detail);
            return null;
        });
    }

    public void exportCsv(Session session, View view, Path target) {
        if (target == null) throw new IllegalArgumentException("Seleccione un archivo de destino.");
        TableData data = table(session, view);
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writeCsvRow(writer, data.columns());
            for (List<Object> row : data.rows()) writeCsvRow(writer, row);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el CSV. Revise la carpeta de destino y los permisos.");
        }
    }

    private TableData table(Connection c, Session actor, View view) throws SQLException {
        Filter requests = scope(actor, "q.project_id", "q.requester_id");
        Filter loans = scope(actor, "l.project_id", "l.borrower_id");
        return switch (view) {
            case RESOURCES -> {
                Filter catalog = catalogScope(actor, "r", false);
                yield query(c, List.of("ID", "Código", "Nombre", "Tipo", "Unidad", "Ubicación", "Mínimo", "Disponible", "Dañado", "Activo"),
                        "SELECT r.id,r.code,r.name,r.type,r.unit,r.location,r.minimum_quantity,r.available_quantity,r.damaged_quantity,r.active FROM resources r WHERE 1=1" + catalog.sql() + " ORDER BY r.code", catalog.values());
            }
            case PROJECTS -> query(c, List.of("ID", "Código", "Nombre", "Activo"), "SELECT id,code,name,active FROM projects WHERE deleted_at IS NULL" + (scoped(actor.role()) ? " AND id=?" : "") + " ORDER BY code", scoped(actor.role()) ? new Object[]{actor.projectId()} : new Object[]{});
            case USERS -> {
                allow(actor, Role.ADMIN);
                yield query(c, List.of("ID", "Usuario", "Nombre", "Rol", "Proyecto ID", "Activo"), "SELECT id,username,display_name,role,project_id,active FROM app_users WHERE deleted_at IS NULL ORDER BY username");
            }
            case REQUESTS -> query(c, List.of("ID", "Recurso", "Proyecto", "Solicitante", "Cantidad", "Devolución", "Estado", "Nota", "Creada"),
                    "SELECT q.id,CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),u.display_name,q.quantity,q.due_date,q.status,q.note,q.created_at FROM requests q JOIN resources r ON r.id=q.resource_id JOIN projects p ON p.id=q.project_id JOIN app_users u ON u.id=q.requester_id WHERE 1=1" + requests.sql() + " ORDER BY q.id DESC", requests.values());
            case LOANS -> query(c, List.of("ID", "Recurso", "Proyecto", "Responsable", "Cantidad", "Devuelto", "Pendiente", "Vencimiento", "Estado"),
                    "SELECT l.id,CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),u.display_name,l.quantity,l.returned_quantity,l.quantity-l.returned_quantity,l.due_date,l.status FROM loans l JOIN resources r ON r.id=l.resource_id JOIN projects p ON p.id=l.project_id JOIN app_users u ON u.id=l.borrower_id WHERE 1=1" + loans.sql() + " ORDER BY l.id DESC", loans.values());
            case MOVEMENTS -> {
                Filter filter = scope(actor, "m.project_id", "q.requester_id");
                yield query(c, List.of("ID", "Fecha", "Recurso", "Proyecto", "Tipo", "Cantidad", "Condición", "Responsable", "Nota"),
                        "SELECT m.id,m.created_at,CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),m.type,m.quantity,m.resource_condition,u.display_name,m.note FROM movements m JOIN resources r ON r.id=m.resource_id LEFT JOIN projects p ON p.id=m.project_id LEFT JOIN requests q ON q.id=m.request_id JOIN app_users u ON u.id=m.actor_id WHERE 1=1" + filter.sql() + " ORDER BY m.id DESC", filter.values());
            }
            case INCIDENTS -> {
                Filter filter = scope(actor, "i.project_id", "l.borrower_id");
                yield query(c, List.of("ID", "Fecha", "Recurso", "Proyecto", "Préstamo", "Tipo", "Reportó", "Estado", "Nota", "Resolución"),
                        "SELECT i.id,i.created_at,CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),i.loan_id,i.type,u.display_name,CASE WHEN i.resolved=TRUE THEN 'RESUELTA' ELSE 'ABIERTA' END,i.note,i.resolution_note FROM incidents i JOIN resources r ON r.id=i.resource_id JOIN projects p ON p.id=i.project_id JOIN loans l ON l.id=i.loan_id JOIN app_users u ON u.id=i.reporter_id WHERE 1=1" + filter.sql() + " ORDER BY i.id DESC", filter.values());
            }
            case ALERTS -> alerts(c, actor);
            case AUDIT -> {
                allow(actor, Role.ADMIN);
                yield query(c, List.of("ID", "Fecha", "Responsable", "Acción", "Entidad", "Registro", "Detalle"), "SELECT a.id,a.created_at,u.display_name,a.action,a.entity_type,a.entity_id,a.detail FROM audit_log a JOIN app_users u ON u.id=a.actor_id ORDER BY a.id DESC");
            }
            case DELETED -> {
                allow(actor, Role.ADMIN);
                purgeExpiredDeleted(c);
                yield query(c, List.of("ID", "Tipo", "Registro", "Eliminado por", "Fecha de eliminación", "Disponible hasta", "Motivo"),
                        "SELECT d.id,d.entity_type,d.entity_label,u.display_name,d.deleted_at,d.expires_at,d.reason FROM deleted_records d JOIN app_users u ON u.id=d.deleted_by WHERE d.expires_at>CURRENT_TIMESTAMP ORDER BY d.id DESC");
            }
        };
    }

    private TableData alerts(Connection c, Session actor) throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        Filter catalog = catalogScope(actor, "r", true);
        rows.addAll(query(c, List.of("ID", "Tipo", "Recurso", "Proyecto", "Detalle"), "SELECT r.id,'STOCK BAJO',CONCAT(r.code,' · ',r.name),'',CONCAT('Disponible: ',r.available_quantity,'; mínimo: ',r.minimum_quantity) FROM resources r WHERE r.available_quantity<=r.minimum_quantity" + catalog.sql() + " ORDER BY r.code", catalog.values()).rows());
        Filter filter = scope(actor, "l.project_id", "l.borrower_id");
        rows.addAll(query(c, List.of("ID", "Tipo", "Recurso", "Proyecto", "Detalle"), "SELECT l.id,'PRÉSTAMO VENCIDO',CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),CONCAT('Pendiente: ',l.quantity-l.returned_quantity,'; venció: ',l.due_date) FROM loans l JOIN resources r ON r.id=l.resource_id JOIN projects p ON p.id=l.project_id WHERE l.status='ACTIVO' AND l.due_date<CURRENT_DATE" + filter.sql() + " ORDER BY l.due_date", filter.values()).rows());
        Filter incidents = scope(actor, "i.project_id", "l.borrower_id");
        rows.addAll(query(c, List.of("ID", "Tipo", "Recurso", "Proyecto", "Detalle"), "SELECT i.id,'NOVEDAD ABIERTA',CONCAT(r.code,' · ',r.name),CONCAT(p.code,' · ',p.name),CONCAT(i.type,': ',i.note) FROM incidents i JOIN resources r ON r.id=i.resource_id JOIN projects p ON p.id=i.project_id JOIN loans l ON l.id=i.loan_id WHERE i.resolved=FALSE" + incidents.sql() + " ORDER BY i.id DESC", incidents.values()).rows());
        return new TableData(List.of("ID", "Tipo", "Recurso", "Proyecto", "Detalle"), List.copyOf(rows));
    }

    private Session authorize(Connection c, Session session, boolean lock, Role... roles) throws SQLException {
        Long generation = session == null ? null : sessions.get(session);
        if (generation == null) throw new SecurityException("Debe iniciar sesión para continuar.");
        try (PreparedStatement p = prepared(c, "SELECT username,role,project_id,active FROM app_users WHERE id=? AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""), session.id()); ResultSet r = p.executeQuery()) {
            if (!r.next() || !r.getBoolean("active") || !session.username().equals(r.getString("username")) || !session.role().name().equals(r.getString("role")) || !Objects.equals(session.projectId(), nullableLong(r, "project_id"))) {
                sessions.remove(session);
                throw new SecurityException("La cuenta o sus permisos cambiaron. Inicie sesión nuevamente.");
            }
        }
        if (generation.longValue() != deletionGeneration(c, session.id())) {
            sessions.remove(session);
            throw new SecurityException("La cuenta fue eliminada. Inicie una sesión nueva después de su restauración.");
        }
        if (scoped(session.role())) requireProject(c, session.projectId(), lock);
        if (roles.length > 0) allow(session, roles);
        return session;
    }

    private static long deletionGeneration(Connection c, long userId) throws SQLException {
        return scalarLong(c, "SELECT COALESCE(MAX(id),0) FROM audit_log WHERE action='ELIMINAR' AND entity_type='USUARIO' AND entity_id=?", userId);
    }

    private static void allow(Session actor, Role... roles) {
        for (Role role : roles) if (actor.role() == role) return;
        throw new SecurityException("Su rol no tiene permiso para realizar esta acción.");
    }

    private static boolean scoped(Role role) { return role != Role.ADMIN && role != Role.BODEGA; }
    private static boolean warehouse(Session actor) { return !scoped(actor.role()); }
    private static void checkProject(Session actor, long project) {
        if (scoped(actor.role()) && !Objects.equals(actor.projectId(), project)) throw new SecurityException("Sólo puede operar en su proyecto asignado.");
    }
    private static void checkLoan(Session actor, Loan loan) {
        checkProject(actor, loan.project());
        if (actor.role() == Role.TRABAJADOR && actor.id() != loan.borrower()) throw new SecurityException("Sólo puede operar sobre sus propios préstamos.");
    }

    private record Filter(String sql, Object[] values) {}
    private static Filter catalogScope(Session actor, String alias, boolean activeOnly) {
        String active = " AND " + alias + ".deleted_at IS NULL" + (activeOnly || !warehouse(actor) ? " AND " + alias + ".active=TRUE" : "");
        if (actor.role() == Role.INVITADO)
            return new Filter(active + " AND EXISTS (SELECT 1 FROM requests catalog_request WHERE catalog_request.resource_id=" + alias + ".id AND catalog_request.project_id=?)", new Object[]{actor.projectId()});
        return new Filter(active, new Object[]{});
    }
    private static Filter scope(Session actor, String project, String owner) {
        if (actor.role() == Role.TRABAJADOR) return new Filter(" AND " + project + "=? AND " + owner + "=?", new Object[]{actor.projectId(), actor.id()});
        if (scoped(actor.role())) return new Filter(" AND " + project + "=?", new Object[]{actor.projectId()});
        return new Filter("", new Object[]{});
    }
    private record Resource(String type, int available, int damaged, boolean active) {}
    private static Resource resource(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement p = prepared(c, "SELECT type,available_quantity,damaged_quantity,active FROM resources WHERE id=? AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""), id); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("El recurso no existe.");
            return new Resource(r.getString(1), r.getInt(2), r.getInt(3), r.getBoolean(4));
        }
    }
    private record Request(long resource, long project, long requester, int quantity, LocalDate due, String status) {}
    private static Request request(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement p = prepared(c, "SELECT resource_id,project_id,requester_id,quantity,due_date,status FROM requests WHERE id=?" + (lock ? " FOR UPDATE" : ""), id); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("La solicitud no existe.");
            java.sql.Date date = r.getDate(5);
            return new Request(r.getLong(1), r.getLong(2), r.getLong(3), r.getInt(4), date == null ? null : date.toLocalDate(), r.getString(6));
        }
    }
    private record Loan(long request, long resource, long project, long borrower, int quantity, int returned, String status) {}
    private static Loan loan(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement p = prepared(c, "SELECT request_id,resource_id,project_id,borrower_id,quantity,returned_quantity,status FROM loans WHERE id=?" + (lock ? " FOR UPDATE" : ""), id); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("El préstamo no existe.");
            return new Loan(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getInt(5), r.getInt(6), r.getString(7));
        }
    }

    private static void requireProject(Connection c, Long id, boolean lock) throws SQLException {
        if (id == null) throw new SecurityException("La cuenta requiere un proyecto asignado.");
        try (PreparedStatement p = prepared(c, "SELECT active FROM projects WHERE id=? AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""), id); ResultSet r = p.executeQuery()) {
            if (!r.next() || !r.getBoolean(1)) throw new IllegalArgumentException("El proyecto no existe o está inactivo.");
        }
    }
    private static void requireProjectExists(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement p = prepared(c, "SELECT id FROM projects WHERE id=? AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""), id); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("El proyecto no existe.");
        }
    }
    private static void lockMetadata(Connection c) throws SQLException {
        try (PreparedStatement p = prepared(c, "SELECT id FROM app_meta WHERE id=1 FOR UPDATE"); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalStateException("Inicialice el esquema antes de continuar.");
        }
    }
    private static boolean emptyDatabase(Connection c) throws SQLException {
        for (String table : List.of("app_users", "projects", "resources", "requests", "loans", "movements", "incidents", "audit_log"))
            if (scalarInt(c, "SELECT COUNT(*) FROM " + table) != 0) return false;
        return true;
    }

    private static void movement(Connection c, long resource, Long project, Long request, Long loan, long actor, String type, int quantity, String condition, String note) throws SQLException {
        Database.insert(c, "INSERT INTO movements(resource_id,project_id,request_id,loan_id,actor_id,type,quantity,resource_condition,note) VALUES(?,?,?,?,?,?,?,?,?)", resource, project, request, loan, actor, type, quantity, condition, note);
    }
    private static void incident(Connection c, Loan loan, long loanId, long actor, String type, String note) throws SQLException {
        long id = Database.insert(c, "INSERT INTO incidents(resource_id,project_id,loan_id,reporter_id,type,note) VALUES(?,?,?,?,?,?)", loan.resource(), loan.project(), loanId, actor, type, note.length() > 1000 ? note.substring(0, 1000) : note);
        audit(c, actor, "REPORTAR_NOVEDAD", "NOVEDAD", id, type + "; préstamo #" + loanId);
    }
    private static void audit(Connection c, long actor, String action, String type, long id, String detail) throws SQLException {
        Database.insert(c, "INSERT INTO audit_log(actor_id,action,entity_type,entity_id,detail) VALUES(?,?,?,?,?)", actor, action, type, id, detail.length() > 1000 ? detail.substring(0, 1000) : detail);
    }
    private static PreparedStatement prepared(Connection c, String sql, Object... values) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        try { Database.bind(p, values); return p; }
        catch (SQLException e) { p.close(); throw e; }
    }
    private static int update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = prepared(c, sql, values)) { return p.executeUpdate(); }
    }
    private static int scalarInt(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = prepared(c, sql, values); ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
    }
    private static long scalarLong(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = prepared(c, sql, values); ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
    }
    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static TableData query(Connection c, List<String> columns, String sql, Object... values) throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        try (PreparedStatement p = prepared(c, sql, values); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                List<Object> row = new ArrayList<>(columns.size());
                for (int i = 1; i <= columns.size(); i++) row.add(r.getObject(i));
                rows.add(Collections.unmodifiableList(row));
            }
        }
        return new TableData(List.copyOf(columns), List.copyOf(rows));
    }
    private static List<Choice> choices(Connection c, String sql, Object... values) throws SQLException {
        List<Choice> result = new ArrayList<>();
        try (PreparedStatement p = prepared(c, sql, values); ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(new Choice(r.getLong(1), r.getString(2) + " · " + r.getString(3)));
        }
        return List.copyOf(result);
    }
    private static String required(String value, String name, int max) {
        String result = optional(value, name, max);
        if (result.isBlank()) throw new IllegalArgumentException(name + " es obligatorio.");
        return result;
    }
    private static String optional(String value, String name, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) throw new IllegalArgumentException(name + " admite hasta " + max + " caracteres.");
        if (result.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " contiene caracteres no válidos.");
        return result;
    }
    private static String username(String value) {
        String result = required(value, "Usuario", 80).toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z0-9][a-z0-9._-]{2,79}")) throw new IllegalArgumentException("El usuario requiere 3 a 80 letras sin tildes, números, punto, guion o guion bajo.");
        return result;
    }
    private static String resourceType(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("HERRAMIENTA", "MATERIAL").contains(result)) throw new IllegalArgumentException("Tipo válido: HERRAMIENTA o MATERIAL.");
        return result;
    }
    private static void positive(int quantity) { if (quantity <= 0) throw new IllegalArgumentException("La cantidad debe ser un entero positivo."); }
    private static void writeCsvRow(BufferedWriter writer, List<?> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) writer.write(',');
            Object value = row.get(i);
            String text = value == null ? "" : value.toString();
            if (value instanceof String) {
                String leading = text.stripLeading();
                if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0 || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) text = "'" + text;
            }
            writer.write('"'); writer.write(text.replace("\"", "\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }

    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(Work<T> work) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException ignored) { /* Original operation determines the message. */ }
                throw e;
            }
        } catch (SQLException e) { throw Database.failure(e); }
    }
}
