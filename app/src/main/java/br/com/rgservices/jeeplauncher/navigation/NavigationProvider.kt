package br.com.rgservices.jeeplauncher.navigation

/**
 * Provedores de navegação disponíveis nesta etapa.
 *
 * A seleção do provedor (esta enum) é intencionalmente separada da lógica de
 * abertura dos aplicativos (ver [NavigationLauncher]).
 *
 * O iGO não possui um nome de pacote fixo conhecido para esta central; por isso
 * [packageName] é nulo e a abertura deve ser resolvida em etapas futuras.
 */
enum class NavigationProvider(
    val key: String,
    val displayName: String,
    val description: String,
    val packageName: String?
) {
    GOOGLE_MAPS("google_maps", "Google Maps", "Mapas e rotas do Google", "com.google.android.apps.maps"),
    WAZE("waze", "Waze", "Trânsito em tempo real", "com.waze"),
    IGO("igo", "iGO", "Navegação offline", null);

    companion object {
        /** Provedor exibido na primeira execução, quando não há preferência salva. */
        const val DEFAULT_KEY = "google_maps"

        fun fromKey(key: String?): NavigationProvider =
            values().firstOrNull { it.key == key } ?: GOOGLE_MAPS
    }
}
