package api;

import bd.Catalogo;
import bd.ConexionBD;
import bd.ConexionBD.Perfil;
import bd.EjecutorSQL;
import bd.EjecutorSQL.ResultadoConsulta;
import bd.GeneradorDDL;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServidorApi {

    private static Connection conexionActiva;
    private static Catalogo catalogo;
    private static GeneradorDDL generadorDDL;
    private static EjecutorSQL ejecutorSQL;

    public static void main(String[] args) throws IOException {
        int puerto = 8080;
        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);

        servidor.createContext("/", ServidorApi::manejarEstaticos);
        servidor.createContext("/api/conectar", ServidorApi::manejarConectar);
        servidor.createContext("/api/catalogo", ServidorApi::manejarCatalogo);
        servidor.createContext("/api/ddl", ServidorApi::manejarDdl);
        servidor.createContext("/api/ejecutar", ServidorApi::manejarEjecutar);

        servidor.start();
        System.out.println("DBara Web corriendo en http://localhost:" + puerto);
    }

    private static void manejarEstaticos(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String ruta = exchange.getRequestURI().getPath();
        if (ruta.equals("/")) {
            ruta = "/index.html";
        }
        Path archivo = Paths.get("src/web" + ruta).normalize();
        Path base = Paths.get("src/web").toAbsolutePath().normalize();
        if (!archivo.toAbsolutePath().normalize().startsWith(base) || !Files.exists(archivo) || Files.isDirectory(archivo)) {
            enviarTexto(exchange, 404, "text/plain; charset=UTF-8", "No encontrado");
            return;
        }
        String tipoMime = obtenerMime(archivo.getFileName().toString());
        byte[] bytes = Files.readAllBytes(archivo);
        exchange.getResponseHeaders().set("Content-Type", tipoMime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void manejarConectar(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            String cuerpo = leerCuerpo(exchange);
            String host = extraerCampoJson(cuerpo, "host");
            String puerto = extraerCampoJson(cuerpo, "puerto");
            String ruta = extraerCampoJson(cuerpo, "ruta");
            String usuario = extraerCampoJson(cuerpo, "usuario");
            String clave = extraerCampoJson(cuerpo, "clave");

            Perfil perfil = new Perfil("Web", host, puerto.isEmpty() ? "3050" : puerto, ruta, usuario, clave);
            Connection nuevaConexion = ConexionBD.conectar(perfil);
            if (nuevaConexion == null) {
                throw new Exception("El driver devolvio conexion nula. Revisa la URL y credenciales.");
            }

            if (conexionActiva != null && !conexionActiva.isClosed()) {
                conexionActiva.close();
            }
            conexionActiva = nuevaConexion;
            catalogo = new Catalogo(conexionActiva);
            generadorDDL = new GeneradorDDL(conexionActiva);
            ejecutorSQL = new EjecutorSQL(conexionActiva);

            enviarJson(exchange, 200, "{\"ok\":true,\"mensaje\":\"Conectado a " + escaparJson(perfil.getUrl()) + "\"}");
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(ex.getMessage()) + "\"}");
        }
    }

    private static void manejarCatalogo(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (catalogo == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexion activa\"}");
            return;
        }
        try {
            StringBuilder json = new StringBuilder("{\"ok\":true,");
            json.append("\"tablas\":").append(listaAJson(catalogo.getTablas())).append(",");
            json.append("\"vistas\":").append(listaAJson(catalogo.getVistas())).append(",");
            json.append("\"procedimientos\":").append(listaAJson(catalogo.getProcedimientos())).append(",");
            json.append("\"funciones\":").append(listaAJson(catalogo.getFunciones())).append(",");
            json.append("\"generadores\":").append(listaAJson(catalogo.getGeneradores())).append(",");
            json.append("\"triggers\":").append(listaAJson(catalogo.getTriggers())).append(",");
            json.append("\"indices\":").append(listaAJson(catalogo.getIndices())).append(",");
            json.append("\"usuarios\":").append(listaAJson(catalogo.getUsuarios()));
            json.append("}");
            enviarJson(exchange, 200, json.toString());
        } catch (Exception ex) {
            enviarJson(exchange, 500, "{\"ok\":false,\"error\":\"" + escaparJson(ex.getMessage()) + "\"}");
        }
    }

    private static void manejarDdl(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (generadorDDL == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexion activa\"}");
            return;
        }
        Map<String, String> params = parsearQuery(exchange.getRequestURI().getRawQuery());
        String tipo = params.getOrDefault("tipo", "");
        String nombre = params.getOrDefault("nombre", "");

        try {
            String ddl;
            switch (tipo) {
                case "tablas":
                    ddl = generadorDDL.getDDLTabla(nombre);
                    break;
                case "vistas":
                    ddl = generadorDDL.getDDLVista(nombre);
                    break;
                case "procedimientos":
                    ddl = generadorDDL.getDDLProcedimiento(nombre);
                    break;
                case "funciones":
                    ddl = generadorDDL.getDDLFuncion(nombre);
                    break;
                case "generadores":
                    ddl = generadorDDL.getDDLGenerador(nombre);
                    break;
                case "triggers":
                    ddl = generadorDDL.getDDLTrigger(nombre);
                    break;
                case "indices":
                    ddl = generadorDDL.getDDLIndice(nombre);
                    break;
                case "usuarios":
                    ddl = "SELECT * FROM RDB$USER_PRIVILEGES WHERE RDB$USER = '" + nombre + "';";
                    break;
                case "paquetes":
                    ddl = "-- JUSTIFICACION TECNICA (PAQUETES):\n" +
                          "-- InterBase no implementa paquetes estilo Oracle (CREATE PACKAGE).\n" +
                          "-- Procedimientos y funciones se almacenan de forma independiente en RDB$PROCEDURES y RDB$FUNCTIONS.";
                    break;
                case "tablespaces":
                    ddl = "-- JUSTIFICACION TECNICA (TABLESPACES):\n" +
                          "-- En arquitectura estandar de InterBase el almacenamiento fisico se gestiona mediante archivos\n" +
                          "-- secundarios registrados en RDB$FILES (ALTER DATABASE ADD FILE) en lugar de tablespaces logicos.";
                    break;
                default:
                    ddl = "";
            }
            enviarJson(exchange, 200, "{\"ok\":true,\"ddl\":\"" + escaparJson(ddl) + "\"}");
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(ex.getMessage()) + "\"}");
        }
    }

    private static void manejarEjecutar(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (ejecutorSQL == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexion activa\"}");
            return;
        }
        try {
            String cuerpo = leerCuerpo(exchange);
            String sql = extraerCampoJson(cuerpo, "sql");
            boolean esScript = "true".equalsIgnoreCase(extraerBooleanoJson(cuerpo, "esScript"));

            if (esScript) {
                String mensaje = ejecutorSQL.ejecutarScript(sql);
                enviarJson(exchange, 200, "{\"ok\":true,\"esConsulta\":false,\"mensaje\":\"" + escaparJson(mensaje) + "\",\"columnas\":[],\"filas\":[]}");
            } else {
                ResultadoConsulta res = ejecutorSQL.ejecutar(sql);
                StringBuilder json = new StringBuilder("{");
                json.append("\"ok\":true,");
                json.append("\"esConsulta\":").append(res.esConsulta).append(",");
                json.append("\"mensaje\":\"").append(escaparJson(res.mensaje)).append("\",");
                json.append("\"columnas\":").append(listaAJson(res.columnas)).append(",");
                json.append("\"filas\":").append(filasAJson(res.columnas, res.filas));
                json.append("}");
                enviarJson(exchange, 200, json.toString());
            }
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(ex.getMessage()) + "\"}");
        }
    }

    private static String leerCuerpo(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void enviarJson(HttpExchange exchange, int codigo, String json) throws IOException {
        enviarTexto(exchange, codigo, "application/json; charset=UTF-8", json);
    }

    private static void enviarTexto(HttpExchange exchange, int codigo, String mime, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String obtenerMime(String nombre) {
        if (nombre.endsWith(".html")) return "text/html; charset=UTF-8";
        if (nombre.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (nombre.endsWith(".css")) return "text/css; charset=UTF-8";
        return "application/octet-stream";
    }

    private static Map<String, String> parsearQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String par : rawQuery.split("&")) {
            String[] kv = par.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
        return map;
    }

    private static String extraerCampoJson(String json, String clave) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) return "";
        return desescaparJson(m.group(1));
    }

    private static String extraerBooleanoJson(String json, String clave) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "false";
    }

    private static String desescaparJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String escaparJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String listaAJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escaparJson(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String filasAJson(List<String> columnas, List<Object[]> filas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filas.size(); i++) {
            if (i > 0) sb.append(",");
            Object[] fila = filas.get(i);
            sb.append("{");
            for (int j = 0; j < columnas.size(); j++) {
                if (j > 0) sb.append(",");
                String col = columnas.get(j);
                Object val = fila[j];
                sb.append("\"").append(escaparJson(col)).append("\":");
                if (val == null) {
                    sb.append("null");
                } else if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else {
                    sb.append("\"").append(escaparJson(String.valueOf(val))).append("\"");
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}