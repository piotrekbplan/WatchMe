package pl.watchme.domain.model

@JvmInline
value class ChannelId(val value: String)

data class Channel(
    val id: ChannelId,
    val name: String,
    val groupId: String,
    val groupName: String,
    val logoUrl: String?,
)

data class ChannelPackage(val id: String, val name: String, val channelIds: List<ChannelId>)

data class TvOperator(val id: String, val name: String, val packages: List<ChannelPackage>)

data class Catalog(val channels: List<Channel>, val operators: List<TvOperator>) {

    private val channelsById = channels.associateBy { it.id }

    fun channel(id: ChannelId): Channel? = channelsById[id]
}
