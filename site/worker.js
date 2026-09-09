/**
 * owcompanion.bais.info — il sito vetrina.
 *
 * Due cose che una cartella di file statici non sa fare da sola:
 *
 *  - `/apk` porta all'ultima release pubblicata, qualunque essa sia. Il file vive
 *    su GitHub perché 35 MB non stanno negli asset di un Worker, e l'indirizzo
 *    cambia a ogni versione: scritto a mano nella pagina, sarebbe sbagliato il
 *    giorno dopo ogni rilascio.
 *  - versione e peso nella pagina vengono dalla stessa risposta. Il sito non può
 *    dire 1.11 mentre il pulsante scarica la 1.12.
 *
 * La chiamata a GitHub è messa in cache un'ora. Senza, ogni visita ne consumerebbe
 * una delle sessanta all'ora che GitHub concede senza autenticazione, e la
 * sessantunesima visita vedrebbe un sito senza numeri.
 */

const RELEASE = "https://api.github.com/repos/bdbais/ow-companion/releases/latest";
const RELEASES = "https://github.com/bdbais/ow-companion/releases";
const CACHE_SECONDS = 3600;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/apk" || url.pathname === "/apk/") {
      const release = await latest(ctx);
      // Senza risposta utile si va all'elenco delle release: una pagina in più da
      // cui scaricare è meglio di un errore in faccia.
      return Response.redirect(release?.apk ?? RELEASES, 302);
    }

    const response = await env.ASSETS.fetch(request);

    // I segnaposto stanno solo nella pagina, e solo lì vale la pena rileggerla.
    const type = response.headers.get("content-type") ?? "";
    if (!type.includes("text/html")) return response;

    const release = await latest(ctx);
    const page = (await response.text())
      .replaceAll("{{versione}}", release?.version ?? "—")
      .replaceAll("{{peso}}", release?.size ?? "");

    return new Response(page, {
      status: response.status,
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "public, max-age=300",
        // Il sito è testo e immagini proprie: niente script, niente incorniciature
        // altrui, niente moduli. Dichiararlo costa una riga.
        "content-security-policy":
          "default-src 'self'; img-src 'self' data:; style-src 'self'; " +
          "script-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
        "referrer-policy": "strict-origin-when-cross-origin",
        "x-content-type-options": "nosniff",
      },
    });
  },
};

/** Tag, indirizzo dell'APK e peso dell'ultima release, o null se GitHub non risponde. */
async function latest(ctx) {
  const cache = caches.default;
  const key = new Request(RELEASE, { headers: { accept: "application/vnd.github+json" } });

  // Il corpo si legge una volta sola e si tiene come testo. Il giro naturale -
  // clonare la risposta, darne una copia alla cache e leggere l'altra - qui
  // falliva in silenzio: la chiamata a GitHub tornava 200 e la pagina restava
  // coi segnaposto, perché l'errore finiva dritto nel catch qui sotto.
  let body = null;

  const hit = await cache.match(key);
  if (hit) {
    body = await hit.text();
  } else {
    const response = await fetch(key, {
      headers: {
        accept: "application/vnd.github+json",
        // GitHub rifiuta le chiamate senza: meglio dire chi siamo.
        "user-agent": "owcompanion-bais-info",
      },
    });
    if (!response.ok) return null;
    body = await response.text();
    ctx.waitUntil(
      cache.put(
        key,
        new Response(body, {
          headers: {
            "content-type": "application/json",
            "cache-control": `public, max-age=${CACHE_SECONDS}`,
          },
        }),
      ),
    );
  }

  try {
    const data = JSON.parse(body);
    const asset = (data.assets ?? []).find((a) => a.name?.endsWith(".apk"));
    return {
      version: (data.tag_name ?? "").replace(/^v/, "") || null,
      apk: asset?.browser_download_url ?? RELEASES,
      size: asset ? `${(asset.size / 1048576).toFixed(1).replace(".", ",")} MB` : "",
    };
  } catch {
    return null;
  }
}
