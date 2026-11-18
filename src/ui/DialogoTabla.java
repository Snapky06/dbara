package ui;

import bd.EjecutorSQL;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class DialogoTabla extends JDialog {

    private final EjecutorSQL ejecutor;
    private final JTextField txtNombreTabla = new JTextField(20);
    private final DefaultTableModel modelo;
    private final JTable tablaCols;
    private boolean creada = false;

    public DialogoTabla(Frame owner, EjecutorSQL ejecutor) {
        super(owner, "Crear Nueva Tabla", true);
        this.ejecutor = ejecutor;
        setSize(640, 380);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelTop.add(new JLabel("Nombre de la Tabla:"));
        panelTop.add(txtNombreTabla);
        add(panelTop, BorderLayout.NORTH);

        String[] cols = {"Columna", "Tipo", "Tamaño", "PK", "NOT NULL"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 3 || columnIndex == 4) {
                    return Boolean.class;
                }
                return String.class;
            }
        };

        tablaCols = new JTable(modelo);
        String[] tipos = {"INTEGER", "VARCHAR", "CHAR", "SMALLINT", "BIGINT", "NUMERIC", "DECIMAL", "FLOAT", "DOUBLE PRECISION", "DATE", "TIME", "TIMESTAMP", "BLOB SUB_TYPE 1"};
        JComboBox<String> comboTipos = new JComboBox<>(tipos);
        tablaCols.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(comboTipos));

        modelo.addRow(new Object[]{"ID", "INTEGER", "", true, true});

        add(new JScrollPane(tablaCols), BorderLayout.CENTER);

        JPanel panelSur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnAgregar = new JButton("+ Columna");
        JButton btnQuitar = new JButton("- Columna");
        JButton btnCrear = new JButton("Crear Tabla");

        btnAgregar.addActionListener(e -> modelo.addRow(new Object[]{"", "VARCHAR", "50", false, false}));
        btnQuitar.addActionListener(e -> {
            int r = tablaCols.getSelectedRow();
            if (r >= 0) {
                modelo.removeRow(r);
            }
        });
        btnCrear.addActionListener(e -> crear());

        panelSur.add(btnAgregar);
        panelSur.add(btnQuitar);
        panelSur.add(btnCrear);
        add(panelSur, BorderLayout.SOUTH);
    }

    private void crear() {
        if (tablaCols.isEditing()) {
            tablaCols.getCellEditor().stopCellEditing();
        }
        String nombreTabla = txtNombreTabla.getText().trim().toUpperCase();
        if (nombreTabla.isEmpty() || modelo.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Indica el nombre de la tabla y al menos una columna.");
            return;
        }

        List<String> defs = new ArrayList<>();
        List<String> pks = new ArrayList<>();

        for (int i = 0; i < modelo.getRowCount(); i++) {
            String col = modelo.getValueAt(i, 0).toString().trim().toUpperCase();
            String tipo = modelo.getValueAt(i, 1).toString().trim();
            String tam = modelo.getValueAt(i, 2) != null ? modelo.getValueAt(i, 2).toString().trim() : "";
            boolean pk = Boolean.TRUE.equals(modelo.getValueAt(i, 3));
            boolean nn = Boolean.TRUE.equals(modelo.getValueAt(i, 4));

            if (col.isEmpty()) continue;

            StringBuilder sb = new StringBuilder(col + " " + tipo);
            if (!tam.isEmpty() && (tipo.equals("VARCHAR") || tipo.equals("CHAR") || tipo.equals("NUMERIC") || tipo.equals("DECIMAL"))) {
                sb.append("(").append(tam).append(")");
            }
            if (nn || pk) {
                sb.append(" NOT NULL");
            }
            defs.add(sb.toString());

            if (pk) {
                pks.add(col);
            }
        }

        if (!pks.isEmpty()) {
            defs.add("CONSTRAINT PK_" + nombreTabla + " PRIMARY KEY (" + String.join(", ", pks) + ")");
        }

        String sql = "CREATE TABLE " + nombreTabla + " (\n    " + String.join(",\n    ", defs) + "\n)";

        try {
            ejecutor.ejecutar(sql);
            creada = true;
            JOptionPane.showMessageDialog(this, "Tabla creada:\n\n" + sql);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error SQL:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isCreada() {
        return creada;
    }
}