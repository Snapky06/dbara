package bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Catalogo {

    private final Connection con;

    public Catalogo(Connection con) {
        this.con = con;
    }

    public List<String> getTablas() throws SQLException {
        String sql = "SELECT RDB$RELATION_NAME FROM RDB$RELATIONS " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "AND RDB$VIEW_BLR IS NULL ORDER BY RDB$RELATION_NAME";
        return listar(sql);
    }

    public List<String> getVistas() throws SQLException {
        String sql = "SELECT RDB$RELATION_NAME FROM RDB$RELATIONS " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "AND RDB$VIEW_BLR IS NOT NULL ORDER BY RDB$RELATION_NAME";
        return listar(sql);
    }

    public List<String> getProcedimientos() throws SQLException {
        String sql = "SELECT RDB$PROCEDURE_NAME FROM RDB$PROCEDURES " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "ORDER BY RDB$PROCEDURE_NAME";
        return listar(sql);
    }

    public List<String> getFunciones() throws SQLException {
        String sql = "SELECT RDB$FUNCTION_NAME FROM RDB$FUNCTIONS " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "ORDER BY RDB$FUNCTION_NAME";
        return listar(sql);
    }

    public List<String> getGeneradores() throws SQLException {
        String sql = "SELECT RDB$GENERATOR_NAME FROM RDB$GENERATORS " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "ORDER BY RDB$GENERATOR_NAME";
        return listar(sql);
    }

    public List<String> getTriggers() throws SQLException {
        String sql = "SELECT RDB$TRIGGER_NAME FROM RDB$TRIGGERS " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "ORDER BY RDB$TRIGGER_NAME";
        return listar(sql);
    }

    public List<String> getIndices() throws SQLException {
        String sql = "SELECT RDB$INDEX_NAME FROM RDB$INDICES " +
                     "WHERE (RDB$SYSTEM_FLAG = 0 OR RDB$SYSTEM_FLAG IS NULL) " +
                     "ORDER BY RDB$INDEX_NAME";
        return listar(sql);
    }

    public List<String> getUsuarios() throws SQLException {
        String sql = "SELECT DISTINCT RDB$USER FROM RDB$USER_PRIVILEGES " +
                     "WHERE RDB$USER_TYPE = 8 ORDER BY RDB$USER";
        return listar(sql);
    }

    private List<String> listar(String sql) throws SQLException {
        List<String> res = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String dato = rs.getString(1);
                if (dato != null) {
                    res.add(dato.trim());
                }
            }
        }
        return res;
    }
}