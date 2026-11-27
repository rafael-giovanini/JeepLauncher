package br.com.rgservices.jeeplauncher.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import br.com.rgservices.jeeplauncher.R

/**
 * Controla os seis atalhos da barra inferior.
 *
 * Nesta etapa, os atalhos sem integração implementada exibem uma mensagem
 * provisória via [Toast]; nenhum toque tenta acessar recursos indisponíveis,
 * de forma que o aplicativo nunca é encerrado por erro.
 */
class ShortcutController(private val context: Context) {

    private data class Shortcut(
        val viewId: Int,
        val labelRes: Int
    )

    private val shortcuts = listOf(
        Shortcut(R.id.shortcut_navigation, R.string.shortcut_navigation),
        Shortcut(R.id.shortcut_radio, R.string.shortcut_radio),
        Shortcut(R.id.shortcut_music, R.string.shortcut_music),
        Shortcut(R.id.shortcut_phone, R.string.shortcut_phone),
        Shortcut(R.id.shortcut_bluetooth, R.string.shortcut_bluetooth),
        Shortcut(R.id.shortcut_apps, R.string.shortcut_apps)
    )

    /** Associa os listeners aos atalhos encontrados em [root]. */
    fun attach(root: View) {
        shortcuts.forEach { shortcut ->
            root.findViewById<View>(shortcut.viewId)?.setOnClickListener {
                Toast.makeText(
                    context,
                    context.getString(R.string.shortcut_provisional, context.getString(shortcut.labelRes)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
