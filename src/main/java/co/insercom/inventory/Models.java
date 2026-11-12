package co.insercom.inventory;

import java.time.LocalDate;
import java.util.List;

public final class Models {
    private Models() {}
    public enum Role {
        ADMIN("Administrador"), BODEGA("Responsable de bodega"),
        SUPERVISOR("Supervisor de obra"), TRABAJADOR("Trabajador autorizado"), INVITADO("Invitado");
        private final String label;
        Role(String label) { this.label = label; }
        public String toString() { return label; }
    }
    public enum View {
        RESOURCES("Inventario"), PROJECTS("Proyectos"), REQUESTS("Solicitudes"),
        LOANS("Préstamos"), MOVEMENTS("Movimientos"), ALERTS("Alertas"),
        INCIDENTS("Novedades"), USERS("Usuarios"), AUDIT("Auditoría"), DELETED("Eliminados (15 días)");
        private final String label;
        View(String label) { this.label = label; }
        public String toString() { return label; }
    }
    public record Session(long id, String username, String name, Role role, Long projectId) {}
    public record Choice(long id, String label) {
        public String toString() { return label; }
    }
    public record TableData(List<String> columns, List<List<Object>> rows) {}
    public record Dashboard(int resources, long available, int pending, int activeLoans, int alerts) {}
    public record ResourceInput(String code, String name, String type, String unit,
                                String location, int minimum, boolean active) {}
    public record UserInput(String username, String name, Role role, Long projectId, boolean active) {}
    public record RequestInput(long resourceId, long projectId, int quantity,
                               LocalDate dueDate, String note) {}
}
