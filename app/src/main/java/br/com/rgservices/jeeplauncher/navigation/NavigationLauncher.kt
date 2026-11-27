package br.com.rgservices.jeeplauncher.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Resultado seguro da tentativa de abrir o aplicativo de navegação. */
sealed interface LaunchResult {
    /** Aplicativo aberto com sucesso. */
    object Launched : LaunchResult

    /** Nenhum aplicativo correspondente encontrado, não instalado ou sem atividade lançável. */
    data class Unavailable(val provider: NavigationProvider) : LaunchResult

    /** Falha inesperada ao abrir (nunca derruba a Activity). */
    data class Failed(val provider: NavigationProvider, val reason: String) : LaunchResult
}

/**
 * Lógica de abertura dos aplicativos externos, separada da tela de seleção.
 *
 * - Google Maps e Waze usam os pacotes fixos definidos em [NavigationProvider].
 * - O iGO recebe o pacote escolhido em [explicitPackage] (descoberto por
 *   [IgoAppDiscovery] e persistido por NavigationPreferences).
 * A checagem explícita de instalação e a flag SINGLE_TOP evitam falhas e
 * múltiplas instâncias desnecessárias.
 */
class NavigationLauncher(private val context: Context) {

    /** Verifica de forma explícita se o pacote existe e é visível nesta central. */
    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(provider: NavigationProvider, explicitPackage: String? = null): LaunchResult {
        val packageName = when (provider) {
            NavigationProvider.GOOGLE_MAPS, NavigationProvider.WAZE -> provider.packageName
            NavigationProvider.IGO -> explicitPackage
        }

        if (packageName.isNullOrBlank()) {
            return LaunchResult.Unavailable(provider)
        }
        if (!isInstalled(packageName)) {
            return LaunchResult.Unavailable(provider)
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.Unavailable(provider)

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launchIntent)
            LaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Unavailable(provider)
        } catch (e: Exception) {
            LaunchResult.Failed(provider, e.javaClass.simpleName)
        }
    }
}
