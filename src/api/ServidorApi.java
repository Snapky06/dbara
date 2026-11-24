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
import java.sql.SQLException;
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
        servidor.createContext("/api/desconectar", ServidorApi::manejarDesconectar);
        servidor.createContext("/api/catalogo", ServidorApi::manejarCatalogo);
        servidor.createContext("/api/ddl", ServidorApi::manejarDdl);
        servidor.createContext("/api/ejecutar", ServidorApi::manejarEjecutar);

        servidor.start();
        System.out.println("DBara Web corriendo en http://localhost:" + puerto);
    }

    private static boolean estaConectado() {
        try {
            return conexionActiva != null && !conexionActiva.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private static void cerrarConexionActual() {
        if (conexionActiva != null) {
            try {
                if (!conexionActiva.isClosed()) {
                    conexionActiva.close();
                }
            } catch (SQLException ignored) {
            }
        }
        conexionActiva = null;
        catalogo = null;
        generadorDDL = null;
        ejecutorSQL = null;
    }

    private static String mensajeError(Exception ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
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
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static synchronized void manejarConectar(HttpExchange exchange) throws IOException {
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

            cerrarConexionActual();
            conexionActiva = nuevaConexion;
            catalogo = new Catalogo(conexionActiva);
            generadorDDL = new GeneradorDDL(conexionActiva);
            ejecutorSQL = new EjecutorSQL(conexionActiva);

            enviarJson(exchange, 200, "{\"ok\":true,\"mensaje\":\"Conectado a " + escaparJson(perfil.getUrl()) + "\"}");
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(mensajeError(ex)) + "\"}");
        }
    }

    private static synchronized void manejarDesconectar(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        cerrarConexionActual();
        enviarJson(exchange, 200, "{\"ok\":true,\"mensaje\":\"Sesión cerrada correctamente.\"}");
    }

    private static synchronized void manejarCatalogo(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (!estaConectado() || catalogo == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexión activa.\"}");
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
            enviarJson(exchange, 500, "{\"ok\":false,\"error\":\"" + escaparJson(mensajeError(ex)) + "\"}");
        }
    }

    private static synchronized void manejarDdl(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (!estaConectado() || generadorDDL == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexión activa.\"}");
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
                    ddl = "SELECT * FROM RDB$USER_PRIVILEGES WHERE RDB$USER = '" + nombre.replace("'", "''") + "';";
                    break;
                default:
                    ddl = "";
            }
            enviarJson(exchange, 200, "{\"ok\":true,\"ddl\":\"" + escaparJson(ddl) + "\"}");
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(mensajeError(ex)) + "\"}");
        }
    }

    private static synchronized void manejarEjecutar(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (!estaConectado() || ejecutorSQL == null) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"No hay conexión activa.\"}");
            return;
        }
        try {
            String cuerpo = leerCuerpo(exchange);
            String sql = extraerCampoJson(cuerpo, "sql");
            boolean esScript = "true".equalsIgnoreCase(extraerBooleanoJson(cuerpo, "esScript"));

            long inicio = System.nanoTime();
            if (esScript) {
                String mensaje = ejecutorSQL.ejecutarScript(sql);
                long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
                enviarJson(exchange, 200,
                    "{\"ok\":true,\"esConsulta\":false,\"duracionMs\":" + duracionMs +
                    ",\"mensaje\":\"" + escaparJson(mensaje) + "\",\"columnas\":[],\"filas\":[]}");
            } else {
                ResultadoConsulta res = ejecutorSQL.ejecutar(sql);
                long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
                StringBuilder json = new StringBuilder("{");
                json.append("\"ok\":true,");
                json.append("\"esConsulta\":").append(res.esConsulta).append(",");
                json.append("\"duracionMs\":").append(duracionMs).append(",");
                json.append("\"mensaje\":\"").append(escaparJson(res.mensaje)).append("\",");
                json.append("\"columnas\":").append(listaAJson(res.columnas)).append(",");
                json.append("\"filas\":").append(filasAJson(res.columnas, res.filas));
                json.append("}");
                enviarJson(exchange, 200, json.toString());
            }
        } catch (Exception ex) {
            enviarJson(exchange, 400, "{\"ok\":false,\"error\":\"" + escaparJson(mensajeError(ex)) + "\"}");
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
        if (nombre.endsWith(".svg")) return "image/svg+xml";
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
        Pattern p = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
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
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char sig = s.charAt(++i);
                switch (sig) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            String hex = s.substring(i + 1, i + 5);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append("\\u");
                            }
                        } else {
                            out.append("\\u");
                        }
                        break;
                    default:
                        out.append(sig);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escaparJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
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
                } else if (val instanceof Boolean) {
                    sb.append(val);
                } else if (val instanceof Double) {
                    double d = (Double) val;
                    sb.append(Double.isFinite(d) ? d : "null");
                } else if (val instanceof Float) {
                    float f = (Float) val;
                    sb.append(Float.isFinite(f) ? f : "null");
                } else if (val instanceof Number) {
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