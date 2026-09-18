/**
 * Impresión de tickets por id de elemento. Vive aquí (y no junto al
 * componente TicketPreview) para no romper Fast Refresh: un archivo que
 * exporta componentes no debe exportar otras funciones.
 */
export function printTicketById(id: string = "ticket-preview") {
  const el = document.getElementById(id);
  if (!el) return;

  // Intento iframe oculto: no bloquea la ventana principal y no requiere popup.
  // Si el navegador bloquea iframe print, fallback a window.open con cierre seguro.
  const html = `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Ticket</title><style>
    @page { size: 80mm; margin: 2mm; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; color: #000; background:#fff; }
    table { width: 100%; border-collapse: collapse; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
  </style></head><body>${el.outerHTML}</body></html>`;

  try {
    const iframe = document.createElement("iframe");
    iframe.style.position = "fixed";
    iframe.style.right = "0";
    iframe.style.bottom = "0";
    iframe.style.width = "0";
    iframe.style.height = "0";
    iframe.style.border = "0";
    iframe.setAttribute("aria-hidden", "true");
    document.body.appendChild(iframe);

    const doc = iframe.contentWindow?.document;
    if (!doc) throw new Error("no-iframe-doc");
    doc.open();
    doc.write(html);
    doc.close();

    const cleanup = () => {
      window.setTimeout(() => {
        if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
      }, 500);
    };

    const doPrint = () => {
      try {
        iframe.contentWindow?.focus();
        iframe.contentWindow?.print();
      } catch {
        // fallback silencioso
      }
      // onafterprint no es fiable si el usuario cancela; limpiamos en ambos casos
      cleanup();
      // Doble seguridad: si el navegador no dispara afterprint, limpiar tras 2s
      window.setTimeout(cleanup, 2000);
    };

    // Esperar a que imágenes/fuentes carguen
    const img = doc.images;
    if (img.length === 0) {
      window.setTimeout(doPrint, 250);
    } else {
      let loaded = 0;
      const check = () => { loaded++; if (loaded >= img.length) window.setTimeout(doPrint, 100); };
      Array.from(img).forEach((i) => {
        if ((i as HTMLImageElement).complete) check();
        else { i.addEventListener("load", check); i.addEventListener("error", check); }
      });
      window.setTimeout(doPrint, 1500);
    }

    // Seguridad: si el usuario cancela, el iframe se limpia igual y la pantalla NO queda pasmada
    iframe.contentWindow?.addEventListener("afterprint", cleanup);
    return;
  } catch {
    // Fallback ventana nueva con cierre garantizado aunque se cancele
  }

  const w = window.open("", "_blank", "width=400,height=700");
  if (!w) return;
  w.document.write(`<html lang="es"><head><meta charset="utf-8"><title>Ticket</title><style>
    @page { size: 80mm; margin: 2mm; }
    body { margin: 0; font-family: ui-monospace, monospace; }
    table { width: 100%; border-collapse: collapse; }
  </style></head><body>${el.outerHTML}<script>
    function done(){ try{window.close();}catch(e){} }
    window.onafterprint = done;
    window.onload = function(){ window.print(); setTimeout(done, 1000); };
    setTimeout(done, 3000);
  </script></body></html>`);
  w.document.close();
}
