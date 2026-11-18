package bd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConexionBD {

    private static final String ARCHIVO = "conexiones.properties";
    private static Driver driverCargado;

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
            String r = ruta.trim();
            if (!r.startsWith("/")) {
                r = "/" + r;
            }
            String p = puerto.trim();
            if (p.isEmpty() || p.equals("3050")) {
                return "jdbc:interbase://" + ip.trim() + r;
            }
            return "jdbc:interbase://" + ip.trim() + ":" + p + r;
        }

        @Override
        public String toString() {
            return nombre + " (" + usuario + "@" + ip + ")";
        }
    }

    private static Driver obtenerDriver() throws Exception {
        if (driverCargado != null) {
            return driverCargado;
        }
        try {
            Class<?> cls = Class.forName("interbase.interclient.Driver");
            driverCargado = (Driver) cls.getDeclaredConstructor().newInstance();
            return driverCargado;
        } catch (ClassNotFoundException e) {
            File dirLib = new File("lib");
            if (dirLib.exists() && dirLib.isDirectory()) {
                File[] jars = dirLib.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    URL[] urls = new URL[jars.length];
                    for (int i = 0; i < jars.length; i++) {
                        urls[i] = jars[i].toURI().toURL();
                    }
                    URLClassLoader loader = new URLClassLoader(urls, ConexionBD.class.getClassLoader());
                    Class<?> cls = Class.forName("interbase.interclient.Driver", true, loader);
                    driverCargado = (Driver) cls.getDeclaredConstructor().newInstance();
                    return driverCargado;
                }
            }
            throw new SQLException("No se encontro interclient.jar dentro de la carpeta lib/");
        }
    }

    public static Connection conectar(Perfil p) throws Exception {
        Driver drv = obtenerDriver();
        Properties props = new Properties();
        props.setProperty("user", p.usuario.trim());
        props.setProperty("password", p.clave);

        try {
            return drv.connect(p.getUrl(), props);
        } catch (SQLException ex) {
            String urlAlterna = "jdbc:interbase://" + p.ip.trim() + ":" + p.puerto.trim() +
                                (p.ruta.trim().startsWith("/") ? p.ruta.trim() : "/" + p.ruta.trim());
            return drv.connect(urlAlterna, props);
        }
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