package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class VaultParametersProvider(private val connector: VaultConnector) : AbstractBuildParametersProvider() {
    companion object {
        val LOG = Logger.getInstance(VaultParametersProvider::class.java.name)!!
    }

    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()
        val feature = build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyMap()
        val wrapped: String
        val settings = VaultFeatureSettings(feature.parameters)
        if (emulationMode) {
            wrapped = VaultConstants.SPECIAL_EMULATTED
        } else {
            wrapped = try {
                connector.requestWrappedToken(build, settings)
            } catch(e: VaultConnector.ConnectionException) {
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", e.message))
                VaultConstants.SPECIAL_FAILED_TO_FETCH
            } catch(e: Throwable) {
                val message = "Failed to fetch Vault wrapped token: ${e.message}"
                LOG.warnAndDebugDetails(message, e)
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", e.message))
                VaultConstants.SPECIAL_FAILED_TO_FETCH
            }
        }
        return mapOf(VaultConstants.WRAPPED_TOKEN_PROPERTY to wrapped, VaultConstants.URL_PROPERTY to settings.url)
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()
        build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyList()
        return listOf(VaultConstants.AGENT_CONFIG_PROP, Constants.ENV_PREFIX + VaultConstants.AGENT_ENV_PROP)
    }
}

