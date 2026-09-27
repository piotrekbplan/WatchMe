package pl.watchme.domain.model

import java.time.Instant

data class PackageRef(val operatorId: String, val packageId: String)

data class ChannelLineup(
    val channelIds: Set<ChannelId>,
    val source: PackageRef?,
    val updatedAt: Instant,
) {
    val isEmpty: Boolean get() = channelIds.isEmpty()

    fun applyPackage(operator: TvOperator, pkg: ChannelPackage, now: Instant): ChannelLineup =
        ChannelLineup(pkg.channelIds.toSet(), PackageRef(operator.id, pkg.id), now)

    fun toggle(id: ChannelId, now: Instant): ChannelLineup =
        copy(channelIds = if (id in channelIds) channelIds - id else channelIds + id, updatedAt = now)

    companion object {
        fun empty(now: Instant) = ChannelLineup(emptySet(), null, now)
    }
}
