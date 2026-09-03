import alerta from "./es/alerta";
import appshell from "./es/appshell";
import auth from "./es/auth";
import caja from "./es/caja";
import catalogo from "./es/catalogo";
import compras from "./es/compras";
import comun from "./es/comun";
import dashboard from "./es/dashboard";
import errores from "./es/errores";
import fiscal from "./es/fiscal";
import inventario from "./es/inventario";
import paginacion from "./es/paginacion";
import paginas from "./es/paginas";
import pos from "./es/pos";
import rango from "./es/rango";
import reportes from "./es/reportes";
import rrhh from "./es/rrhh";
import seguridad from "./es/seguridad";
import ventas from "./es/ventas";

const diccionario: Record<string, unknown> = {
	alerta,
	appshell,
	auth,
	caja,
	catalogo,
	compras,
	comun,
	dashboard,
	errores,
	fiscal,
	inventario,
	paginacion,
	paginas,
	pos,
	rango,
	reportes,
	rrhh,
	seguridad,
	ventas,
};

export default diccionario;
