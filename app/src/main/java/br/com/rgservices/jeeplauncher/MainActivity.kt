package br.com.rgservices.jeeplauncher

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import br.com.rgservices.jeeplauncher.navigation.IgoAppDiscovery
import br.com.rgservices.jeeplauncher.navigation.LaunchResult
import br.com.rgservices.jeeplauncher.navigation.NavigationLauncher
import br.com.rgservices.jeeplauncher.navigation.NavigationPreferences
import br.com.rgservices.jeeplauncher.navigation.NavigationProvider
import br.com.rgservices.jeeplauncher.ui.ClockController
import br.com.rgservices.jeeplauncher.ui.MediaPanelController
import br.com.rgservices.jeeplauncher.ui.ShortcutController

/**
 * Activity principal do Jeep Launcher.
 *
 * A Activity apenas orquestra os componentes: a lógica de relógio, atalhos,
 * mídia e navegação fica em classes dedicadas (pacotes ui/ e navigation/),
 * conforme a organização evita concentrar toda a interface em uma única classe.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var preferences: NavigationPreferences
    private lateinit var launcher: NavigationLauncher
    private lateinit var igoDiscovery: IgoAppDiscovery
    private lateinit var clock: ClockController
    private lateinit var mediaPanel: MediaPanelController

    private lateinit var providerRows: Map<NavigationProvider, LinearLayout>
    private lateinit var providerIcons: Map<NavigationProvider, ImageView>
    private lateinit var providerChecks: Map<NavigationProvider, ImageView>

    private var selectedProvider: NavigationProvider = NavigationProvider.GOOGLE_MAPS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        preferences = NavigationPreferences(this)
        launcher = NavigationLauncher(this)
        igoDiscovery = IgoAppDiscovery(this)
        selectedProvider = preferences.getSelected()

        applySystemBarsVisibility()
        setupWindowInsets()
        setupClock()
        setupProviderRows()
        setupOpenNavigationButton()

        val contentArea = findViewById<View>(R.id.content_area)
        ShortcutController(this).attach(contentArea)

        // Instância única: o ciclo de vida da tela é repassado ao controlador,
        // que registra e remove o listener de sessões de mídia.
        mediaPanel = MediaPanelController(this)
        mediaPanel.attach(contentArea)

        renderProviderSelection()
    }

    /**
     * Mantém a barra de status superior oculta enquanto o Jeep Launcher está em primeiro plano.
     * A barra de navegação inferior e os gestos do Android permanecem disponíveis:
     * navigationBars() nunca é ocultado. Compatível com minSdk 23 via androidx.core.
     * O comportamento "transient by swipe" evita que um gesto deixe a barra de status
     * permanentemente visível após reaparecer.
     */
    private fun applySystemBarsVisibility() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Reaplica a ocultação da barra de status sempre que a janela recupera o foco
     * (ex.: retorno de app externo, transição do sistema ou reexibição transitória).
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applySystemBarsVisibility()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupClock() {
        clock = ClockController(
            topTime = findViewById(R.id.top_time),
            topDate = findViewById(R.id.top_date),
            infoTime = findViewById(R.id.info_time),
            infoDate = findViewById(R.id.info_date)
        )
    }

    private fun setupProviderRows() {
        providerRows = mapOf(
            NavigationProvider.GOOGLE_MAPS to findViewById(R.id.provider_row_maps),
            NavigationProvider.WAZE to findViewById(R.id.provider_row_waze),
            NavigationProvider.IGO to findViewById(R.id.provider_row_igo)
        )
        providerIcons = mapOf(
            NavigationProvider.GOOGLE_MAPS to findViewById(R.id.provider_icon_maps),
            NavigationProvider.WAZE to findViewById(R.id.provider_icon_waze),
            NavigationProvider.IGO to findViewById(R.id.provider_icon_igo)
        )
        providerChecks = mapOf(
            NavigationProvider.GOOGLE_MAPS to findViewById(R.id.provider_check_maps),
            NavigationProvider.WAZE to findViewById(R.id.provider_check_waze),
            NavigationProvider.IGO to findViewById(R.id.provider_check_igo)
        )

        providerRows.forEach { (provider, row) ->
            row.setOnClickListener { selectProvider(provider) }
        }
    }

    private fun selectProvider(provider: NavigationProvider) {
        selectedProvider = provider
        preferences.setSelected(provider)
        renderProviderSelection()
    }

    /** Aplica o destaque visual ao provedor selecionado (cartão laranja + check + ícone laranja). */
    private fun renderProviderSelection() {
        providerRows.forEach { (provider, row) ->
            val selected = provider == selectedProvider
            row.setBackgroundResource(if (selected) R.drawable.bg_card_selected else R.drawable.bg_card)

            providerIcons[provider]?.let { icon ->
                icon.imageTintList = ContextCompat.getColorStateList(
                    this,
                    if (selected) R.color.jeep_orange else R.color.jeep_text_secondary
                )
            }

            providerChecks[provider]?.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun setupOpenNavigationButton() {
        findViewById<View>(R.id.nav_open_button).setOnClickListener { openSelectedNavigation() }
    }

    /** Mapeia o provedor selecionado para a abertura correspondente. */
    private fun openSelectedNavigation() {
        when (selectedProvider) {
            NavigationProvider.GOOGLE_MAPS, NavigationProvider.WAZE ->
                launchKnownProvider(selectedProvider)

            NavigationProvider.IGO ->
                openIgoNavigation()
        }
    }

    /** Maps e Waze usam pacotes fixos: aviso claro quando não estão instalados. */
    private fun launchKnownProvider(provider: NavigationProvider) {
        when (val result = launcher.launch(provider)) {
            is LaunchResult.Launched ->
                showToast(getString(R.string.nav_launch_ok, provider.displayName))

            is LaunchResult.Unavailable ->
                showToast(getString(R.string.nav_launch_uninstalled, provider.displayName))

            is LaunchResult.Failed ->
                showToast(getString(R.string.nav_launch_unavailable, provider.displayName))
        }
    }

    /**
     * iGO não tem pacote fixo: reaproveita a instalação persistida quando ainda
     * está instalada; caso contrário descobre candidatos e só abre após o usuário
     * confirmar a escolha (não há associação automática).
     */
    private fun openIgoNavigation() {
        val savedPackage = preferences.getIgoPackage()
        if (!savedPackage.isNullOrBlank() && launcher.isInstalled(savedPackage)) {
            launchIgoPackage(savedPackage)
            return
        }

        val candidates = igoDiscovery.discover()
        if (candidates.isEmpty()) {
            showToast(R.string.nav_igo_not_found)
            return
        }
        showIgoChooser(candidates)
    }

    private fun launchIgoPackage(packageName: String) {
        when (val result = launcher.launch(NavigationProvider.IGO, packageName)) {
            is LaunchResult.Launched ->
                showToast(getString(R.string.nav_launch_ok, NavigationProvider.IGO.displayName))

            else ->
                showToast(R.string.nav_igo_not_found)
        }
    }

    /** Diálogo simples com os candidatos iGO encontrados; a escolha fica persistida. */
    private fun showIgoChooser(candidates: List<IgoAppDiscovery.IgoCandidate>) {
        val labels = candidates.map { "${it.label}  (${it.packageName})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.nav_igo_pick_title)
            .setItems(labels) { _, which ->
                val chosen = candidates[which]
                preferences.setIgoPackage(chosen.packageName)
                showToast(getString(R.string.nav_igo_picked, chosen.label))
                launchIgoPackage(chosen.packageName)
            }
            .setOnCancelListener { showToast(R.string.nav_igo_none_selected) }
            .show()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        clock.start()
        // Reavalia autorização e sessões a cada retorno (inclusive ao voltar das
        // configurações de acesso a notificações).
        mediaPanel.onResume()
    }

    override fun onPause() {
        super.onPause()
        clock.stop()
        mediaPanel.onPause()
    }

    override fun onDestroy() {
        mediaPanel.onDestroy()
        super.onDestroy()
    }
}
