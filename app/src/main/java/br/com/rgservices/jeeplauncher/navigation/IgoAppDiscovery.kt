package br.com.rgservices.jeeplauncher.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Descobre instalações compatíveis com o provedor iGO sem fixar um package name.
 *
 * Critério conservador: aplicativos com atividade lançável (MAIN/LAUNCHER) cujo
 * rótulo visível ou nome do pacote contenham "igo" (case-insensitive).
 * Google Maps, Waze e o próprio launcher são excluídos.
 *
 * A classe apenas lista candidatos; a associação ao iGO ocorre somente quando o
 * usuário confirma a escolha — nenhum aplicativo é associado automaticamente.
 */
class IgoAppDiscovery(private val context: Context) {

    data class IgoCandidate(
        val packageName: String,
        val label: String
    )

    fun discover(): List<IgoCandidate> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info -> mapCandidate(info, packageManager) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun mapCandidate(
        info: ResolveInfo,
        packageManager: PackageManager
    ): IgoCandidate? {
        val packageName = info.activityInfo?.packageName ?: return null
        if (packageName in EXCLUDED_PACKAGES) return null
        if (packageName == context.packageName) return null

        val label = info.loadLabel(packageManager).toString().trim()
        val matchesLabel = label.contains(KEYWORD, ignoreCase = true)
        val matchesPackage = packageName.contains(KEYWORD, ignoreCase = true)

        return if (matchesLabel || matchesPackage) {
            IgoCandidate(packageName, label.ifBlank { packageName })
        } else {
            null
        }
    }

    companion object {
        private const val KEYWORD = "igo"
        private val EXCLUDED_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.waze"
        )
    }
}
