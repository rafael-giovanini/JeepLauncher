package br.com.rgservices.jeeplauncher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import br.com.rgservices.jeeplauncher.R
import br.com.rgservices.jeeplauncher.media.MediaSessionHelper
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Controla o painel Música: exibe a sessão de mídia ativa do sistema e traduz os
 * toques nos botões em comandos de transporte.
 *
 * O painel nunca é fonte de informação: título, artista, capa e estado vêm
 * exclusivamente de [MediaSessionHelper]. Quando não há sessão, ou quando o
 * acesso às notificações não foi concedido, o painel mostra o estado vazio
 * original — sem inventar conteúdo.
 */
class MediaPanelController(private val activity: Activity) {

    companion object {
        private const val TAG = "MediaPanel"
    }

    private val mediaHelper = MediaSessionHelper(activity)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Uma única thread para leitura de capas por URI; nada de I/O na thread de UI. */
    private val coverExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var attached = false
    private var destroyed = false

    /** Identifica a requisição de capa atual; respostas antigas são descartadas. */
    private var coverRequest = 0
    private var coverTargetPx = 0

    private lateinit var tvNoMedia: TextView
    private lateinit var tvNoMediaHint: TextView
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var ivAlbumCover: ImageView
    private lateinit var ivAlbumPlaceholder: ImageView
    private lateinit var btnPrev: View
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: View

    /** Liga o painel às views de [root]. Chamado uma única vez pela MainActivity. */
    fun attach(root: View) {
        if (attached) return
        attached = true

        tvNoMedia = root.findViewById(R.id.tv_no_media)
        tvNoMediaHint = root.findViewById(R.id.tv_no_media_hint)
        tvTrackTitle = root.findViewById(R.id.tv_track_title)
        tvArtist = root.findViewById(R.id.tv_artist)
        ivAlbumCover = root.findViewById(R.id.iv_album_cover)
        ivAlbumPlaceholder = root.findViewById(R.id.iv_album_placeholder)
        btnPrev = root.findViewById(R.id.media_prev)
        btnPlayPause = root.findViewById(R.id.media_play)
        btnNext = root.findViewById(R.id.media_next)

        coverTargetPx = activity.resources.getDimensionPixelSize(R.dimen.album_cover_size)

        btnPrev.setOnClickListener { mediaHelper.skipToPrevious() }
        btnPlayPause.setOnClickListener { mediaHelper.togglePlayPause() }
        btnNext.setOnClickListener { mediaHelper.skipToNext() }

        mediaHelper.onPanelChanged = { render() }
        render()
    }

    fun onResume() {
        if (destroyed) return
        mediaHelper.start()
    }

    fun onPause() {
        if (destroyed) return
        mediaHelper.stop()
    }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        mediaHelper.release()
        coverExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------------
    // Renderização
    // ------------------------------------------------------------------

    private fun render() {
        if (destroyed || !attached) return

        if (!mediaHelper.isNotificationAccessGranted) {
            renderAccessHint()
            return
        }

        val controller = mediaHelper.activeController
        if (controller == null) {
            renderEmpty()
            return
        }

        val metadata = try {
            controller.metadata
        } catch (e: Exception) {
            Log.w(TAG, "Sessão sem metadados legíveis", e)
            null
        }

        renderPlaying(metadata)
    }

    /** Sem autorização: explica o motivo e abre as configurações ao toque. */
    private fun renderAccessHint() {
        showTextBlock(
            title = activity.getString(R.string.media_need_access),
            hint = activity.getString(R.string.media_need_access_hint)
        )
        setAccessHintClickable(true)
        resetCover()
        updateControls(playPauseEnabled = false, prevEnabled = false, nextEnabled = false)
    }

    /** Autorizado, mas sem sessão de mídia: preserva o estado vazio original. */
    private fun renderEmpty() {
        showTextBlock(
            title = activity.getString(R.string.music_empty_title),
            hint = activity.getString(R.string.music_empty_hint)
        )
        setAccessHintClickable(false)
        resetCover()
        updateControls(playPauseEnabled = false, prevEnabled = false, nextEnabled = false)
    }

    private fun renderPlaying(metadata: MediaMetadata?) {
        tvNoMedia.visibility = View.GONE
        tvNoMediaHint.visibility = View.GONE
        setAccessHintClickable(false)

        tvTrackTitle.visibility = View.VISIBLE
        tvArtist.visibility = View.VISIBLE

        tvTrackTitle.text = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: activity.getString(R.string.media_unknown_track)

        tvArtist.text = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: activity.getString(R.string.media_unknown_artist)

        loadCover(metadata)

        val playing = mediaHelper.isPlaying()
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        btnPlayPause.contentDescription = activity.getString(
            if (playing) R.string.cd_media_pause else R.string.cd_media_play
        )

        updateControls(
            playPauseEnabled = mediaHelper.canPlayPause(),
            prevEnabled = mediaHelper.canSkipPrevious(),
            nextEnabled = mediaHelper.canSkipNext()
        )
    }

    private fun showTextBlock(title: String, hint: String) {
        tvNoMedia.text = title
        tvNoMedia.visibility = View.VISIBLE
        tvNoMediaHint.text = hint
        tvNoMediaHint.visibility = View.VISIBLE
        tvTrackTitle.visibility = View.GONE
        tvArtist.visibility = View.GONE
    }

    private fun setAccessHintClickable(clickable: Boolean) {
        val listener = if (clickable) {
            View.OnClickListener { mediaHelper.requestNotificationAccess(activity) }
        } else {
            null
        }
        tvNoMedia.setOnClickListener(listener)
        tvNoMedia.isClickable = clickable
        tvNoMediaHint.setOnClickListener(listener)
        tvNoMediaHint.isClickable = clickable
    }

    private fun updateControls(
        playPauseEnabled: Boolean,
        prevEnabled: Boolean,
        nextEnabled: Boolean
    ) {
        applyEnabled(btnPrev, prevEnabled)
        applyEnabled(btnPlayPause, playPauseEnabled)
        applyEnabled(btnNext, nextEnabled)
    }

    private fun applyEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.isClickable = enabled
        view.alpha = if (enabled) 1.0f else 0.4f
    }

    // ------------------------------------------------------------------
    // Capa do álbum
    // ------------------------------------------------------------------

    /**
     * Prioriza o bitmap já embutido nos metadados; se só houver URI, a leitura
     * acontece fora da thread principal e só é aplicada se ainda for a requisição
     * atual — evita que a capa de uma faixa anterior apareça na faixa seguinte.
     */
    private fun loadCover(metadata: MediaMetadata?) {
        val request = ++coverRequest

        val embedded = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (embedded != null) {
            showCover(embedded)
            return
        }

        resetCover()

        val uriString = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
        if (uriString.isNullOrBlank()) return

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.w(TAG, "URI de capa inválida: $uriString", e)
            return
        }

        try {
            coverExecutor.execute {
                val bitmap = decodeCover(uri)
                mainHandler.post {
                    if (destroyed || request != coverRequest) return@post
                    if (bitmap != null) showCover(bitmap) else resetCover()
                }
            }
        } catch (e: Exception) {
            // Executor encerrado: painel já destruído, nada a exibir.
            Log.d(TAG, "Leitura de capa ignorada após encerramento", e)
        }
    }

    private fun showCover(bitmap: Bitmap) {
        ivAlbumCover.setImageBitmap(bitmap)
        ivAlbumCover.visibility = View.VISIBLE
        ivAlbumPlaceholder.visibility = View.GONE
    }

    private fun resetCover() {
        ivAlbumCover.setImageDrawable(null)
        ivAlbumCover.visibility = View.GONE
        ivAlbumPlaceholder.visibility = View.VISIBLE
    }

    /** Decodifica com downsample para o tamanho real do painel, limitando a memória. */
    private fun decodeCover(uri: Uri): Bitmap? {
        return try {
            val resolver = activity.applicationContext.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            val target = coverTargetPx.coerceAtLeast(1)
            while (bounds.outWidth / (sampleSize * 2) >= target &&
                bounds.outHeight / (sampleSize * 2) >= target
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
