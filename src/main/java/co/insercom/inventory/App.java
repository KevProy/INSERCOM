package co.insercom.inventory;

import static co.insercom.inventory.Models.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class App {
    private App() {}

    public static void main(String[] args) {
        try {
            installLookAndFeel();
            List<String> arguments=List.of(args);
            if(arguments.contains("--capture")) {
                int index=arguments.indexOf("--capture");
                if(index+1>=args.length) throw new IllegalArgumentException("Indique la carpeta para las capturas.");
                capture(Path.of(args[index+1]));
                return;
            }
            boolean mysql=arguments.contains("--mysql");
            Database db=database(arguments,mysql);
            db.initialize(!mysql || arguments.contains("--seed-demo"));
            InventoryService service=new InventoryService(db);
            if(arguments.contains("--verify-db")) {
                try(Connection c=db.connect()) {
                    System.out.println("Conexión verificada: "+c.getMetaData().getDatabaseProductName()+" "+c.getMetaData().getDatabaseProductVersion());
                }
                db.close(); return;
            }
            String label=mysql?"MySQL · "+db.description():"Demostración · datos ficticios · H2 local";
            SwingUtilities.invokeLater(()->{
                try {
                    if(service.needsBootstrap() && !bootstrap(service)) { db.close(); return; }
                    Session session=LoginDialog.showLogin(service,label);
                    if(session==null) { db.close(); return; }
                    InventoryFrame frame=new InventoryFrame(service,session,label);
                    frame.addWindowListener(new WindowAdapter(){
                        public void windowClosed(WindowEvent e) { service.logout(session); db.close(); }
                    });
                    frame.setVisible(true);
                } catch(Exception e) { showFailure(e); db.close(); }
            });
        } catch(Exception e) {
            if(Arrays.asList(args).contains("--verify-db") || Arrays.asList(args).contains("--capture")) {
                System.err.println("No fue posible iniciar: "+safeMessage(e)); System.exit(1);
            }
            showFailure(e);
        }
    }

    static Database database(List<String> arguments,boolean mysql) throws Exception {
        if(!mysql) {
            String defaultHome=Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse(System.getProperty("user.home"));
            Path folder=Path.of(System.getProperty("insercom.data",Path.of(defaultHome,"INSERCOM","demostracion").toString())).toAbsolutePath();
            Files.createDirectories(folder);
            return new Database("jdbc:h2:file:"+folder.resolve("inventario").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE","sa","");
        }
        Properties p=new Properties();
        int i=arguments.indexOf("--config");
        Path config=Path.of(i>=0 && i+1<arguments.size()?arguments.get(i+1):"config/mysql.properties");
        if(Files.isRegularFile(config)) try(var in=Files.newInputStream(config)){p.load(in);}
        String url=envOr("INSERCOM_DB_URL",p.getProperty("db.url","jdbc:mysql://localhost:3306/insercom?connectionTimeZone=America/Bogota"));
        String user=envOr("INSERCOM_DB_USER",p.getProperty("db.user","insercom_app"));
        String password=envOr("INSERCOM_DB_PASSWORD",p.getProperty("db.password",""));
        if(!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException("El modo MySQL requiere una dirección jdbc:mysql válida.");
        return new Database(url,user,password);
    }

    private static String envOr(String key,String fallback) {
        String value=System.getenv(key); return value==null?fallback:value;
    }

    private static boolean bootstrap(InventoryService service) {
        JTextField username=new JTextField("admin",22), name=new JTextField("Administrador",22);
        JPasswordField password=new JPasswordField(22), confirmation=new JPasswordField(22);
        JPanel panel=new JPanel(new GridLayout(0,1,4,5));
        for(Object part:List.of(new JLabel("Cree la primera cuenta administradora de esta base."),new JLabel("Usuario"),username,new JLabel("Nombre"),name,new JLabel("Contraseña (mínimo 8 caracteres)"),password,new JLabel("Repita la contraseña"),confirmation)) panel.add((Component)part);
        while(JOptionPane.showConfirmDialog(null,panel,"Configuración inicial de INSERCOM",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            char[] a=password.getPassword(), b=confirmation.getPassword();
            try {
                if(!Arrays.equals(a,b)) throw new IllegalArgumentException("Las contraseñas no coinciden.");
                service.bootstrapAdmin(username.getText().trim(),name.getText().trim(),a); return true;
            } catch(RuntimeException e) { JOptionPane.showMessageDialog(null,safeMessage(e),"Revise los datos",JOptionPane.WARNING_MESSAGE); }
            finally { Arrays.fill(a,'\0'); Arrays.fill(b,'\0'); }
        }
        return false;
    }

    private static void installLookAndFeel() {
        try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch(Exception ignored) { }
        Font f=new Font("Segoe UI",Font.PLAIN,14);
        for(Object key:new ArrayList<>(UIManager.getDefaults().keySet())) if(UIManager.get(key) instanceof Font) UIManager.put(key,f);
        UIManager.put("control",new Color(245,247,251));
        UIManager.put("nimbusBase",new Color(29,68,112));
        UIManager.put("nimbusFocus",new Color(40,104,181));
    }

    private static void capture(Path output) throws Exception {
        Files.createDirectories(output);
        try(Database db=new Database("jdbc:h2:mem:capture;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","")) {
            db.initialize(true);
            InventoryService service=new InventoryService(db);
            Session admin=service.login("admin","Demo2026!".toCharArray());
            Session worker=service.login("trabajador","Demo2026!".toCharArray());
            Session supervisor=service.login("supervisor","Demo2026!".toCharArray());
            Session warehouse=service.login("bodega","Demo2026!".toCharArray());
            TableData resources=service.table(admin,View.RESOURCES);
            long resource=resources.rows().stream().filter(row->"HERRAMIENTA".equals(String.valueOf(row.get(3))) && ((Number)row.get(7)).intValue()>=2).map(row->((Number)row.get(0)).longValue()).findFirst().orElseThrow();
            long request=service.request(worker,new RequestInput(resource,worker.projectId(),2,LocalDate.now().plusDays(5),"Solicitud de prueba técnica documentada"));
            service.decide(supervisor,request,true,"Revisión de escenario funcional"); service.dispatch(warehouse,request);
            long loan=((Number)service.table(warehouse,View.LOANS).rows().get(0).get(0)).longValue();
            service.returnLoan(warehouse,loan,1,"DANADO","Daño simulado para verificar trazabilidad");
            long removed=service.saveResource(admin,null,new ResourceInput("MED-001","Medidor de demostración","HERRAMIENTA","unidad","Bodega central",3,true));
            service.deleteResource(admin,removed,"Retiro ficticio para documentar la papelera de 15 días");
            SwingUtilities.invokeAndWait(()->{
                InventoryFrame frame=new InventoryFrame(service,admin,"Demostración · evidencia de ejecución con datos ficticios");
                frame.setLocation(-3000,-3000); frame.setVisible(true);
                try {
                    for(View view:List.of(View.RESOURCES,View.REQUESTS,View.LOANS,View.MOVEMENTS,View.ALERTS,View.USERS,View.AUDIT,View.DELETED)) {
                        frame.selectView(view); frame.refreshAll();
                        ImageIO.write(frame.snapshot(),"png",output.resolve(view.name().toLowerCase(Locale.ROOT)+".png").toFile());
                    }
                    System.out.println("Capturas de aplicación real: "+output.toAbsolutePath());
                } catch(Exception e) { throw new IllegalStateException(e); }
                finally { frame.dispose(); }
            });
        }
    }

    private static String safeMessage(Throwable e) {
        String msg=e.getMessage(); return msg==null?"Revise la configuración y vuelva a intentarlo.":msg;
    }
    private static void showFailure(Throwable e) {
        JOptionPane.showMessageDialog(null,"No fue posible completar la operación.\n"+safeMessage(e),"INSERCOM Inventario",JOptionPane.ERROR_MESSAGE);
    }
}
