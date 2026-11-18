package bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GeneradorDDL {

    private final Connection con;

    public GeneradorDDL(Connection con) {
        this.con = con;
    }

    public String getDDLTabla(String tabla) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tabla).append(" (\n");

        List<String> lineas = new ArrayList<>();

        String sqlCols = "SELECT rf.RDB$FIELD_NAME, f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, " +
                         "f.RDB$FIELD_LENGTH, f.RDB$FIELD_SCALE, f.RDB$FIELD_PRECISION, " +
                         "rf.RDB$NULL_FLAG, rf.RDB$DEFAULT_SOURCE " +
                         "FROM RDB$RELATION_FIELDS rf " +
                         "JOIN RDB$FIELDS f ON rf.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME " +
                         "WHERE TRIM(rf.RDB$RELATION_NAME) = ? " +
                         "ORDER BY rf.RDB$FIELD_POSITION";

        try (PreparedStatement ps = con.prepareStatement(sqlCols)) {
            ps.setString(1, tabla.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString(1).trim();
                    int tipo = rs.getInt(2);
                    int subtipo = rs.getInt(3);
                    int largo = rs.getInt(4);
                    int escala = rs.getInt(5);
                    int prec = rs.getInt(6);
                    int nulo = rs.getInt(7);
                    String def = rs.getString(8);

                    StringBuilder linea = new StringBuilder("    ");
                    linea.append(col).append(" ").append(traducirTipo(tipo, subtipo, largo, escala, prec));

                    if (def != null && !def.trim().isEmpty()) {
                        linea.append(" ").append(def.trim());
                    }
                    if (nulo == 1) {
                        linea.append(" NOT NULL");
                    }
                    lineas.add(linea.toString());
                }
            }
        }

        String pk = getPK(tabla);
        if (!pk.isEmpty()) {
            lineas.add("    " + pk);
        }

        lineas.addAll(getFKs(tabla));

        sb.append(String.join(",\n", lineas));
        sb.append("\n);");
        return sb.toString();
    }

    private String getPK(String tabla) throws SQLException {
        String sql = "SELECT rc.RDB$CONSTRAINT_NAME, iseg.RDB$FIELD_NAME " +
                     "FROM RDB$RELATION_CONSTRAINTS rc " +
                     "JOIN RDB$INDEX_SEGMENTS iseg ON rc.RDB$INDEX_NAME = iseg.RDB$INDEX_NAME " +
                     "WHERE TRIM(rc.RDB$RELATION_NAME) = ? AND rc.RDB$CONSTRAINT_TYPE = 'PRIMARY KEY' " +
                     "ORDER BY iseg.RDB$FIELD_POSITION";

        List<String> cols = new ArrayList<>();
        String nombrePk = "";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tabla.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nombrePk = rs.getString(1).trim();
                    cols.add(rs.getString(2).trim());
                }
            }
        }

        if (cols.isEmpty()) {
            return "";
        }
        return "CONSTRAINT " + nombrePk + " PRIMARY KEY (" + String.join(", ", cols) + ")";
    }

    private List<String> getFKs(String tabla) throws SQLException {
        List<String> fks = new ArrayList<>();
        String sql = "SELECT rc.RDB$CONSTRAINT_NAME, iseg.RDB$FIELD_NAME, " +
                     "rc2.RDB$RELATION_NAME, iseg2.RDB$FIELD_NAME " +
                     "FROM RDB$RELATION_CONSTRAINTS rc " +
                     "JOIN RDB$REF_CONSTRAINTS ref ON rc.RDB$CONSTRAINT_NAME = ref.RDB$CONSTRAINT_NAME " +
                     "JOIN RDB$RELATION_CONSTRAINTS rc2 ON ref.RDB$CONST_NAME_UQ = rc2.RDB$CONSTRAINT_NAME " +
                     "JOIN RDB$INDEX_SEGMENTS iseg ON rc.RDB$INDEX_NAME = iseg.RDB$INDEX_NAME " +
                     "JOIN RDB$INDEX_SEGMENTS iseg2 ON rc2.RDB$INDEX_NAME = iseg2.RDB$INDEX_NAME " +
                     "AND iseg.RDB$FIELD_POSITION = iseg2.RDB$FIELD_POSITION " +
                     "WHERE TRIM(rc.RDB$RELATION_NAME) = ? AND rc.RDB$CONSTRAINT_TYPE = 'FOREIGN KEY'";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tabla.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString(1).trim();
                    String colLocal = rs.getString(2).trim();
                    String tablaRef = rs.getString(3).trim();
                    String colRef = rs.getString(4).trim();
                    fks.add("    CONSTRAINT " + nom + " FOREIGN KEY (" + colLocal + ") REFERENCES " + tablaRef + " (" + colRef + ")");
                }
            }
        }
        return fks;
    }

    public String getDDLVista(String vista) throws SQLException {
        String sql = "SELECT RDB$VIEW_SOURCE FROM RDB$RELATIONS WHERE TRIM(RDB$RELATION_NAME) = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, vista.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String src = rs.getString(1);
                    return "CREATE VIEW " + vista + " AS\n" + (src != null ? src.trim() : "") + ";";
                }
            }
        }
        return "";
    }

    public String getDDLTrigger(String trigger) throws SQLException {
        String sql = "SELECT RDB$RELATION_NAME, RDB$TRIGGER_TYPE, RDB$TRIGGER_INACTIVE, RDB$TRIGGER_SOURCE " +
                     "FROM RDB$TRIGGERS WHERE TRIM(RDB$TRIGGER_NAME) = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, trigger.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String tabla = rs.getString(1).trim();
                    int tipo = rs.getInt(2);
                    int inactivo = rs.getInt(3);
                    String src = rs.getString(4);

                    String estado = (inactivo == 1) ? "INACTIVE" : "ACTIVE";
                    String evento = traducirEventoTrigger(tipo);

                    return "CREATE TRIGGER " + trigger + " FOR " + tabla + "\n" +
                           estado + " " + evento + "\n" +
                           (src != null ? src.trim() : "");
                }
            }
        }
        return "";
    }

    public String getDDLProcedimiento(String proc) throws SQLException {
        String sql = "SELECT RDB$PROCEDURE_SOURCE FROM RDB$PROCEDURES WHERE TRIM(RDB$PROCEDURE_NAME) = ?";
        String cuerpo = "";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, proc.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cuerpo = rs.getString(1);
                }
            }
        }

        List<String> entradas = new ArrayList<>();
        List<String> salidas = new ArrayList<>();

        String sqlParams = "SELECT pp.RDB$PARAMETER_NAME, pp.RDB$PARAMETER_TYPE, " +
                           "f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH, f.RDB$FIELD_SCALE, f.RDB$FIELD_PRECISION " +
                           "FROM RDB$PROCEDURE_PARAMETERS pp " +
                           "JOIN RDB$FIELDS f ON pp.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME " +
                           "WHERE TRIM(pp.RDB$PROCEDURE_NAME) = ? ORDER BY pp.RDB$PARAMETER_NUMBER";

        try (PreparedStatement ps = con.prepareStatement(sqlParams)) {
            ps.setString(1, proc.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString(1).trim();
                    int dir = rs.getInt(2);
                    String tipo = traducirTipo(rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7));
                    if (dir == 0) {
                        entradas.add(nom + " " + tipo);
                    } else {
                        salidas.add(nom + " " + tipo);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(proc);
        if (!entradas.isEmpty()) {
            sb.append(" (\n    ").append(String.join(",\n    ", entradas)).append("\n)");
        }
        if (!salidas.isEmpty()) {
            sb.append("\nRETURNS (\n    ").append(String.join(",\n    ", salidas)).append("\n)");
        }
        sb.append("\nAS\n").append(cuerpo != null ? cuerpo.trim() : "");
        return sb.toString();
    }

    public String getDDLGenerador(String gen) throws SQLException {
        long val = 0;
        String sql = "SELECT GEN_ID(" + gen + ", 0) FROM RDB$DATABASE";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                val = rs.getLong(1);
            }
        }
        return "CREATE GENERATOR " + gen + ";\nSET GENERATOR " + gen + " TO " + val + ";";
    }

    public String getDDLIndice(String indice) throws SQLException {
        String sql = "SELECT i.RDB$RELATION_NAME, i.RDB$UNIQUE_FLAG, i.RDB$INDEX_TYPE, s.RDB$FIELD_NAME " +
                     "FROM RDB$INDICES i " +
                     "JOIN RDB$INDEX_SEGMENTS s ON i.RDB$INDEX_NAME = s.RDB$INDEX_NAME " +
                     "WHERE TRIM(i.RDB$INDEX_NAME) = ? ORDER BY s.RDB$FIELD_POSITION";

        String tabla = "";
        boolean unico = false;
        boolean desc = false;
        List<String> cols = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, indice.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tabla = rs.getString(1).trim();
                    unico = rs.getInt(2) == 1;
                    desc = rs.getInt(3) == 1;
                    cols.add(rs.getString(4).trim());
                }
            }
        }

        if (cols.isEmpty()) {
            return "";
        }

        return "CREATE " + (unico ? "UNIQUE " : "") + (desc ? "DESCENDING " : "") +
               "INDEX " + indice + " ON " + tabla + " (" + String.join(", ", cols) + ");";
    }

    public String getDDLFuncion(String func) throws SQLException {
        String sql = "SELECT RDB$MODULE_NAME, RDB$ENTRYPOINT FROM RDB$FUNCTIONS WHERE TRIM(RDB$FUNCTION_NAME) = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, func.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String mod = rs.getString(1) != null ? rs.getString(1).trim() : "";
                    String entry = rs.getString(2) != null ? rs.getString(2).trim() : "";
                    return "DECLARE EXTERNAL FUNCTION " + func + "\nENTRY_POINT '" + entry + "'\nMODULE_NAME '" + mod + "';";
                }
            }
        }
        return "";
    }

    private String traducirTipo(int tipo, int subtipo, int largo, int escala, int prec) {
        int escPos = Math.abs(escala);
        switch (tipo) {
            case 7:
                if (subtipo == 1) return "NUMERIC(" + prec + "," + escPos + ")";
                if (subtipo == 2) return "DECIMAL(" + prec + "," + escPos + ")";
                return "SMALLINT";
            case 8:
                if (subtipo == 1) return "NUMERIC(" + prec + "," + escPos + ")";
                if (subtipo == 2) return "DECIMAL(" + prec + "," + escPos + ")";
                return "INTEGER";
            case 10:
                return "FLOAT";
            case 12:
                return "DATE";
            case 13:
                return "TIME";
            case 14:
                return "CHAR(" + largo + ")";
            case 16:
                if (subtipo == 1) return "NUMERIC(" + prec + "," + escPos + ")";
                if (subtipo == 2) return "DECIMAL(" + prec + "," + escPos + ")";
                return "BIGINT";
            case 27:
                return "DOUBLE PRECISION";
            case 35:
                return "TIMESTAMP";
            case 37:
                return "VARCHAR(" + largo + ")";
            case 261:
                return "BLOB SUB_TYPE " + subtipo;
            default:
                return "VARCHAR(" + largo + ")";
        }
    }

    private String traducirEventoTrigger(int t) {
        switch (t) {
            case 1: return "BEFORE INSERT";
            case 2: return "AFTER INSERT";
            case 3: return "BEFORE UPDATE";
            case 4: return "AFTER UPDATE";
            case 5: return "BEFORE DELETE";
            case 6: return "AFTER DELETE";
            default: return "BEFORE INSERT";
        }
    }
}