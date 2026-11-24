document.addEventListener("DOMContentLoaded", () => {
    const STORAGE_KEY = "dbara_perfiles";
    const TIPOS_COLUMNA = [
        "INTEGER",
        "VARCHAR(100)",
        "VARCHAR(255)",
        "CHAR(10)",
        "DECIMAL(10,2)",
        "DOUBLE PRECISION",
        "DATE",
        "TIMESTAMP",
        "BLOB SUB_TYPE 1"
    ];

    const barraProgreso = document.getElementById("barraProgreso");
    const arbol = document.getElementById("arbol");
    const filtroArbol = document.getElementById("filtroArbol");
    const tituloConexion = document.getElementById("tituloConexion");
    const indicadorConexion = document.getElementById("indicadorConexion");
    const textoIndicador = document.getElementById("textoIndicador");
    const editorSql = document.getElementById("editorSql");
    const zonaEditor = document.getElementById("zonaEditor");
    const divisorHorizontal = document.getElementById("divisorHorizontal");
    const tablaResultados = document.getElementById("tablaResultados");
    const cargandoResultados = document.getElementById("cargandoResultados");
    const textoCargandoResultados = document.getElementById("textoCargandoResultados");
    const estadoVacioResultados = document.getElementById("estadoVacioResultados");
    const contadorResultados = document.getElementById("contadorResultados");
    const tiempoEjecucion = document.getElementById("tiempoEjecucion");
    const barraEstado = document.getElementById("barraEstado");
    const toastContenedor = document.getElementById("toastContenedor");

    const btnConectar = document.getElementById("btnConectar");
    const btnDesconectar = document.getElementById("btnDesconectar");
    const btnRefrescar = document.getElementById("btnRefrescar");
    const btnEjecutar = document.getElementById("btnEjecutar");
    const btnScript = document.getElementById("btnScript");
    const btnLimpiarSql = document.getElementById("btnLimpiarSql");
    const btnExportarCsv = document.getElementById("btnExportarCsv");
    const btnSubmitConexion = document.getElementById("btnSubmitConexion");

    const modalConexion = document.getElementById("modalConexion");
    const modalTabla = document.getElementById("modalTabla");
    const modalVista = document.getElementById("modalVista");
    const listaColumnas = document.getElementById("listaColumnas");
    const selectPerfil = document.getElementById("selectPerfil");

    let conectado = false;
    let peticionesActivas = 0;
    let ultimasColumnas = [];
    let ultimasFilas = [];

    function iniciarProgresoGlobal() {
        peticionesActivas++;
        barraProgreso.classList.add("activa");
    }

    function finalizarProgresoGlobal() {
        peticionesActivas = Math.max(0, peticionesActivas - 1);
        if (peticionesActivas === 0) {
            barraProgreso.classList.remove("activa");
        }
    }

    function setBotonCargando(btn, activo, textoCargando) {
        if (!btn) return;
        if (activo) {
            if (!btn.dataset.textoOriginal) {
                btn.dataset.textoOriginal = btn.textContent;
            }
            btn.disabled = true;
            btn.classList.add("cargando");
            if (textoCargando) {
                btn.textContent = textoCargando;
            }
        } else {
            btn.disabled = false;
            btn.classList.remove("cargando");
            if (btn.dataset.textoOriginal) {
                btn.textContent = btn.dataset.textoOriginal;
            }
        }
    }

    function mostrarCargaResultados(visible, texto = "Consultando InterBase...") {
        textoCargandoResultados.textContent = texto;
        cargandoResultados.classList.toggle("oculto", !visible);
    }

    function mostrarSkeletonArbol() {
        arbol.textContent = "";
        for (let g = 0; g < 4; g++) {
            const grupo = document.createElement("div");
            grupo.className = "skeleton-grupo";

            const titulo = document.createElement("div");
            titulo.className = "skeleton-linea titulo";
            grupo.appendChild(titulo);

            for (let i = 0; i < 3; i++) {
                const linea = document.createElement("div");
                linea.className = i % 2 === 0 ? "skeleton-linea media" : "skeleton-linea corta";
                grupo.appendChild(linea);
            }
            arbol.appendChild(grupo);
        }
    }

    function actualizarEstado(mensaje, tipo = "") {
        barraEstado.textContent = mensaje;
        barraEstado.className = tipo ? `estado ${tipo}` : "estado";
    }

    function mostrarToast(mensaje, tipo = "") {
        const toast = document.createElement("div");
        toast.className = tipo ? `toast ${tipo}` : "toast";

        const span = document.createElement("span");
        span.textContent = mensaje;

        const btnCerrar = document.createElement("button");
        btnCerrar.type = "button";
        btnCerrar.className = "toast-cerrar";
        btnCerrar.textContent = "×";

        const cerrar = () => {
            toast.classList.add("saliendo");
            setTimeout(() => toast.remove(), 180);
        };

        btnCerrar.addEventListener("click", cerrar);
        toast.appendChild(span);
        toast.appendChild(btnCerrar);
        toastContenedor.appendChild(toast);

        const duracion = tipo === "error" ? 5500 : 3200;
        setTimeout(cerrar, duracion);
    }

    async function solicitarApi(url, opciones = {}) {
        iniciarProgresoGlobal();
        try {
            const resp = await fetch(url, opciones);
            const texto = await resp.text();
            let data;
            try {
                data = JSON.parse(texto);
            } catch {
                throw new Error(texto || `Respuesta inválida del servidor (${resp.status})`);
            }
            if (!resp.ok || !data.ok) {
                throw new Error(data.error || `Error HTTP ${resp.status}`);
            }
            return data;
        } finally {
            finalizarProgresoGlobal();
        }
    }

    function abrirModal(modal) {
        if (modal) {
            modal.classList.remove("oculto");
        }
    }

    function cerrarModales() {
        modalConexion.classList.add("oculto");
        modalTabla.classList.add("oculto");
        modalVista.classList.add("oculto");
    }

    document.querySelectorAll(".cerrar-modal").forEach(btn => {
        btn.addEventListener("click", cerrarModales);
    });

    [modalConexion, modalTabla, modalVista].forEach(modal => {
        modal.addEventListener("click", e => {
            if (e.target === modal) {
                cerrarModales();
            }
        });
    });

    document.addEventListener("keydown", e => {
        if (e.key === "Escape") {
            cerrarModales();
        }
    });

    btnConectar.addEventListener("click", () => {
        abrirModal(modalConexion);
        document.getElementById("conNombre").focus();
    });

    btnDesconectar.addEventListener("click", async () => {
        setBotonCargando(btnDesconectar, true, "Cerrando...");
        try {
            await solicitarApi("/api/desconectar", { method: "POST" });
            conectado = false;
            tituloConexion.textContent = "Explorador";
            textoIndicador.textContent = "Desconectado";
            indicadorConexion.classList.remove("activo");
            btnDesconectar.classList.add("oculto");
            filtroArbol.value = "";
            filtroArbol.disabled = true;

            arbol.textContent = "";
            const vacio = document.createElement("div");
            vacio.className = "arbol-vacio";
            vacio.textContent = "Conéctate a un servidor InterBase para explorar el catálogo.";
            arbol.appendChild(vacio);

            renderizarTabla([], []);
            tiempoEjecucion.classList.add("oculto");
            actualizarEstado("Sesión cerrada.");
            mostrarToast("Desconectado de InterBase.");
        } catch (err) {
            actualizarEstado("Error: " + err.message, "error");
            mostrarToast(err.message, "error");
        } finally {
            setBotonCargando(btnDesconectar, false);
        }
    });

    btnRefrescar.addEventListener("click", cargarCatalogo);
    btnEjecutar.addEventListener("click", () => ejecutarSql(false));
    btnScript.addEventListener("click", () => ejecutarSql(true));

    btnLimpiarSql.addEventListener("click", () => {
        editorSql.value = "";
        editorSql.focus();
    });

    btnExportarCsv.addEventListener("click", exportarCsv);

    document.getElementById("btnNuevaTabla").addEventListener("click", () => {
        if (!conectado) {
            mostrarToast("Primero conéctate a una base de datos.", "error");
            abrirModal(modalConexion);
            return;
        }
        document.getElementById("tablaNombre").value = "";
        listaColumnas.textContent = "";
        agregarFilaColumna();
        abrirModal(modalTabla);
    });

    document.getElementById("btnNuevaVista").addEventListener("click", () => {
        if (!conectado) {
            mostrarToast("Primero conéctate a una base de datos.", "error");
            abrirModal(modalConexion);
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

        const inputNombre = document.createElement("input");
        inputNombre.type = "text";
        inputNombre.className = "col-nombre";
        inputNombre.placeholder = "Nombre";
        inputNombre.required = true;

        const selectTipo = document.createElement("select");
        selectTipo.className = "col-tipo";
        TIPOS_COLUMNA.forEach(tipo => {
            const opt = document.createElement("option");
            opt.value = tipo;
            opt.textContent = tipo;
            selectTipo.appendChild(opt);
        });

        const labelPk = document.createElement("label");
        const checkPk = document.createElement("input");
        checkPk.type = "checkbox";
        checkPk.className = "col-pk";
        labelPk.appendChild(checkPk);
        labelPk.appendChild(document.createTextNode(" PK"));

        const labelNn = document.createElement("label");
        const checkNn = document.createElement("input");
        checkNn.type = "checkbox";
        checkNn.className = "col-nn";
        labelNn.appendChild(checkNn);
        labelNn.appendChild(document.createTextNode(" NN"));

        const btnQuitar = document.createElement("button");
        btnQuitar.type = "button";
        btnQuitar.className = "peligro";
        btnQuitar.textContent = "X";
        btnQuitar.addEventListener("click", () => fila.remove());

        fila.appendChild(inputNombre);
        fila.appendChild(selectTipo);
        fila.appendChild(labelPk);
        fila.appendChild(labelNn);
        fila.appendChild(btnQuitar);
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

        setBotonCargando(btnSubmitConexion, true, "Conectando...");
        actualizarEstado("Estableciendo conexión con InterBase...");

        try {
            const res = await solicitarApi("/api/conectar", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(datos)
            });

            conectado = true;
            const etiqueta = `${datos.usuario}@${datos.host}`;
            tituloConexion.textContent = etiqueta;
            textoIndicador.textContent = etiqueta;
            indicadorConexion.classList.add("activo");
            btnDesconectar.classList.remove("oculto");
            filtroArbol.disabled = false;

            actualizarEstado(res.mensaje, "ok");
            mostrarToast(`Conectado a ${datos.host}`, "ok");
            cerrarModales();
            await cargarCatalogo();
        } catch (err) {
            actualizarEstado("Error: " + err.message, "error");
            mostrarToast(err.message, "error");
        } finally {
            setBotonCargando(btnSubmitConexion, false);
        }
    });

    async function cargarCatalogo() {
        if (!conectado) {
            mostrarToast("Conéctate primero para cargar el catálogo.", "error");
            return;
        }
        setBotonCargando(btnRefrescar, true, "Cargando...");
        mostrarSkeletonArbol();

        try {
            const data = await solicitarApi("/api/catalogo");
            arbol.textContent = "";

            const categorias = [
    { clave: "tablas", titulo: "Tablas", items: data.tablas || [], permiteDatos: true },
    { clave: "vistas", titulo: "Vistas", items: data.vistas || [], permiteDatos: true },
    { clave: "procedimientos", titulo: "Procedimientos", items: data.procedimientos || [] },
    { clave: "funciones", titulo: "Funciones", items: data.funciones || [] },
    { clave: "generadores", titulo: "Generadores", items: data.generadores || [] },
    { clave: "triggers", titulo: "Triggers", items: data.triggers || [] },
    { clave: "indices", titulo: "Índices", items: data.indices || [] },
    { clave: "usuarios", titulo: "Usuarios", items: data.usuarios || [] }
];

            categorias.forEach(cat => {
                const bloque = document.createElement("div");
                bloque.className = "categoria";

                const cabecera = document.createElement("div");
                cabecera.className = "categoria-titulo";

                const spanTitulo = document.createElement("span");
                spanTitulo.textContent = cat.titulo;

                const spanCuenta = document.createElement("span");
                spanCuenta.className = "categoria-cuenta";
                spanCuenta.textContent = String(cat.items.length);

                cabecera.appendChild(spanTitulo);
                cabecera.appendChild(spanCuenta);

                const lista = document.createElement("ul");
                lista.className = "categoria-lista";

                cabecera.addEventListener("click", () => {
                    lista.classList.toggle("oculto");
                });

                cat.items.forEach(nombre => {
                    const li = document.createElement("li");
                    li.dataset.nombre = nombre.toLowerCase();

                    const spanNombre = document.createElement("span");
                    spanNombre.className = "item-nombre";
                    spanNombre.textContent = nombre;
                    spanNombre.title = nombre;
                    li.appendChild(spanNombre);

                    if (cat.permiteDatos) {
                        const btnDatos = document.createElement("button");
                        btnDatos.type = "button";
                        btnDatos.className = "btn-ver-datos";
                        btnDatos.textContent = "Datos";
                        btnDatos.title = `Ver primeras 100 filas de ${nombre}`;
                        btnDatos.addEventListener("click", e => {
                            e.stopPropagation();
                            verDatosObjeto(nombre);
                        });
                        li.appendChild(btnDatos);
                    }

                    li.addEventListener("click", () => {
                        arbol.querySelectorAll("li.activo").forEach(el => el.classList.remove("activo"));
                        li.classList.add("activo");
                    });
                    li.addEventListener("dblclick", () => cargarDdl(cat.clave, nombre));
                    lista.appendChild(li);
                });

                bloque.appendChild(cabecera);
                bloque.appendChild(lista);
                arbol.appendChild(bloque);
            });

            if (filtroArbol.value.trim()) {
                aplicarFiltroArbol();
            }
        } catch (err) {
            arbol.textContent = "";
            actualizarEstado("Error catálogo: " + err.message, "error");
            mostrarToast("No se pudo cargar el catálogo: " + err.message, "error");
        } finally {
            setBotonCargando(btnRefrescar, false);
        }
    }

    function aplicarFiltroArbol() {
        const termino = filtroArbol.value.trim().toLowerCase();
        arbol.querySelectorAll(".categoria").forEach(bloque => {
            const lista = bloque.querySelector(".categoria-lista");
            const items = lista.querySelectorAll("li");
            let visibles = 0;

            items.forEach(li => {
                const coincide = !termino || li.dataset.nombre.includes(termino);
                li.classList.toggle("oculto", !coincide);
                if (coincide) visibles++;
            });

            bloque.classList.toggle("oculto", Boolean(termino) && visibles === 0);
            if (termino && visibles > 0) {
                lista.classList.remove("oculto");
            }
        });
    }

    filtroArbol.addEventListener("input", aplicarFiltroArbol);

    function formatearIdentificador(nombre) {
    if (/^[A-Z][A-Z0-9_$]*$/.test(nombre)) {
        return nombre;
    }
    return `"${nombre.replace(/"/g, "\"\"")}"`;
}

async function verDatosObjeto(nombre) {
    editorSql.value = `SELECT * FROM ${formatearIdentificador(nombre)} ROWS 1 TO 100;`;
    await ejecutarSql(false);
}

    async function cargarDdl(tipo, nombre) {
        mostrarCargaResultados(true, `Extrayendo DDL de ${nombre}...`);
        try {
            const url = `/api/ddl?tipo=${encodeURIComponent(tipo)}&nombre=${encodeURIComponent(nombre)}`;
            const data = await solicitarApi(url);
            editorSql.value = data.ddl;
            actualizarEstado(`DDL generado desde RDB$ para: ${nombre}`, "ok");
        } catch (err) {
            actualizarEstado("Error DDL: " + err.message, "error");
            mostrarToast(err.message, "error");
        } finally {
            mostrarCargaResultados(false);
        }
    }

    async function ejecutarSql(esScript) {
        if (!conectado) {
            mostrarToast("Primero conéctate a una base de datos.", "error");
            abrirModal(modalConexion);
            return;
        }

        const inicio = editorSql.selectionStart;
        const fin = editorSql.selectionEnd;
        let sql = inicio !== fin ? editorSql.value.substring(inicio, fin) : editorSql.value;
        sql = sql.trim();
        if (!sql) {
            mostrarToast("Escribe una sentencia SQL para ejecutar.", "error");
            return;
        }

        const botonActivo = esScript ? btnScript : btnEjecutar;
        setBotonCargando(botonActivo, true, esScript ? "Ejecutando..." : "Ejecutando...");
        btnEjecutar.disabled = true;
        btnScript.disabled = true;
        mostrarCargaResultados(true, esScript ? "Ejecutando script en InterBase..." : "Ejecutando consulta SQL...");
        actualizarEstado("Ejecutando en InterBase...");

        try {
            const data = await solicitarApi("/api/ejecutar", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ sql, esScript })
            });

            if (typeof data.duracionMs === "number") {
                tiempoEjecucion.textContent = `${data.duracionMs} ms`;
                tiempoEjecucion.classList.remove("oculto");
            }

            if (data.esConsulta) {
                renderizarTabla(data.columnas || [], data.filas || []);
            } else {
                renderizarTabla([], []);
                mostrarToast(data.mensaje, "ok");
                await cargarCatalogo();
            }
            actualizarEstado(data.mensaje, "ok");
        } catch (err) {
            actualizarEstado("Error SQL: " + err.message, "error");
            mostrarToast(err.message, "error");
        } finally {
            setBotonCargando(botonActivo, false);
            btnEjecutar.disabled = false;
            btnScript.disabled = false;
            mostrarCargaResultados(false);
        }
    }

    function renderizarTabla(columnas, filas) {
        ultimasColumnas = columnas;
        ultimasFilas = filas;

        const thead = tablaResultados.querySelector("thead");
        const tbody = tablaResultados.querySelector("tbody");
        thead.textContent = "";
        tbody.textContent = "";
        contadorResultados.textContent = `${filas.length} filas`;
        btnExportarCsv.classList.toggle("oculto", filas.length === 0);

        if (!columnas.length) {
            estadoVacioResultados.classList.remove("oculto");
            return;
        }

        estadoVacioResultados.classList.add("oculto");

        const trHead = document.createElement("tr");
        columnas.forEach(col => {
            const th = document.createElement("th");
            th.textContent = col;
            trHead.appendChild(th);
        });
        thead.appendChild(trHead);

        const fragmento = document.createDocumentFragment();
        filas.forEach(fila => {
            const tr = document.createElement("tr");
            columnas.forEach(col => {
                const td = document.createElement("td");
                const val = fila[col];
                if (val === null || val === undefined) {
                    td.textContent = "NULL";
                    td.className = "celda-null";
                } else {
                    td.textContent = String(val);
                }
                td.title = "Clic para copiar";
                td.addEventListener("click", () => copiarTexto(td.textContent));
                tr.appendChild(td);
            });
            fragmento.appendChild(tr);
        });
        tbody.appendChild(fragmento);
    }

    async function copiarTexto(texto) {
        try {
            await navigator.clipboard.writeText(texto);
            mostrarToast("Valor copiado al portapapeles.");
        } catch {
            actualizarEstado("No se pudo copiar al portapapeles.");
        }
    }

    function exportarCsv() {
        if (!ultimasColumnas.length) return;
        const escaparCsv = valor => {
            const texto = valor === null || valor === undefined ? "" : String(valor);
            if (/[",\n\r]/.test(texto)) {
                return `"${texto.replace(/"/g, "\"\"")}"`;
            }
            return texto;
        };

        const lineas = [ultimasColumnas.map(escaparCsv).join(",")];
        ultimasFilas.forEach(fila => {
            lineas.push(ultimasColumnas.map(col => escaparCsv(fila[col])).join(","));
        });

        const blob = new Blob(["\uFEFF" + lineas.join("\r\n")], { type: "text/csv;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement("a");
        enlace.href = url;
        enlace.download = "dbara_resultados.csv";
        enlace.click();
        URL.revokeObjectURL(url);
        mostrarToast("Resultados exportados a CSV.", "ok");
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

        if (!definiciones.length) {
            mostrarToast("Agrega al menos una columna válida.", "error");
            return;
        }

        if (pks.length) {
            definiciones.push(`PRIMARY KEY (${pks.join(", ")})`);
        }

        editorSql.value = `CREATE TABLE ${nombreTabla} (\n    ${definiciones.join(",\n    ")}\n);`;
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

        editorSql.value = `CREATE VIEW ${nombreVista} AS\n${select};`;
        cerrarModales();
        await ejecutarSql(false);
    });

    function obtenerPerfiles() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
        } catch {
            return [];
        }
    }

    function guardarPerfiles(lista) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(lista));
    }

    function cargarSelectPerfiles(indiceSeleccionado = "") {
        const perfiles = obtenerPerfiles();
        selectPerfil.textContent = "";

        const optNuevo = document.createElement("option");
        optNuevo.value = "";
        optNuevo.textContent = "-- Nuevo perfil --";
        selectPerfil.appendChild(optNuevo);

        perfiles.forEach((p, idx) => {
            const opt = document.createElement("option");
            opt.value = String(idx);
            opt.textContent = `${p.nombre} (${p.usuario}@${p.host})`;
            selectPerfil.appendChild(opt);
        });

        selectPerfil.value = String(indiceSeleccionado);
    }

    function limpiarFormularioConexion() {
        document.getElementById("conNombre").value = "";
        document.getElementById("conHost").value = "";
        document.getElementById("conPuerto").value = "3050";
        document.getElementById("conRuta").value = "";
        document.getElementById("conUsuario").value = "";
        document.getElementById("conClave").value = "";
    }

    selectPerfil.addEventListener("change", () => {
        const idx = selectPerfil.value;
        if (idx === "") {
            limpiarFormularioConexion();
            return;
        }
        const p = obtenerPerfiles()[Number(idx)];
        if (!p) return;
        document.getElementById("conNombre").value = p.nombre;
        document.getElementById("conHost").value = p.host;
        document.getElementById("conPuerto").value = p.puerto;
        document.getElementById("conRuta").value = p.ruta;
        document.getElementById("conUsuario").value = p.usuario;
        document.getElementById("conClave").value = p.clave;
    });

    document.getElementById("btnGuardarPerfil").addEventListener("click", () => {
        const perfil = {
            nombre: document.getElementById("conNombre").value.trim() || "Sin nombre",
            host: document.getElementById("conHost").value.trim(),
            puerto: document.getElementById("conPuerto").value.trim() || "3050",
            ruta: document.getElementById("conRuta").value.trim(),
            usuario: document.getElementById("conUsuario").value.trim(),
            clave: document.getElementById("conClave").value
        };
        if (!perfil.host || !perfil.ruta || !perfil.usuario) {
            mostrarToast("Completa host, ruta y usuario antes de guardar.", "error");
            return;
        }
        const perfiles = obtenerPerfiles();
        const idx = selectPerfil.value;
        let nuevoIndice;
        if (idx !== "") {
            nuevoIndice = Number(idx);
            perfiles[nuevoIndice] = perfil;
        } else {
            perfiles.push(perfil);
            nuevoIndice = perfiles.length - 1;
        }
        guardarPerfiles(perfiles);
        cargarSelectPerfiles(String(nuevoIndice));
        actualizarEstado(`Perfil "${perfil.nombre}" guardado.`, "ok");
        mostrarToast(`Perfil "${perfil.nombre}" guardado.`, "ok");
    });

    document.getElementById("btnEliminarPerfil").addEventListener("click", () => {
        const idx = selectPerfil.value;
        if (idx === "") return;
        const perfiles = obtenerPerfiles();
        const eliminado = perfiles[Number(idx)];
        perfiles.splice(Number(idx), 1);
        guardarPerfiles(perfiles);
        cargarSelectPerfiles("");
        limpiarFormularioConexion();
        actualizarEstado("Perfil eliminado.");
        if (eliminado) {
            mostrarToast(`Perfil "${eliminado.nombre}" eliminado.`);
        }
    });

    editorSql.addEventListener("keydown", e => {
        if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
            e.preventDefault();
            ejecutarSql(false);
        }
    });

    let arrastrando = false;
    divisorHorizontal.addEventListener("mousedown", e => {
        e.preventDefault();
        arrastrando = true;
        divisorHorizontal.classList.add("arrastrando");
        document.body.style.cursor = "row-resize";
    });

    document.addEventListener("mousemove", e => {
        if (!arrastrando) return;
        const rect = zonaEditor.parentElement.getBoundingClientRect();
        const nuevaAltura = e.clientY - rect.top;
        const minimo = 120;
        const maximo = rect.height - 140;
        if (nuevaAltura >= minimo && nuevaAltura <= maximo) {
            zonaEditor.style.height = `${nuevaAltura}px`;
        }
    });

    document.addEventListener("mouseup", () => {
        if (!arrastrando) return;
        arrastrando = false;
        divisorHorizontal.classList.remove("arrastrando");
        document.body.style.cursor = "";
    });

    cargarSelectPerfiles();
});