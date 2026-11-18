package ui;

import bd.Catalogo;
import bd.ConexionBD.Perfil;
import bd.EjecutorSQL;
import bd.EjecutorSQL.Resultado;
import bd.GeneradorDDL;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class VentanaPrincipal extends JFrame {

    private Connection con;
    private Catalogo catalogo;
    private GeneradorDDL ddl;
    private EjecutorSQL ejecutor;

    private final DefaultMutableTreeNode nodoRaiz = new DefaultMutableTreeNode("DBara (Desconectado)");
    private final DefaultTreeModel modeloArbol = new DefaultTreeModel(nodoRaiz);
    private final JTree arbol = new JTree(modeloArbol);

    private final JTextArea editorSql = new JTextArea();
    private final JTable tablaResultados = new JTable();
    private final JLabel lblEstado = new JLabel(" Listo. Conéctate a una base de datos para empezar.");

    public VentanaPrincipal() {
        setTitle("DBara - Administrador InterBase");
        setSize(1050, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton btnConectar = new JButton("Conexiones");
        JButton btnRefrescar = new JButton("Refrescar Árbol");
        JButton btnEjecutar = new JButton("Ejecutar SQL");
        JButton btnScript = new JButton("Ejecutar Script");
        JButton btnNuevaTabla = new JButton("+ Tabla");
        JButton btnNuevaVista = new JButton("+ Vista");

        btnConectar.addActionListener(e -> abrirConexiones());
        btnRefrescar.addActionListener(e -> cargarArbol());
        btnEjecutar.addActionListener(e -> ejecutarSql(false));
        btnScript.addActionListener(e -> ejecutarSql(true));
        btnNuevaTabla.addActionListener(e -> abrirNuevaTabla());
        btnNuevaVista.addActionListener(e -> abrirNuevaVista());

        barra.add(btnConectar);
        barra.add(btnRefrescar);
        barra.add(btnEjecutar);
        barra.add(btnScript);
        barra.add(btnNuevaTabla);
        barra.add(btnNuevaVista);
        add(barra, BorderLayout.NORTH);

        arbol.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    seleccionarNodoArbol();
                }
            }
        });

        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setPreferredSize(new Dimension(260, 0));

        editorSql.setFont(new Font("Consolas", Font.PLAIN, 14));
        JScrollPane scrollEditor = new JScrollPane(editorSql);
        scrollEditor.setBorder(BorderFactory.createTitledBorder("Editor SQL / DDL"));

        JScrollPane scrollTabla = new JScrollPane(tablaResultados);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Resultados"));

        JSplitPane splitDerecho = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollEditor, scrollTabla);
        splitDerecho.setDividerLocation(290);

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollArbol, splitDerecho);
        splitPrincipal.setDividerLocation(260);
        add(splitPrincipal, BorderLayout.CENTER);

        lblEstado.setBorder(BorderFactory.createEtchedBorder());
        add(lblEstado, BorderLayout.SOUTH);
    }

    private void abrirConexiones() {
        DialogoConexion dlg = new DialogoConexion(this);
        dlg.setVisible(true);
        if (dlg.getConexionActiva() != null) {
            try {
                if (con != null && !con.isClosed()) {
                    con.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            con = dlg.getConexionActiva();
            Perfil p = dlg.getPerfilActivo();
            catalogo = new Catalogo(con);
            ddl = new GeneradorDDL(con);
            ejecutor = new EjecutorSQL(con);

            nodoRaiz.setUserObject(p.nombre + " (" + p.ip + ")");
            cargarArbol();
            lblEstado.setText(" Conectado a " + p.getUrl() + " como " + p.usuario);
        }
    }

    private void cargarArbol() {
        if (catalogo == null) {
            return;
        }
        nodoRaiz.removeAllChildren();
        try {
            agregarCategoria("Tablas", catalogo.getTablas());
            agregarCategoria("Vistas", catalogo.getVistas());
            agregarCategoria("Procedimientos", catalogo.getProcedimientos());
            agregarCategoria("Funciones", catalogo.getFunciones());
            agregarCategoria("Generadores", catalogo.getGeneradores());
            agregarCategoria("Triggers", catalogo.getTriggers());
            agregarCategoria("Índices", catalogo.getIndices());
            agregarCategoria("Usuarios", catalogo.getUsuarios());

            DefaultMutableTreeNode nPaq = new DefaultMutableTreeNode("Paquetes (No soportado)");
            nPaq.add(new DefaultMutableTreeNode("Ver justificación técnica"));
            nodoRaiz.add(nPaq);

            DefaultMutableTreeNode nTs = new DefaultMutableTreeNode("Tablespaces (No soportado)");
            nTs.add(new DefaultMutableTreeNode("Ver justificación técnica"));
            nodoRaiz.add(nTs);

            modeloArbol.reload();
            for (int i = 0; i < arbol.getRowCount(); i++) {
                arbol.expandRow(i);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error leyendo catálogo RDB$:\n" + ex.getMessage());
        }
    }

    private void agregarCategoria(String titulo, List<String> items) {
        DefaultMutableTreeNode cat = new DefaultMutableTreeNode(titulo + " (" + items.size() + ")");
        for (String item : items) {
            cat.add(new DefaultMutableTreeNode(item));
        }
        nodoRaiz.add(cat);
    }

    private void seleccionarNodoArbol() {
        TreePath path = arbol.getSelectionPath();
        if (path == null || path.getPathCount() < 3 || ddl == null) {
            return;
        }
        DefaultMutableTreeNode padre = (DefaultMutableTreeNode) path.getPathComponent(1);
        DefaultMutableTreeNode hijo = (DefaultMutableTreeNode) path.getLastPathComponent();

        String cat = padre.toString();
        String nombre = hijo.toString();

        try {
            String texto = "";
            if (cat.startsWith("Tablas")) {
                texto = ddl.getDDLTabla(nombre);
            } else if (cat.startsWith("Vistas")) {
                texto = ddl.getDDLVista(nombre);
            } else if (cat.startsWith("Procedimientos")) {
                texto = ddl.getDDLProcedimiento(nombre);
            } else if (cat.startsWith("Funciones")) {
                texto = ddl.getDDLFuncion(nombre);
            } else if (cat.startsWith("Generadores")) {
                texto = ddl.getDDLGenerador(nombre);
            } else if (cat.startsWith("Triggers")) {
                texto = ddl.getDDLTrigger(nombre);
            } else if (cat.startsWith("Índices")) {
                texto = ddl.getDDLIndice(nombre);
            } else if (cat.startsWith("Usuarios")) {
                texto = "SELECT * FROM RDB$USER_PRIVILEGES WHERE RDB$USER = '" + nombre + "';";
            } else if (cat.startsWith("Paquetes")) {
                texto = "-- JUSTIFICACIÓN TÉCNICA (PAQUETES):\n" +
                        "-- InterBase no implementa paquetes estilo Oracle (CREATE PACKAGE).\n" +
                        "-- Los procedimientos y funciones se almacenan de forma independiente en RDB$PROCEDURES y RDB$FUNCTIONS.";
            } else if (cat.startsWith("Tablespaces")) {
                texto = "-- JUSTIFICACIÓN TÉCNICA (TABLESPACES):\n" +
                        "-- En arquitectura estándar de InterBase el almacenamiento físico se maneja por archivos secundarios\n" +
                        "-- registrados en RDB$FILES (ALTER DATABASE ADD FILE) en lugar de tablespaces lógicos.";
            }
            editorSql.setText(texto);
            lblEstado.setText(" DDL generado desde RDB$ para: " + nombre);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error extrayendo DDL:\n" + ex.getMessage());
        }
    }

    private void ejecutarSql(boolean comoScript) {
        if (ejecutor == null) {
            JOptionPane.showMessageDialog(this, "Primero conéctate a una base de datos.");
            return;
        }
        String sql = editorSql.getSelectedText();
        if (sql == null || sql.trim().isEmpty()) {
            sql = editorSql.getText();
        }
        if (sql.trim().isEmpty()) {
            return;
        }

        try {
            if (comoScript) {
                String msg = ejecutor.ejecutarScript(sql);
                lblEstado.setText(" " + msg);
                cargarArbol();
            } else {
                Resultado res = ejecutor.ejecutar(sql);
                if (res.esConsulta) {
                    tablaResultados.setModel(res.modelo);
                } else {
                    tablaResultados.setModel(new DefaultTableModel());
                    cargarArbol();
                }
                lblEstado.setText(" " + res.mensaje);
            }
        } catch (Exception ex) {
            lblEstado.setText(" Error SQL: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error de Ejecución SQL", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirNuevaTabla() {
        if (ejecutor == null) {
            JOptionPane.showMessageDialog(this, "Primero conéctate a una base de datos.");
            return;
        }
        DialogoTabla dlg = new DialogoTabla(this, ejecutor);
        dlg.setVisible(true);
        if (dlg.isCreada()) {
            cargarArbol();
        }
    }

    private void abrirNuevaVista() {
        if (ejecutor == null) {
            JOptionPane.showMessageDialog(this, "Primero conéctate a una base de datos.");
            return;
        }
        DialogoVista dlg = new DialogoVista(this, ejecutor);
        dlg.setVisible(true);
        if (dlg.isCreada()) {
            cargarArbol();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new VentanaPrincipal().setVisible(true);
        });
    }
}