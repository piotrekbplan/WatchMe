package pl.watchme.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.repository.LineupRepository

sealed interface MergeAction {
    data object None : MergeAction
    data class Push(val lineup: ChannelLineup) : MergeAction
    data class TakeRemote(val lineup: ChannelLineup) : MergeAction
}

object LineupMerge {
    fun resolve(local: ChannelLineup?, remote: ChannelLineup?): MergeAction = when {
        local == null && remote == null -> MergeAction.None
        local == null -> MergeAction.TakeRemote(checkNotNull(remote))
        remote == null -> MergeAction.Push(local)
        remote.updatedAt.isAfter(local.updatedAt) -> MergeAction.TakeRemote(remote)
        local.updatedAt.isAfter(remote.updatedAt) -> MergeAction.Push(local)
        else -> MergeAction.None
    }
}

interface LineupSyncScheduler {
    fun schedule()
}

class WorkManagerLineupSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LineupSyncScheduler {

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<LineupSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "lineup-sync"
    }
}

@HiltWorker
class LineupSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val lineups: LineupRepository,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = when (val outcome = lineups.sync()) {
        is Outcome.Success -> Result.success()
        is Outcome.Failure -> if (outcome.error == DomainError.Network) Result.retry() else Result.failure()
    }
}
