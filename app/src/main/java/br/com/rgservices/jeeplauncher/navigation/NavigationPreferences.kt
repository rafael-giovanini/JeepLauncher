package br.com.rgservices.jeeplauncher.navigation

import android.content.Context

/**
 * Persiste a preferência de navegação usando SharedPreferences.
 * A escolha é restaurada automaticamente na próxima abertura do launcher.
 *
 * Além do provedor selecionado, guarda o package name da instalação de iGO
 * escolhida pelo usuário: o iGO não tem pacote fixo conhecido, então a
 * identificação é definida somente quando o usuário seleciona um aplicativo
 * compatível (ver [IgoAppDiscovery]).
 */
class NavigationPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelected(): NavigationProvider =
        NavigationProvider.fromKey(prefs.getString(KEY_PROVIDER, null))

    fun setSelected(provider: NavigationProvider) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider.key)
            .apply()
    }

    /** Pacote da instalação de iGO escolhida; nulo quando ainda não definido. */
    fun getIgoPackage(): String? = prefs.getString(KEY_IGO_PACKAGE, null)

    /** Persiste o pacote de iGO escolhido. Passar nulo limpa a associação. */
    fun setIgoPackage(packageName: String?) {
        prefs.edit()
            .putString(KEY_IGO_PACKAGE, packageName)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "jeep_launcher_prefs"
        private const val KEY_PROVIDER = "selected_nav_provider"
        private const val KEY_IGO_PACKAGE = "selected_igo_package"
    }
}
