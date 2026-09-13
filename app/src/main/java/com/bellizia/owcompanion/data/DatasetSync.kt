package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tiene aggiornati i numeri degli eroi, senza chiedere niente a nessuno.
 *
 * Il controllo della versione dell'app e questo sono due cose diverse, e vanno
 * trattate diversamente. Una versione nuova dell'app la deve installare una
 * persona, quindi si mostra un avviso e si aspetta. I dati no: sono la stessa
 * app con numeri piu' recenti, e chiedere il permesso di correggere un danno
 * sbagliato non e' rispetto, e' un fastidio.
 *
 * Prima esisteva solo come pulsante nella schermata Info. Chi non ci passava -
 * cioe' quasi tutti - restava coi numeri del giorno in cui aveva installato,
 * anche a mesi di distanza.
 *
 * Tre regole, le stesse dell'avviso di aggiornamento:
 *
 *  - Non blocca mai niente: gira sullo sfondo e nessuna schermata lo aspetta.
 *  - Un fallimento e' silenzio. Senza rete, col server giu', con una risposta
 *    incomprensibile: l'app ha gia' tutto dentro di se' e continua come prima.
 *  - Una volta per avvio, e non piu' di una volta al giorno. Il controllo costa
 *    poche centinaia di byte, ma ripeterlo a ogni rotazione dello schermo
 *    sarebbe traffico speso per niente.
 *
 * Il file nuovo viene letto dal prossimo avvio: le repository lo preferiscono a
 * quello dentro l'APK, ma lo leggono all'apertura. Scambiarlo sotto i piedi a
 * un'app gia' avviata vorrebbe dire una schermata coi dati vecchi e quella
 * accanto coi nuovi.
 */
object DatasetSync {

    private const val PREFS = "dataset"
    private const val KEY_LAST = "last_check"
    private const val ONE_DAY = 24 * 60 * 60 * 1000L

    /** Vero se ha scaricato dati nuovi, che saranno attivi dal prossimo avvio. */
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        val updater = DatasetUpdater(context)
        if (!updater.isConfigured) return@withContext false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST, 0L)
        // Il confronto e' con un intervallo, non con "e' passato un giorno": un
        // orologio spostato indietro dava un valore negativo e bloccava il
        // controllo fino a che non tornava avanti.
        if (since in 0 until ONE_DAY) return@withContext false

        val installed = DatasetUpdater.installedVersion(context, BuildConfig.DATASET_VERSION)
        val result = runCatching { updater.check(installed) }.getOrNull()

        // L'ora si segna solo quando si e' parlato davvero col server. Se non
        // c'era rete, il prossimo avvio ci riprova subito invece di aspettare
        // un giorno per un tentativo che non c'e' mai stato.
        if (result != null && result !is DatasetUpdater.Result.Failed) {
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        }

        result is DatasetUpdater.Result.Updated
    }
}
