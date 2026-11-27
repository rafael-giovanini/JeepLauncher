package br.com.rgservices.jeeplauncher.ui

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Atualiza o relógio e a data da barra superior e do painel de informações.
 *
 * Usa apenas um [Handler] na thread principal; não há serviços em segundo
 * plano nem processamento contínuo pesado. A atualização só grava no TextView
 * quando o valor muda, evitando trabalho desnecessário.
 */
class ClockController(
    private val topTime: TextView,
    private val topDate: TextView,
    private val infoTime: TextView,
    private val infoDate: TextView
) {

    private val handler = Handler(Looper.getMainLooper())
    private val date = Date()

    private val timeFormat = SimpleDateFormat("HH:mm", LOCALE)
    private val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", LOCALE)
    private val longDateFormat = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", LOCALE)

    private var lastTopTime = ""
    private var lastTopDate = ""
    private var lastInfoTime = ""
    private var lastInfoDate = ""

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    /** Inicia a atualização (chamar em onResume). */
    fun start() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Para a atualização (chamar em onPause). */
    fun stop() {
        handler.removeCallbacks(tick)
    }

    private fun update() {
        date.time = System.currentTimeMillis()

        val time = timeFormat.format(date)
        val shortDate = shortDateFormat.format(date)
        val longDate = longDateFormat.format(date).replaceFirstChar { it.titlecase() }

        if (time != lastTopTime) {
            topTime.text = time
            lastTopTime = time
        }
        if (shortDate != lastTopDate) {
            topDate.text = shortDate
            lastTopDate = shortDate
        }
        if (time != lastInfoTime) {
            infoTime.text = time
            lastInfoTime = time
        }
        if (longDate != lastInfoDate) {
            infoDate.text = longDate
            lastInfoDate = longDate
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 1000L
        private val LOCALE = Locale.forLanguageTag("pt-BR")
    }
}
