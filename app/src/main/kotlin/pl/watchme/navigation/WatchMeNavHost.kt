package pl.watchme.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import pl.watchme.feature.lineup.LineupRoute
import pl.watchme.feature.ranking.RankingRoute

@Serializable
data object LineupDestination

@Serializable
data object RankingDestination

@Composable
fun WatchMeNavHost(start: StartDestination, navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = when (start) {
            StartDestination.LINEUP -> LineupDestination
            StartDestination.RANKING -> RankingDestination
        },
    ) {
        composable<LineupDestination> {
            LineupRoute(
                onSaved = {
                    navController.navigate(RankingDestination) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<RankingDestination> {
            RankingRoute(onEditChannels = { navController.navigate(LineupDestination) })
        }
    }
}
