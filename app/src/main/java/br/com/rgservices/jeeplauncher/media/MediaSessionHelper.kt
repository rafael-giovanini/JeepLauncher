package br.com.rgservices.jeeplauncher.media

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Ponte entre o sistema de mídia do Android e o painel Música do launcher.
 *
 * Responsabilidades:
 *  - verificar se o componente [MediaSessionListenerService] está habilitado como
 *    listener de notificações (é isso que autoriza ler sessões de outros apps);
 *  - consultar as sessões ativas via [MediaSessionManager.getActiveSessions];
 *  - escolher a sessão mais adequada e acompanhá-la por [MediaController.Callback];
 *  - observar o aparecimento/desaparecimento de sessões com
 *    [MediaSessionManager.addOnActiveSessionsChangedListener];
 *  - enviar comandos de transporte respeitando as ações anunciadas pela sessão.
 *
 * Todo callback entregue a [onPanelChanged] acontece na thread principal. A classe
 * nunca mantém referência à Activity: [requestNotificationAccess] recebe a Activity
 * apenas no momento da chamada.
 */
class MediaSessionHelper(context: Context) {

    companion object {
        private const val TAG = "MediaSessionHelper"

        /**
         * Nome da configuração que lista os listeners habilitados. A constante
         * equivalente de Settings.Secure é interna do framework (não exportada no
         * SDK público), por isso é referenciada pelo nome.
         */
        private const val SETTING_ENABLED_NOTIFICATION_LISTENERS =
            "enabled_notification_listeners"
    }

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaSessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    /** Componente que precisa estar habilitado em "Acesso a notificações". */
    private val listenerComponent =
        ComponentName(appContext, MediaSessionListenerService::class.java)

    /** Notifica o painel de que a sessão selecionada ou seus dados mudaram. */
    var onPanelChanged: (() -> Unit)? = null

    var activeController: MediaController? = null
        private set

    private var controllerCallback: MediaController.Callback? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var released = false

    /**
     * A autorização é por componente, não por pacote: ter qualquer outro serviço
     * do aplicativo habilitado não basta. A partir do Android 8.1 o sistema expõe
     * uma consulta dedicada; nas versões anteriores, a lista de listeners
     * habilitados é lida de Settings e comparada pelo ComponentName completo.
     */
    val isNotificationAccessGranted: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                return notificationManager.isNotificationListenerAccessGranted(listenerComponent)
            }

            val enabled = Settings.Secure.getString(
                appContext.contentResolver,
                SETTING_ENABLED_NOTIFICATION_LISTENERS
            ) ?: return false

            return enabled.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
                .any { it == listenerComponent }
        }

    /** Abre a tela de acesso a notificações do Android. O usuário decide quando. */
    fun requestNotificationAccess(activity: Activity) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Tela de acesso a notificações indisponível neste dispositivo", e)
        }
    }

    /**
     * Inicia o acompanhamento: registra o listener de sessões (se autorizado) e
     * faz a primeira avaliação. Chamado em onResume da Activity.
     */
    fun start() {
        if (released) return
        registerSessionsListener()
        refresh()
    }

    /** Remove o listener de sessões. Chamado em onPause da Activity. */
    fun stop() {
        unregisterSessionsListener()
    }

    /** Libera tudo. Chamado em onDestroy da Activity. */
    fun release() {
        released = true
        unregisterSessionsListener()
        detachController()
        onPanelChanged = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun registerSessionsListener() {
        if (sessionsListener != null) return
        if (!isNotificationAccessGranted) return

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (released) return@OnActiveSessionsChangedListener
            // O sistema já entrega a lista atualizada: reaproveitamos em vez de
            // consultar de novo, evitando uma janela de inconsistência.
            applySelection(selectBest(controllers.orEmpty()))
            dispatch()
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                listener,
                listenerComponent,
                mainHandler
            )
            sessionsListener = listener
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem autorização para acompanhar sessões de mídia", e)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar listener de sessões", e)
        }
    }

    private fun unregisterSessionsListener() {
        val listener = sessionsListener ?: return
        sessionsListener = null
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao remover listener de sessões", e)
        }
    }

    /**
     * Reavalia a lista de sessões ativas, troca o controlador se necessário e
     * avisa o painel. Pode ser chamado a qualquer momento (onResume, retorno das
     * configurações, sessão destruída).
     */
    fun refresh() {
        if (released) return
        applySelection(selectBest(queryActiveSessions()))
        dispatch()
    }

    private fun queryActiveSessions(): List<MediaController> {
        if (!isNotificationAccessGranted) return emptyList()
        return try {
            mediaSessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            Log.w(TAG, "Consulta de sessões negada pelo sistema", e)
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao consultar sessões de mídia", e)
            emptyList()
        }
    }

    /**
     * Prioridade: sessão efetivamente em reprodução; depois a primeira sessão com
     * estado de reprodução válido; por último a primeira disponível.
     */
    private fun selectBest(sessions: List<MediaController>): MediaController? {
        if (sessions.isEmpty()) return null

        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.let { return it }

        sessions.firstOrNull { it.playbackState != null }
            ?.let { return it }

        return sessions.firstOrNull()
    }

    /**
     * Anexa o controlador escolhido, trocando o callback quando a sessão muda.
     * Se a sessão é a mesma, mantém o registro existente para não perder eventos.
     */
    private fun applySelection(controller: MediaController?) {
        val current = activeController

        if (controller != null && current != null &&
            controller.sessionToken == current.sessionToken
        ) {
            activeController = controller
            return
        }

        detachController()
        activeController = controller ?: return

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = dispatch()

            override fun onMetadataChanged(metadata: MediaMetadata?) = dispatch()

            override fun onSessionEvent(event: String, extras: Bundle?) = dispatch()

            override fun onSessionDestroyed() {
                // A sessão morreu: nada dela pode ser lido a partir daqui.
                refresh()
            }
        }

        try {
            controller.registerCallback(callback, mainHandler)
            controllerCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar callback da sessão", e)
        }
    }

    private fun detachController() {
        val controller = activeController
        val callback = controllerCallback
        activeController = null
        controllerCallback = null

        if (controller != null && callback != null) {
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao remover callback da sessão", e)
            }
        }
    }

    /** Publica a atualização sempre pela thread principal, fora do callback de origem. */
    private fun dispatch() {
        if (released) return
        mainHandler.post {
            if (!released) onPanelChanged?.invoke()
        }
    }

    // ---------------------------------------------------------------------
    // Consulta de estado e comandos de transporte
    // ---------------------------------------------------------------------

    fun isPlaying(): Boolean {
        val state = activeController?.playbackState?.state ?: return false
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    fun canPlayPause(): Boolean {
        val actions = activeController?.playbackState?.actions ?: return false
        return actions and PlaybackState.ACTION_PLAY != 0L ||
            actions and PlaybackState.ACTION_PAUSE != 0L ||
            actions and PlaybackState.ACTION_PLAY_PAUSE != 0L
    }

    fun canSkipPrevious(): Boolean = supportsAction(PlaybackState.ACTION_SKIP_TO_PREVIOUS)

    fun canSkipNext(): Boolean = supportsAction(PlaybackState.ACTION_SKIP_TO_NEXT)

    private fun supportsAction(action: Long): Boolean {
        val state = activeController?.playbackState ?: return false
        return state.actions and action != 0L
    }

    /**
     * Alterna reprodução/pausa respeitando as ações anunciadas.
     *
     * Observação de API: o [MediaController.TransportControls] público não expõe
     * um método playPause(). Quando a sessão anuncia apenas ACTION_PLAY_PAUSE, o
     * comando é traduzido para pause()/play() conforme o estado atual — é o mesmo
     * caminho que o framework usa para resolver essa ação. Sem ação compatível,
     * nada é enviado.
     */
    fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState ?: return

        when (state.state) {
            PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> return
        }

        val actions = state.actions
        val hasPlay = actions and PlaybackState.ACTION_PLAY != 0L
        val hasPause = actions and PlaybackState.ACTION_PAUSE != 0L
        val hasPlayPause = actions and PlaybackState.ACTION_PLAY_PAUSE != 0L

        val canPause = hasPause || hasPlayPause
        val canPlay = hasPlay || hasPlayPause

        val playing = state.state == PlaybackState.STATE_PLAYING ||
            state.state == PlaybackState.STATE_BUFFERING

        try {
            if (playing && canPause) {
                controller.transportControls.pause()
            } else if (!playing && canPlay) {
                controller.transportControls.play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao alternar reprodução", e)
        }
    }

    fun skipToPrevious() {
        val controller = activeController ?: return
        if (!supportsAction(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) return
        try {
            controller.transportControls.skipToPrevious()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao voltar faixa", e)
        }
    }

    fun skipToNext() {
        val controller = activeController ?: return
        if (!supportsAction(PlaybackState.ACTION_SKIP_TO_NEXT)) return
        try {
            controller.transportControls.skipToNext()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao avançar faixa", e)
        }
    }
}
