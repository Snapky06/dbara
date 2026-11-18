package bd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConexionBD {

    private static final String ARCHIVO = "conexiones.properties";

    public static class Perfil {
        public String nombre;
        public String ip;
        public String puerto;
        public String ruta;
        public String usuario;
        public String clave;

        public Perfil(String nombre, String ip, String puerto, String ruta, String usuario, String clave) {
            this.nombre = nombre;
            this.ip = ip;
            this.puerto = puerto;
            this.ruta = ruta;
            this.usuario = usuario;
            this.clave = clave;
        }

        public String getUrl() {
            String r = ruta.startsWith("/") ? ruta : "/" + ruta;
            return "jdbc:interbase://" + ip + ":" + puerto + r;
        }

        @Override
        public String toString() {
            return nombre + " (" + usuario + "@" + ip + ")";
        }
    }

    public static Connection conectar(Perfil p) throws ClassNotFoundException, SQLException {
        Class.forName("interbase.interclient.Driver");
        Properties props = new Properties();
        props.setProperty("user", p.usuario);
        props.setProperty("password", p.clave);
        props.setProperty("charSet", "UTF8");
        return DriverManager.getConnection(p.getUrl(), props);
    }

    public static void guardar(List<Perfil> lista) throws IOException {
        Properties props = new Properties();
        props.setProperty("total", String.valueOf(lista.size()));
        for (int i = 0; i < lista.size(); i++) {
            Perfil p = lista.get(i);
            props.setProperty("p." + i + ".nombre", p.nombre);
            props.setProperty("p." + i + ".ip", p.ip);
            props.setProperty("p." + i + ".puerto", p.puerto);
            props.setProperty("p." + i + ".ruta", p.ruta);
            props.setProperty("p." + i + ".usuario", p.usuario);
            props.setProperty("p." + i + ".clave", p.clave != null ? p.clave : "");
        }
        try (FileOutputStream out = new FileOutputStream(ARCHIVO)) {
            props.store(out, null);
        }
    }

    public static List<Perfil> cargar() {
        List<Perfil> lista = new ArrayList<>();
        File f = new File(ARCHIVO);
        if (!f.exists()) {
            return lista;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
            int total = Integer.parseInt(props.getProperty("total", "0"));
            for (int i = 0; i < total; i++) {
                lista.add(new Perfil(
                    props.getProperty("p." + i + ".nombre", "BD " + i),
                    props.getProperty("p." + i + ".ip", "localhost"),
                    props.getProperty("p." + i + ".puerto", "3050"),
                    props.getProperty("p." + i + ".ruta", ""),
                    props.getProperty("p." + i + ".usuario", "SYSDBA"),
                    props.getProperty("p." + i + ".clave", "")
                ));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lista;
    }
}