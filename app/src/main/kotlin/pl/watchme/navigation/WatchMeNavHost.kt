package pl.watchme.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import pl.watchme.feature.auth.LoginRoute
import pl.watchme.feature.auth.RegisterRoute
import pl.watchme.feature.auth.ResetPasswordRoute
import pl.watchme.feature.lineup.LineupRoute
import pl.watchme.feature.ranking.RankingRoute
import pl.watchme.feature.settings.SettingsRoute

@Serializable
data object LoginDestination

@Serializable
data object RegisterDestination

@Serializable
data object ResetPasswordDestination

@Serializable
data object LineupDestination

@Serializable
data object RankingDestination

@Serializable
data object SettingsDestination

@Composable
fun WatchMeNavHost(
    start: StartDestination,
    sessionEnded: Flow<Unit>,
    appVersion: String,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(navController, sessionEnded) {
        sessionEnded.collect { navController.showSignIn() }
    }
    NavHost(
        navController = navController,
        startDestination = when (start) {
            StartDestination.LOGIN -> LoginDestination
            StartDestination.LINEUP -> LineupDestination
            StartDestination.RANKING -> RankingDestination
        },
    ) {
        composable<LoginDestination> {
            LoginRoute(
                onSignedIn = navController::showHome,
                onCreateAccount = { navController.navigate(RegisterDestination) },
                onForgotPassword = { navController.navigate(ResetPasswordDestination) },
            )
        }
        composable<RegisterDestination> {
            RegisterRoute(onSignedIn = navController::showHome, onBack = navController::popBackStack)
        }
        composable<ResetPasswordDestination> {
            ResetPasswordRoute(onBack = navController::popBackStack)
        }
        composable<LineupDestination> {
            LineupRoute(onSaved = { navController.replaceStack(RankingDestination) })
        }
        composable<RankingDestination> {
            RankingRoute(
                onOpenSettings = { navController.navigate(SettingsDestination) },
                onEditChannels = { navController.navigate(LineupDestination) },
            )
        }
        composable<SettingsDestination> {
            SettingsRoute(
                appVersion = appVersion,
                onBack = navController::popBackStack,
                onEditChannels = { navController.navigate(LineupDestination) },
                onSignedOut = navController::showSignIn,
            )
        }
    }
}

private fun NavHostController.showHome(hasChannels: Boolean) =
    replaceStack(if (hasChannels) RankingDestination else LineupDestination)

private fun NavHostController.showSignIn() {
    if (currentDestination?.hasRoute<LoginDestination>() == true) return
    replaceStack(LoginDestination)
}

private fun <T : Any> NavHostController.replaceStack(destination: T) = navigate(destination) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
