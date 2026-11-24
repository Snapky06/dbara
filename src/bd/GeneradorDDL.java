package bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GeneradorDDL {

    private final Connection connection;

    public GeneradorDDL(Connection connection) {
        this.connection = connection;
    }

    public String getDDLTabla(String nombreTabla) throws SQLException {
        String tabla = limpiar(nombreTabla);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(tabla).append(" (\n");

        List<String> definiciones = new ArrayList<>();

        String sqlCols =
            "SELECT rf.RDB$FIELD_NAME, rf.RDB$NULL_FLAG, rf.RDB$DEFAULT_SOURCE AS COL_DEFAULT, " +
            "f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH, " +
            "f.RDB$CHARACTER_LENGTH, f.RDB$FIELD_PRECISION, f.RDB$FIELD_SCALE, " +
            "f.RDB$DEFAULT_SOURCE AS DOM_DEFAULT " +
            "FROM RDB$RELATION_FIELDS rf " +
            "JOIN RDB$FIELDS f ON rf.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME " +
            "WHERE rf.RDB$RELATION_NAME = ? " +
            "ORDER BY rf.RDB$FIELD_POSITION";

        try (PreparedStatement ps = connection.prepareStatement(sqlCols)) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colNombre = limpiar(rs.getString("RDB$FIELD_NAME"));
                    int tipo = rs.getInt("RDB$FIELD_TYPE");
                    int subTipo = obtenerInt(rs, "RDB$FIELD_SUB_TYPE");
                    int longitud = obtenerInt(rs, "RDB$FIELD_LENGTH");
                    int charLen = obtenerInt(rs, "RDB$CHARACTER_LENGTH");
                    int precision = obtenerInt(rs, "RDB$FIELD_PRECISION");
                    int escala = obtenerInt(rs, "RDB$FIELD_SCALE");
                    int nullFlag = obtenerInt(rs, "RDB$NULL_FLAG");

                    String defCol = limpiar(rs.getString("COL_DEFAULT"));
                    String defDom = limpiar(rs.getString("DOM_DEFAULT"));
                    String valorDefault = !defCol.isEmpty() ? defCol : defDom;

                    StringBuilder linea = new StringBuilder();
                    linea.append("    ").append(colNombre).append(" ")
                         .append(mapearTipo(tipo, subTipo, longitud, charLen, precision, escala));

                    if (!valorDefault.isEmpty()) {
                        if (valorDefault.toUpperCase().startsWith("DEFAULT")) {
                            linea.append(" ").append(valorDefault);
                        } else {
                            linea.append(" DEFAULT ").append(valorDefault);
                        }
                    }

                    if (nullFlag == 1) {
                        linea.append(" NOT NULL");
                    }
                    definiciones.add(linea.toString());
                }
            }
        }

        String sqlConstraints =
            "SELECT RDB$CONSTRAINT_NAME, RDB$CONSTRAINT_TYPE, RDB$INDEX_NAME " +
            "FROM RDB$RELATION_CONSTRAINTS " +
            "WHERE RDB$RELATION_NAME = ? " +
            "ORDER BY RDB$CONSTRAINT_TYPE, RDB$CONSTRAINT_NAME";

        try (PreparedStatement ps = connection.prepareStatement(sqlConstraints)) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cNombre = limpiar(rs.getString("RDB$CONSTRAINT_NAME"));
                    String cTipo = limpiar(rs.getString("RDB$CONSTRAINT_TYPE"));
                    String idxNombre = limpiar(rs.getString("RDB$INDEX_NAME"));

                    if ("PRIMARY KEY".equalsIgnoreCase(cTipo)) {
                        List<String> cols = obtenerColumnasIndice(idxNombre);
                        if (!cols.isEmpty()) {
                            definiciones.add("    CONSTRAINT " + cNombre + " PRIMARY KEY (" + String.join(", ", cols) + ")");
                        }
                    } else if ("UNIQUE".equalsIgnoreCase(cTipo)) {
                        List<String> cols = obtenerColumnasIndice(idxNombre);
                        if (!cols.isEmpty()) {
                            definiciones.add("    CONSTRAINT " + cNombre + " UNIQUE (" + String.join(", ", cols) + ")");
                        }
                    } else if ("FOREIGN KEY".equalsIgnoreCase(cTipo)) {
                        String fk = construirForeignKey(cNombre, idxNombre);
                        if (!fk.isEmpty()) {
                            definiciones.add("    " + fk);
                        }
                    } else if ("CHECK".equalsIgnoreCase(cTipo)) {
                        String chk = obtenerFuenteCheck(cNombre);
                        if (!chk.isEmpty()) {
                            definiciones.add("    CONSTRAINT " + cNombre + " " + chk);
                        }
                    }
                }
            }
        }

        if (definiciones.isEmpty()) {
            return "-- No se encontraron columnas para la tabla " + tabla;
        }

        ddl.append(String.join(",\n", definiciones));
        ddl.append("\n);");
        return ddl.toString();
    }

    public String getDDLVista(String nombreVista) throws SQLException {
        String vista = limpiar(nombreVista);
        String fuente = "";

        String sql = "SELECT RDB$VIEW_SOURCE FROM RDB$RELATIONS WHERE RDB$RELATION_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, vista);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    fuente = limpiar(rs.getString("RDB$VIEW_SOURCE"));
                }
            }
        }

        List<String> columnas = new ArrayList<>();
        String sqlCols =
            "SELECT RDB$FIELD_NAME FROM RDB$RELATION_FIELDS " +
            "WHERE RDB$RELATION_NAME = ? ORDER BY RDB$FIELD_POSITION";
        try (PreparedStatement ps = connection.prepareStatement(sqlCols)) {
            ps.setString(1, vista);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columnas.add(limpiar(rs.getString("RDB$FIELD_NAME")));
                }
            }
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE VIEW ").append(vista);
        if (!columnas.isEmpty()) {
            ddl.append(" (").append(String.join(", ", columnas)).append(")");
        }
        ddl.append(" AS\n").append(fuente);
        if (!fuente.endsWith(";")) {
            ddl.append(";");
        }
        return ddl.toString();
    }

    public String getDDLProcedimiento(String nombreProc) throws SQLException {
        String proc = limpiar(nombreProc);
        String fuente = "";

        String sql = "SELECT RDB$PROCEDURE_SOURCE FROM RDB$PROCEDURES WHERE RDB$PROCEDURE_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, proc);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    fuente = limpiar(rs.getString("RDB$PROCEDURE_SOURCE"));
                }
            }
        }

        List<String> entradas = new ArrayList<>();
        List<String> salidas = new ArrayList<>();

        String sqlParams =
            "SELECT pp.RDB$PARAMETER_NAME, pp.RDB$PARAMETER_TYPE, " +
            "f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH, " +
            "f.RDB$CHARACTER_LENGTH, f.RDB$FIELD_PRECISION, f.RDB$FIELD_SCALE " +
            "FROM RDB$PROCEDURE_PARAMETERS pp " +
            "JOIN RDB$FIELDS f ON pp.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME " +
            "WHERE pp.RDB$PROCEDURE_NAME = ? " +
            "ORDER BY pp.RDB$PARAMETER_TYPE, pp.RDB$PARAMETER_NUMBER";

        try (PreparedStatement ps = connection.prepareStatement(sqlParams)) {
            ps.setString(1, proc);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pNombre = limpiar(rs.getString("RDB$PARAMETER_NAME"));
                    int pTipo = rs.getInt("RDB$PARAMETER_TYPE");
                    String tipoSql = mapearTipo(
                        rs.getInt("RDB$FIELD_TYPE"),
                        obtenerInt(rs, "RDB$FIELD_SUB_TYPE"),
                        obtenerInt(rs, "RDB$FIELD_LENGTH"),
                        obtenerInt(rs, "RDB$CHARACTER_LENGTH"),
                        obtenerInt(rs, "RDB$FIELD_PRECISION"),
                        obtenerInt(rs, "RDB$FIELD_SCALE")
                    );
                    String definicion = pNombre + " " + tipoSql;
                    if (pTipo == 0) {
                        entradas.add(definicion);
                    } else {
                        salidas.add(definicion);
                    }
                }
            }
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE PROCEDURE ").append(proc);
        if (!entradas.isEmpty()) {
            ddl.append(" (\n    ").append(String.join(",\n    ", entradas)).append("\n)");
        }
        if (!salidas.isEmpty()) {
            ddl.append("\nRETURNS (\n    ").append(String.join(",\n    ", salidas)).append("\n)");
        }
        ddl.append("\nAS\n").append(fuente);
        return ddl.toString();
    }

    public String getDDLFuncion(String nombreFunc) throws SQLException {
        String func = limpiar(nombreFunc);
        String modulo = "";
        String puntoEntrada = "";
        int argRetorno = 0;

        String sql =
            "SELECT RDB$MODULE_NAME, RDB$ENTRYPOINT, RDB$RETURN_ARGUMENT " +
            "FROM RDB$FUNCTIONS WHERE RDB$FUNCTION_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, func);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    modulo = limpiar(rs.getString("RDB$MODULE_NAME"));
                    puntoEntrada = limpiar(rs.getString("RDB$ENTRYPOINT"));
                    argRetorno = obtenerInt(rs, "RDB$RETURN_ARGUMENT");
                }
            }
        }

        List<String> args = new ArrayList<>();
        String retorno = "";

        String sqlArgs =
            "SELECT RDB$ARGUMENT_POSITION, RDB$MECHANISM, RDB$FIELD_TYPE, " +
            "RDB$FIELD_SUB_TYPE, RDB$FIELD_LENGTH, RDB$FIELD_PRECISION, RDB$FIELD_SCALE " +
            "FROM RDB$FUNCTION_ARGUMENTS " +
            "WHERE RDB$FUNCTION_NAME = ? " +
            "ORDER BY RDB$ARGUMENT_POSITION";

        try (PreparedStatement ps = connection.prepareStatement(sqlArgs)) {
            ps.setString(1, func);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pos = rs.getInt("RDB$ARGUMENT_POSITION");
                    int mecanismo = obtenerInt(rs, "RDB$MECHANISM");
                    String tipoSql = mapearTipo(
                        rs.getInt("RDB$FIELD_TYPE"),
                        obtenerInt(rs, "RDB$FIELD_SUB_TYPE"),
                        obtenerInt(rs, "RDB$FIELD_LENGTH"),
                        0,
                        obtenerInt(rs, "RDB$FIELD_PRECISION"),
                        obtenerInt(rs, "RDB$FIELD_SCALE")
                    );
                    if (pos == argRetorno) {
                        if (pos == 0) {
                            retorno = tipoSql + (mecanismo == 0 ? " BY VALUE" : "");
                        } else {
                            args.add(tipoSql);
                            retorno = "PARAMETER " + pos;
                        }
                    } else {
                        args.add(tipoSql);
                    }
                }
            }
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("DECLARE EXTERNAL FUNCTION ").append(func);
        if (!args.isEmpty()) {
            ddl.append("\n    ").append(String.join(", ", args));
        }
        if (!retorno.isEmpty()) {
            ddl.append("\nRETURNS ").append(retorno);
        }
        if (!puntoEntrada.isEmpty()) {
            ddl.append("\nENTRY_POINT '").append(puntoEntrada).append("'");
        }
        if (!modulo.isEmpty()) {
            ddl.append(" MODULE_NAME '").append(modulo).append("'");
        }
        ddl.append(";");
        return ddl.toString();
    }

    public String getDDLGenerador(String nombreGen) throws SQLException {
        String gen = limpiar(nombreGen);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE GENERATOR ").append(gen).append(";\n");

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT GEN_ID(" + gen + ", 0) FROM RDB$DATABASE")) {
            if (rs.next()) {
                ddl.append("SET GENERATOR ").append(gen).append(" TO ").append(rs.getLong(1)).append(";");
            }
        }
        return ddl.toString();
    }

    public String getDDLTrigger(String nombreTrigger) throws SQLException {
        String trig = limpiar(nombreTrigger);
        String sql =
            "SELECT RDB$RELATION_NAME, RDB$TRIGGER_SEQUENCE, RDB$TRIGGER_TYPE, " +
            "RDB$TRIGGER_SOURCE, RDB$TRIGGER_INACTIVE " +
            "FROM RDB$TRIGGERS WHERE RDB$TRIGGER_NAME = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, trig);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "-- No se encontró el trigger " + trig;
                }
                String tabla = limpiar(rs.getString("RDB$RELATION_NAME"));
                int secuencia = obtenerInt(rs, "RDB$TRIGGER_SEQUENCE");
                int tipo = obtenerInt(rs, "RDB$TRIGGER_TYPE");
                int inactivo = obtenerInt(rs, "RDB$TRIGGER_INACTIVE");
                String fuente = limpiar(rs.getString("RDB$TRIGGER_SOURCE"));

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE TRIGGER ").append(trig).append(" FOR ").append(tabla).append("\n");
                ddl.append(inactivo == 1 ? "INACTIVE " : "ACTIVE ");
                ddl.append(mapearTipoTrigger(tipo)).append(" POSITION ").append(secuencia).append("\n");
                if (!fuente.toUpperCase().startsWith("AS")) {
                    ddl.append("AS\n");
                }
                ddl.append(fuente);
                return ddl.toString();
            }
        }
    }

    public String getDDLIndice(String nombreIndice) throws SQLException {
        String idx = limpiar(nombreIndice);
        String sql =
            "SELECT RDB$RELATION_NAME, RDB$UNIQUE_FLAG, RDB$INDEX_TYPE, RDB$INDEX_INACTIVE " +
            "FROM RDB$INDICES WHERE RDB$INDEX_NAME = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idx);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "-- No se encontró el índice " + idx;
                }
                String tabla = limpiar(rs.getString("RDB$RELATION_NAME"));
                int unico = obtenerInt(rs, "RDB$UNIQUE_FLAG");
                int tipo = obtenerInt(rs, "RDB$INDEX_TYPE");
                int inactivo = obtenerInt(rs, "RDB$INDEX_INACTIVE");
                List<String> cols = obtenerColumnasIndice(idx);

                StringBuilder ddl = new StringBuilder("CREATE ");
                if (unico == 1) {
                    ddl.append("UNIQUE ");
                }
                if (tipo == 1) {
                    ddl.append("DESCENDING ");
                }
                ddl.append("INDEX ").append(idx).append(" ON ").append(tabla)
                   .append(" (").append(String.join(", ", cols)).append(");");
                if (inactivo == 1) {
                    ddl.append("\nALTER INDEX ").append(idx).append(" INACTIVE;");
                }
                return ddl.toString();
            }
        }
    }

    private List<String> obtenerColumnasIndice(String nombreIndice) throws SQLException {
        List<String> cols = new ArrayList<>();
        if (nombreIndice.isEmpty()) {
            return cols;
        }
        String sql =
            "SELECT RDB$FIELD_NAME FROM RDB$INDEX_SEGMENTS " +
            "WHERE RDB$INDEX_NAME = ? ORDER BY RDB$FIELD_POSITION";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nombreIndice);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(limpiar(rs.getString("RDB$FIELD_NAME")));
                }
            }
        }
        return cols;
    }

    private String construirForeignKey(String cNombre, String idxLocal) throws SQLException {
        List<String> colsLocales = obtenerColumnasIndice(idxLocal);
        String sql =
            "SELECT refc.RDB$UPDATE_RULE, refc.RDB$DELETE_RULE, " +
            "rc2.RDB$RELATION_NAME AS REF_TABLA, rc2.RDB$INDEX_NAME AS REF_INDEX " +
            "FROM RDB$REF_CONSTRAINTS refc " +
            "JOIN RDB$RELATION_CONSTRAINTS rc2 ON refc.RDB$CONST_NAME_UQ = rc2.RDB$CONSTRAINT_NAME " +
            "WHERE refc.RDB$CONSTRAINT_NAME = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cNombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "";
                }
                String refTabla = limpiar(rs.getString("REF_TABLA"));
                String refIndex = limpiar(rs.getString("REF_INDEX"));
                String onUpdate = limpiar(rs.getString("RDB$UPDATE_RULE"));
                String onDelete = limpiar(rs.getString("RDB$DELETE_RULE"));
                List<String> colsRef = obtenerColumnasIndice(refIndex);

                StringBuilder fk = new StringBuilder();
                fk.append("CONSTRAINT ").append(cNombre)
                  .append(" FOREIGN KEY (").append(String.join(", ", colsLocales)).append(")")
                  .append(" REFERENCES ").append(refTabla);
                if (!colsRef.isEmpty()) {
                    fk.append(" (").append(String.join(", ", colsRef)).append(")");
                }
                if (!onUpdate.isEmpty() && !"RESTRICT".equalsIgnoreCase(onUpdate)) {
                    fk.append(" ON UPDATE ").append(onUpdate);
                }
                if (!onDelete.isEmpty() && !"RESTRICT".equalsIgnoreCase(onDelete)) {
                    fk.append(" ON DELETE ").append(onDelete);
                }
                return fk.toString();
            }
        }
    }

    private String obtenerFuenteCheck(String cNombre) throws SQLException {
        String sql =
            "SELECT t.RDB$TRIGGER_SOURCE " +
            "FROM RDB$CHECK_CONSTRAINTS cc " +
            "JOIN RDB$TRIGGERS t ON cc.RDB$TRIGGER_NAME = t.RDB$TRIGGER_NAME " +
            "WHERE cc.RDB$CONSTRAINT_NAME = ? AND t.RDB$TRIGGER_TYPE = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cNombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return limpiar(rs.getString("RDB$TRIGGER_SOURCE"));
                }
            }
        }
        return "";
    }

    private String mapearTipo(int tipo, int subTipo, int longitud, int charLen, int precision, int escala) {
        int escAbs = Math.abs(escala);
        int largoTexto = charLen > 0 ? charLen : longitud;

        switch (tipo) {
            case 7:
                return escala < 0
                    ? (subTipo == 2 ? "DECIMAL" : "NUMERIC") + "(" + (precision > 0 ? precision : 4) + "," + escAbs + ")"
                    : "SMALLINT";
            case 8:
                return escala < 0
                    ? (subTipo == 2 ? "DECIMAL" : "NUMERIC") + "(" + (precision > 0 ? precision : 9) + "," + escAbs + ")"
                    : "INTEGER";
            case 10:
                return "FLOAT";
            case 12:
                return "DATE";
            case 13:
                return "TIME";
            case 14:
                return "CHAR(" + largoTexto + ")";
            case 16:
                if (escala < 0 || subTipo == 1 || subTipo == 2) {
                    return (subTipo == 2 ? "DECIMAL" : "NUMERIC") + "(" + (precision > 0 ? precision : 18) + "," + escAbs + ")";
                }
                return "NUMERIC(18,0)";
            case 17:
                return "BOOLEAN";
            case 27:
                return "DOUBLE PRECISION";
            case 35:
                return "TIMESTAMP";
            case 37:
                return "VARCHAR(" + largoTexto + ")";
            case 261:
                return "BLOB SUB_TYPE " + subTipo;
            default:
                return "VARCHAR(" + Math.max(largoTexto, 50) + ")";
        }
    }

    private String mapearTipoTrigger(int tipo) {
        switch (tipo) {
            case 1: return "BEFORE INSERT";
            case 2: return "AFTER INSERT";
            case 3: return "BEFORE UPDATE";
            case 4: return "AFTER UPDATE";
            case 5: return "BEFORE DELETE";
            case 6: return "AFTER DELETE";
            default: return "BEFORE INSERT";
        }
    }

    private int obtenerInt(ResultSet rs, String columna) throws SQLException {
        Object val = rs.getObject(columna);
        return (val instanceof Number) ? ((Number) val).intValue() : 0;
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}