package br.com.rgservices.jeeplauncher.media

import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Serviço exigido pelo Android para que o aplicativo tenha acesso às sessões de
 * mídia de outros aplicativos.
 *
 * O [MediaSessionManager] só entrega sessões a quem possui um listener de
 * notificações habilitado pelo usuário; este serviço é esse componente. Ele não
 * processa notificação alguma: a descoberta e o acompanhamento de sessões são
 * feitos por [MediaSessionHelper], com callbacks ligados ao ciclo de vida da tela.
 */
class MediaSessionListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaSessionListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Acesso a notificações concedido; sessões de mídia disponíveis")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Acesso a notificações revogado; sessões de mídia indisponíveis")
    }
}
