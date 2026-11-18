package bd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;

public class EjecutorSQL {

    private final Connection con;

    public static class Resultado {
        public boolean esConsulta;
        public DefaultTableModel modelo;
        public String mensaje;
    }

    public EjecutorSQL(Connection con) {
        this.con = con;
    }

    public Resultado ejecutar(String sql) throws SQLException {
        Resultado res = new Resultado();
        String limpio = sql.trim();

        if (limpio.endsWith(";")) {
            limpio = limpio.substring(0, limpio.length() - 1).trim();
        }

        String inicio = limpio.toUpperCase();
        if (inicio.startsWith("SELECT") || inicio.startsWith("WITH")) {
            res.esConsulta = true;
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(limpio)) {
                res.modelo = armarTabla(rs);
                res.mensaje = "Filas devueltas: " + res.modelo.getRowCount();
            }
        } else {
            res.esConsulta = false;
            try (Statement st = con.createStatement()) {
                int filas = st.executeUpdate(limpio);
                res.mensaje = "Sentencia ejecutada. Filas afectadas: " + filas;
            }
        }
        return res;
    }

    public String ejecutarScript(String script) throws SQLException {
        String[] sentencias = script.split(";");
        int ejecutadas = 0;

        try (Statement st = con.createStatement()) {
            for (String s : sentencias) {
                String limpia = s.trim();
                if (!limpia.isEmpty()) {
                    st.execute(limpia);
                    ejecutadas++;
                }
            }
        }
        return "Script completado. Sentencias ejecutadas: " + ejecutadas;
    }

    private DefaultTableModel armarTabla(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int totalCols = meta.getColumnCount();

        Vector<String> columnas = new Vector<>();
        for (int i = 1; i <= totalCols; i++) {
            columnas.add(meta.getColumnLabel(i).trim());
        }

        Vector<Vector<Object>> datos = new Vector<>();
        while (rs.next()) {
            Vector<Object> fila = new Vector<>();
            for (int i = 1; i <= totalCols; i++) {
                Object val = rs.getObject(i);
                if (val instanceof String) {
                    val = ((String) val).trim();
                }
                fila.add(val);
            }
            datos.add(fila);
        }

        return new DefaultTableModel(datos, columnas) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}