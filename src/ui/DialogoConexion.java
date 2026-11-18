package ui;

import bd.ConexionBD;
import bd.ConexionBD.Perfil;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.sql.Connection;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class DialogoConexion extends JDialog {

    private final List<Perfil> perfiles;
    private final DefaultListModel<Perfil> modeloLista;
    private final JList<Perfil> lista;

    private final JTextField txtNombre = new JTextField();
    private final JTextField txtIp = new JTextField("localhost");
    private final JTextField txtPuerto = new JTextField("3050");
    private final JTextField txtRuta = new JTextField();
    private final JTextField txtUsuario = new JTextField("SYSDBA");
    private final JPasswordField txtClave = new JPasswordField();

    private Connection conexionActiva;
    private Perfil perfilActivo;

    public DialogoConexion(Frame owner) {
        super(owner, "Gestor de Conexiones InterBase", true);
        setSize(620, 340);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        perfiles = ConexionBD.cargar();
        modeloLista = new DefaultListModel<>();
        for (Perfil p : perfiles) {
            modeloLista.addElement(p);
        }

        lista = new JList<>(modeloLista);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) {
                cargarCampos(lista.getSelectedValue());
            }
        });

        JScrollPane scrollLista = new JScrollPane(lista);
        scrollLista.setBorder(BorderFactory.createTitledBorder("Conexiones Guardadas"));
        scrollLista.setPreferredSize(new java.awt.Dimension(220, 0));
        add(scrollLista, BorderLayout.WEST);

        JPanel panelForm = new JPanel(new GridLayout(6, 2, 5, 8));
        panelForm.setBorder(BorderFactory.createTitledBorder("Datos del Servidor"));
        panelForm.add(new JLabel("Nombre Perfil:"));
        panelForm.add(txtNombre);
        panelForm.add(new JLabel("Host / IP VPS:"));
        panelForm.add(txtIp);
        panelForm.add(new JLabel("Puerto:"));
        panelForm.add(txtPuerto);
        panelForm.add(new JLabel("Ruta archivo (.ib):"));
        panelForm.add(txtRuta);
        panelForm.add(new JLabel("Usuario:"));
        panelForm.add(txtUsuario);
        panelForm.add(new JLabel("Contraseña:"));
        panelForm.add(txtClave);
        add(panelForm, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnNuevo = new JButton("Nuevo");
        JButton btnGuardar = new JButton("Guardar Perfil");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnConectar = new JButton("Conectar");

        btnNuevo.addActionListener(e -> limpiarCampos());
        btnGuardar.addActionListener(e -> guardarPerfil());
        btnEliminar.addActionListener(e -> eliminarPerfil());
        btnConectar.addActionListener(e -> conectar());

        panelBotones.add(btnNuevo);
        panelBotones.add(btnGuardar);
        panelBotones.add(btnEliminar);
        panelBotones.add(btnConectar);
        add(panelBotones, BorderLayout.SOUTH);
    }

    private void cargarCampos(Perfil p) {
        txtNombre.setText(p.nombre);
        txtIp.setText(p.ip);
        txtPuerto.setText(p.puerto);
        txtRuta.setText(p.ruta);
        txtUsuario.setText(p.usuario);
        txtClave.setText(p.clave);
    }

    private void limpiarCampos() {
        lista.clearSelection();
        txtNombre.setText("");
        txtIp.setText("");
        txtPuerto.setText("3050");
        txtRuta.setText("");
        txtUsuario.setText("SYSDBA");
        txtClave.setText("");
    }

    private void guardarPerfil() {
        String nom = txtNombre.getText().trim();
        if (nom.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingresa un nombre para el perfil.");
            return;
        }
        Perfil p = new Perfil(
            nom,
            txtIp.getText().trim(),
            txtPuerto.getText().trim(),
            txtRuta.getText().trim(),
            txtUsuario.getText().trim(),
            new String(txtClave.getPassword())
        );

        int idx = lista.getSelectedIndex();
        if (idx >= 0) {
            perfiles.set(idx, p);
            modeloLista.set(idx, p);
        } else {
            perfiles.add(p);
            modeloLista.addElement(p);
        }

        try {
            ConexionBD.guardar(perfiles);
            JOptionPane.showMessageDialog(this, "Perfil guardado.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage());
        }
    }

    private void eliminarPerfil() {
        int idx = lista.getSelectedIndex();
        if (idx >= 0) {
            perfiles.remove(idx);
            modeloLista.remove(idx);
            try {
                ConexionBD.guardar(perfiles);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            limpiarCampos();
        }
    }

    private void conectar() {
        Perfil p = new Perfil(
            txtNombre.getText().trim().isEmpty() ? "Temporal" : txtNombre.getText().trim(),
            txtIp.getText().trim(),
            txtPuerto.getText().trim(),
            txtRuta.getText().trim(),
            txtUsuario.getText().trim(),
            new String(txtClave.getPassword())
        );
        try {
            conexionActiva = ConexionBD.conectar(p);
            perfilActivo = p;
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo conectar:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public Connection getConexionActiva() {
        return conexionActiva;
    }

    public Perfil getPerfilActivo() {
        return perfilActivo;
    }
}