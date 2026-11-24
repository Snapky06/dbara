package bd;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EjecutorSQL {

    private final Connection connection;

    public static class ResultadoConsulta {
        public final boolean esConsulta;
        public final String mensaje;
        public final List<String> columnas;
        public final List<Object[]> filas;

        public ResultadoConsulta(
            boolean esConsulta,
            String mensaje,
            List<String> columnas,
            List<Object[]> filas
        ) {
            this.esConsulta = esConsulta;
            this.mensaje = mensaje;
            this.columnas = columnas;
            this.filas = filas;
        }
    }

    public EjecutorSQL(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexion no puede ser nula.");
        }
        this.connection = connection;
    }

    public ResultadoConsulta ejecutar(String sql) throws SQLException {
        List<String> sentencias = dividirSentencias(sql);
        if (sentencias.isEmpty()) {
            throw new SQLException("La consulta SQL esta vacia.");
        }

        ResultadoConsulta ultimoResultado = null;
        int ejecutadas = 0;

        try (Statement statement = connection.createStatement()) {
            for (String sentencia : sentencias) {
                boolean tieneResultSet = statement.execute(sentencia);
                ejecutadas++;
                if (tieneResultSet) {
                    try (ResultSet rs = statement.getResultSet()) {
                        ultimoResultado = extraerResultSet(rs);
                    }
                } else {
                    int afectadas = statement.getUpdateCount();
                    String msg = afectadas >= 0
                        ? "Sentencia ejecutada. Filas afectadas: " + afectadas
                        : "Sentencia ejecutada correctamente.";
                    ultimoResultado = new ResultadoConsulta(false, msg, List.of(), List.of());
                }
            }
        }

        if (sentencias.size() > 1 && !ultimoResultado.esConsulta) {
            return new ResultadoConsulta(false, "Sentencias ejecutadas: " + ejecutadas, List.of(), List.of());
        }
        return ultimoResultado;
    }

    public String ejecutarScript(String script) throws SQLException {
        List<String> sentencias = dividirSentencias(script);
        if (sentencias.isEmpty()) {
            throw new SQLException("El script SQL esta vacio.");
        }

        int ejecutadas = 0;
        try (Statement statement = connection.createStatement()) {
            for (String sentencia : sentencias) {
                statement.execute(sentencia);
                ejecutadas++;
            }
        }
        return "Script completado. Sentencias ejecutadas: " + ejecutadas;
    }

    private ResultadoConsulta extraerResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int totalColumnas = meta.getColumnCount();
        List<String> columnas = new ArrayList<>(totalColumnas);
        Set<String> usadas = new HashSet<>();

        for (int i = 1; i <= totalColumnas; i++) {
            String base = meta.getColumnLabel(i);
            if (base == null || base.isBlank()) {
                base = meta.getColumnName(i);
            }
            base = (base == null || base.isBlank()) ? "COL_" + i : base.trim();

            String nombre = base;
            int sufijo = 2;
            while (!usadas.add(nombre)) {
                nombre = base + "_" + sufijo++;
            }
            columnas.add(nombre);
        }

        List<Object[]> filas = new ArrayList<>();
        while (rs.next()) {
            Object[] fila = new Object[totalColumnas];
            for (int i = 1; i <= totalColumnas; i++) {
                Object valor = rs.getObject(i);
                if (valor instanceof String) {
                    valor = ((String) valor).trim();
                } else if (valor instanceof byte[]) {
                    valor = new String((byte[]) valor, StandardCharsets.UTF_8).trim();
                }
                fila[i - 1] = valor;
            }
            filas.add(fila);
        }

        return new ResultadoConsulta(true, "Filas devueltas: " + filas.size(), columnas, filas);
    }

    private List<String> dividirSentencias(String script) {
        List<String> sentencias = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return sentencias;
        }

        StringBuilder actual = new StringBuilder();
        String delimitador = ";";
        int profundidad = 0;
        boolean enSimple = false;
        boolean enDoble = false;
        boolean enLinea = false;
        boolean enBloque = false;
        int n = script.length();

        for (int i = 0; i < n; i++) {
            char c = script.charAt(i);
            char sig = (i + 1 < n) ? script.charAt(i + 1) : '\0';

            if (enLinea) {
                actual.append(c);
                if (c == '\n') {
                    enLinea = false;
                }
                continue;
            }

            if (enBloque) {
                actual.append(c);
                if (c == '*' && sig == '/') {
                    actual.append(sig);
                    i++;
                    enBloque = false;
                }
                continue;
            }

            if (enSimple) {
                actual.append(c);
                if (c == '\'') {
                    if (sig == '\'') {
                        actual.append(sig);
                        i++;
                    } else {
                        enSimple = false;
                    }
                }
                continue;
            }

            if (enDoble) {
                actual.append(c);
                if (c == '"') {
                    if (sig == '"') {
                        actual.append(sig);
                        i++;
                    } else {
                        enDoble = false;
                    }
                }
                continue;
            }

            if (c == '-' && sig == '-') {
                actual.append(c).append(sig);
                i++;
                enLinea = true;
                continue;
            }

            if (c == '/' && sig == '*') {
                actual.append(c).append(sig);
                i++;
                enBloque = true;
                continue;
            }

            if (c == '\'') {
                enSimple = true;
                actual.append(c);
                continue;
            }

            if (c == '"') {
                enDoble = true;
                actual.append(c);
                continue;
            }

            if (delimitador.equals(";") && esCaracterPalabra(c)) {
                int inicio = i;
                while (i + 1 < n && esCaracterPalabra(script.charAt(i + 1))) {
                    i++;
                }
                String palabra = script.substring(inicio, i + 1);
                actual.append(palabra);
                String upper = palabra.toUpperCase();
                if (upper.equals("BEGIN") || upper.equals("CASE")) {
                    profundidad++;
                } else if (upper.equals("END") && profundidad > 0) {
                    profundidad--;
                }
                continue;
            }

            if ((profundidad == 0 || !delimitador.equals(";")) && script.startsWith(delimitador, i)) {
                int len = delimitador.length();
                delimitador = procesarBloque(actual.toString(), delimitador, sentencias);
                actual.setLength(0);
                profundidad = 0;
                i += len - 1;
                continue;
            }

            actual.append(c);
        }

        procesarBloque(actual.toString(), delimitador, sentencias);
        return sentencias;
    }

    private String procesarBloque(String texto, String delimitadorActual, List<String> destino) {
        String sinCom = quitarComentarios(texto).trim();
        if (sinCom.isEmpty()) {
            return delimitadorActual;
        }
        String normalizada = sinCom.replaceAll("\\s+", " ");
        if (normalizada.toUpperCase().startsWith("SET TERM ")) {
            String nuevo = normalizada.substring(9).trim();
            return nuevo.isEmpty() ? delimitadorActual : nuevo;
        }
        destino.add(texto.trim());
        return delimitadorActual;
    }

    private String quitarComentarios(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("(?m)--[^\\n]*", " ");
    }

    private boolean esCaracterPalabra(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}