package ui;

import bd.EjecutorSQL;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class DialogoVista extends JDialog {

    private final EjecutorSQL ejecutor;
    private final JTextField txtNombre = new JTextField(20);
    private final JTextArea txtSelect = new JTextArea("SELECT * FROM ");
    private boolean creada = false;

    public DialogoVista(Frame owner, EjecutorSQL ejecutor) {
        super(owner, "Crear Nueva Vista", true);
        this.ejecutor = ejecutor;
        setSize(520, 300);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Nombre de la Vista:"));
        top.add(txtNombre);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(txtSelect);
        scroll.setBorder(BorderFactory.createTitledBorder("Consulta SELECT de la Vista"));
        add(scroll, BorderLayout.CENTER);

        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCrear = new JButton("Crear Vista");
        btnCrear.addActionListener(e -> crear());
        sur.add(btnCrear);
        add(sur, BorderLayout.SOUTH);
    }

    private void crear() {
        String nom = txtNombre.getText().trim().toUpperCase();
        String sel = txtSelect.getText().trim();
        if (nom.isEmpty() || sel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Completa el nombre y el SELECT.");
            return;
        }
        if (sel.endsWith(";")) {
            sel = sel.substring(0, sel.length() - 1);
        }

        String sql = "CREATE VIEW " + nom + " AS\n" + sel;
        try {
            ejecutor.ejecutar(sql);
            creada = true;
            JOptionPane.showMessageDialog(this, "Vista creada.");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error SQL:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isCreada() {
        return creada;
    }
}