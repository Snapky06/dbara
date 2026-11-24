const arbol = document.getElementById("arbol");
const tituloConexion = document.getElementById("tituloConexion");
const editorSql = document.getElementById("editorSql");
const tablaResultados = document.getElementById("tablaResultados");
const barraEstado = document.getElementById("barraEstado");

const modalConexion = document.getElementById("modalConexion");
const modalTabla = document.getElementById("modalTabla");
const modalVista = document.getElementById("modalVista");
const listaColumnas = document.getElementById("listaColumnas");

let conectado = false;

function actualizarEstado(mensaje) {
    barraEstado.textContent = mensaje;
}

function abrirModal(modal) {
    modal.classList.remove("oculto");
}

function cerrarModales() {
    modalConexion.classList.add("oculto");
    modalTabla.classList.add("oculto");
    modalVista.classList.add("oculto");
}

document.querySelectorAll(".cerrar-modal").forEach(btn => {
    btn.addEventListener("click", cerrarModales);
});

document.getElementById("btnConectar").addEventListener("click", () => abrirModal(modalConexion));
document.getElementById("btnRefrescar").addEventListener("click", cargarCatalogo);
document.getElementById("btnEjecutar").addEventListener("click", () => ejecutarSql(false));
document.getElementById("btnScript").addEventListener("click", () => ejecutarSql(true));

document.getElementById("btnNuevaTabla").addEventListener("click", () => {
    if (!conectado) {
        alert("Primero conéctate a una base de datos.");
        return;
    }
    document.getElementById("tablaNombre").value = "";
    listaColumnas.innerHTML = "";
    agregarFilaColumna();
    abrirModal(modalTabla);
});

document.getElementById("btnNuevaVista").addEventListener("click", () => {
    if (!conectado) {
        alert("Primero conéctate a una base de datos.");
        return;
    }
    document.getElementById("vistaNombre").value = "";
    document.getElementById("vistaSelect").value = "";
    abrirModal(modalVista);
});

document.getElementById("btnAgregarColumna").addEventListener("click", agregarFilaColumna);

function agregarFilaColumna() {
    const fila = document.createElement("div");
    fila.className = "fila-columna";
    fila.innerHTML = `
        <input type="text" class="col-nombre" placeholder="Nombre" required>
        <select class="col-tipo">
            <option value="INTEGER">INTEGER</option>
            <option value="VARCHAR(100)">VARCHAR(100)</option>
            <option value="VARCHAR(255)">VARCHAR(255)</option>
            <option value="CHAR(10)">CHAR(10)</option>
            <option value="DECIMAL(10,2)">DECIMAL(10,2)</option>
            <option value="DOUBLE PRECISION">DOUBLE PRECISION</option>
            <option value="DATE">DATE</option>
            <option value="TIMESTAMP">TIMESTAMP</option>
            <option value="BLOB SUB_TYPE 1">BLOB SUB_TYPE 1</option>
        </select>
        <label><input type="checkbox" class="col-pk"> PK</label>
        <label><input type="checkbox" class="col-nn"> NN</label>
        <button type="button" class="btn-quitar">X</button>
    `;
    fila.querySelector(".btn-quitar").addEventListener("click", () => fila.remove());
    listaColumnas.appendChild(fila);
}

document.getElementById("formConexion").addEventListener("submit", async e => {
    e.preventDefault();
    const datos = {
        host: document.getElementById("conHost").value.trim(),
        puerto: document.getElementById("conPuerto").value.trim(),
        ruta: document.getElementById("conRuta").value.trim(),
        usuario: document.getElementById("conUsuario").value.trim(),
        clave: document.getElementById("conClave").value
    };

    try {
        actualizarEstado("Conectando...");
        const resp = await fetch("/api/conectar", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(datos)
        });
        const res = await resp.json();
        if (!res.ok) {
            throw new Error(res.error || "Error al conectar");
        }
        conectado = true;
        tituloConexion.textContent = `${datos.usuario}@${datos.host}`;
        actualizarEstado(res.mensaje);
        cerrarModales();
        await cargarCatalogo();
    } catch (err) {
        actualizarEstado("Error: " + err.message);
        alert(err.message);
    }
});

async function cargarCatalogo() {
    if (!conectado) {
        return;
    }
    try {
        const resp = await fetch("/api/catalogo");
        const data = await resp.json();
        if (!data.ok) {
            throw new Error(data.error || "No se pudo leer el catálogo");
        }

        arbol.innerHTML = "";
        const categorias = [
            { clave: "tablas", titulo: "Tablas", items: data.tablas },
            { clave: "vistas", titulo: "Vistas", items: data.vistas },
            { clave: "procedimientos", titulo: "Procedimientos", items: data.procedimientos },
            { clave: "funciones", titulo: "Funciones", items: data.funciones },
            { clave: "generadores", titulo: "Generadores", items: data.generadores },
            { clave: "triggers", titulo: "Triggers", items: data.triggers },
            { clave: "indices", titulo: "Índices", items: data.indices },
            { clave: "usuarios", titulo: "Usuarios", items: data.usuarios },
            { clave: "paquetes", titulo: "Paquetes (No soportado)", items: ["Ver justificación técnica"] },
            { clave: "tablespaces", titulo: "Tablespaces (No soportado)", items: ["Ver justificación técnica"] }
        ];

        categorias.forEach(cat => {
            const bloque = document.createElement("div");
            bloque.className = "categoria";

            const cabecera = document.createElement("div");
            cabecera.className = "categoria-titulo";
            cabecera.textContent = `${cat.titulo} (${cat.items.length})`;

            const lista = document.createElement("ul");
            lista.className = "categoria-lista";

            cabecera.addEventListener("click", () => {
                lista.classList.toggle("oculto");
            });

            cat.items.forEach(nombre => {
                const li = document.createElement("li");
                li.textContent = nombre;
                li.title = nombre;
                li.addEventListener("dblclick", () => cargarDdl(cat.clave, nombre));
                lista.appendChild(li);
            });

            bloque.appendChild(cabecera);
            bloque.appendChild(lista);
            arbol.appendChild(bloque);
        });
    } catch (err) {
        actualizarEstado("Error catálogo: " + err.message);
    }
}

async function cargarDdl(tipo, nombre) {
    try {
        const url = `/api/ddl?tipo=${encodeURIComponent(tipo)}&nombre=${encodeURIComponent(nombre)}`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (!data.ok) {
            throw new Error(data.error || "Error generando DDL");
        }
        editorSql.value = data.ddl;
        actualizarEstado(`DDL generado desde RDB$ para: ${nombre}`);
    } catch (err) {
        actualizarEstado("Error DDL: " + err.message);
        alert(err.message);
    }
}

async function ejecutarSql(esScript) {
    if (!conectado) {
        alert("Primero conéctate a una base de datos.");
        return;
    }

    const inicio = editorSql.selectionStart;
    const fin = editorSql.selectionEnd;
    let sql = inicio !== fin ? editorSql.value.substring(inicio, fin) : editorSql.value;
    sql = sql.trim();
    if (!sql) {
        return;
    }

    try {
        actualizarEstado("Ejecutando...");
        const resp = await fetch("/api/ejecutar", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ sql, esScript })
        });
        const data = await resp.json();
        if (!data.ok) {
            throw new Error(data.error || "Error de ejecución SQL");
        }

        if (data.esConsulta) {
            renderizarTabla(data.columnas, data.filas);
        } else {
            renderizarTabla([], []);
            await cargarCatalogo();
        }
        actualizarEstado(data.mensaje);
    } catch (err) {
        actualizarEstado("Error SQL: " + err.message);
        alert(err.message);
    }
}

function renderizarTabla(columnas, filas) {
    const thead = tablaResultados.querySelector("thead");
    const tbody = tablaResultados.querySelector("tbody");
    thead.innerHTML = "";
    tbody.innerHTML = "";

    if (!columnas.length) {
        return;
    }

    const trHead = document.createElement("tr");
    columnas.forEach(col => {
        const th = document.createElement("th");
        th.textContent = col;
        trHead.appendChild(th);
    });
    thead.appendChild(trHead);

    filas.forEach(fila => {
        const tr = document.createElement("tr");
        columnas.forEach(col => {
            const td = document.createElement("td");
            const val = fila[col];
            td.textContent = val === null || val === undefined ? "NULL" : String(val);
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });
}

document.getElementById("formTabla").addEventListener("submit", async e => {
    e.preventDefault();
    const nombreTabla = document.getElementById("tablaNombre").value.trim();
    const filas = listaColumnas.querySelectorAll(".fila-columna");
    if (!nombreTabla || !filas.length) {
        return;
    }

    const definiciones = [];
    const pks = [];

    filas.forEach(f => {
        const col = f.querySelector(".col-nombre").value.trim();
        const tipo = f.querySelector(".col-tipo").value;
        const esPk = f.querySelector(".col-pk").checked;
        const esNn = f.querySelector(".col-nn").checked;
        if (!col) return;

        let linea = `${col} ${tipo}`;
        if (esPk || esNn) {
            linea += " NOT NULL";
        }
        definiciones.push(linea);
        if (esPk) {
            pks.push(col);
        }
    });

    if (pks.length) {
        definiciones.push(`PRIMARY KEY (${pks.join(", ")})`);
    }

    const sql = `CREATE TABLE ${nombreTabla} (\n    ${definiciones.join(",\n    ")}\n);`;
    editorSql.value = sql;
    cerrarModales();
    await ejecutarSql(false);
});

document.getElementById("formVista").addEventListener("submit", async e => {
    e.preventDefault();
    const nombreVista = document.getElementById("vistaNombre").value.trim();
    const select = document.getElementById("vistaSelect").value.trim().replace(/;+$/, "");
    if (!nombreVista || !select) {
        return;
    }

    const sql = `CREATE VIEW ${nombreVista} AS\n${select};`;
    editorSql.value = sql;
    cerrarModales();
    await ejecutarSql(false);
});