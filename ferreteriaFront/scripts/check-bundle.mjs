// Presupuesto de bundle (corre tras `bun run build`).
// Falla con mensaje claro si el JS servido engorda sin revisión.
// Umbrales con holgura ~15% sobre lo medido el 2026-10-10:
//   index (chunk inicial: react+motion+sonner+DataTable…) gzip 162.9 kB
//   total dist/assets gzip 868.8 kB (xlsx y recharts van en chunks lazy).
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

const DIR = new URL("../dist/assets/", import.meta.url);
const LIMITE_INDEX_GZIP_KB = 190;
const LIMITE_TOTAL_GZIP_KB = 1000;

let total = 0;
let peor = { archivo: "", kb: 0 };
for (const f of readdirSync(DIR)) {
	if (!f.endsWith(".js")) continue;
	const bytes = statSync(join(DIR.pathname, f)).size;
	const gz = gzipSync(readFileSync(join(DIR.pathname, f)));
	const kb = gz.length / 1024;
	total += kb;
	if (f.startsWith("index-")) peor = { archivo: f, kb };
	console.log(`${f}: ${(bytes / 1024).toFixed(1)} kB (gzip ${kb.toFixed(1)} kB)`);
}

let ok = true;
if (peor.kb > LIMITE_INDEX_GZIP_KB) {
	console.error(
		`BUDGET: ${peor.archivo} gzip ${peor.kb.toFixed(1)} kB > ${LIMITE_INDEX_GZIP_KB} kB. ` +
			"Revisa imports al chunk compartido (solo lo común va en index).",
	);
	ok = false;
}
if (total > LIMITE_TOTAL_GZIP_KB) {
	console.error(
		`BUDGET: total gzip ${total.toFixed(1)} kB > ${LIMITE_TOTAL_GZIP_KB} kB. ` +
			"Considera lazy() en la ruta nueva o partir el chunk.",
	);
	ok = false;
}
console.log(`Total gzip: ${total.toFixed(1)} kB`);
if (!ok) process.exit(1);
