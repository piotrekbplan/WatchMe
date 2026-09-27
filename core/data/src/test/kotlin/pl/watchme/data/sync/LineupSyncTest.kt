package pl.watchme.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.data.auth.StoredSession
import pl.watchme.data.fakes.FakeLineupDao
import pl.watchme.data.fakes.FakeSessionStore
import pl.watchme.data.mapper.toEntity
import pl.watchme.data.repository.LineupRepositoryImpl
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.PackageRef

class LineupSyncTest {

    private val older = ChannelLineup(setOf(ChannelId("tvp-1")), null, Instant.parse("2026-09-28T08:00:00Z"))
    private val newer = ChannelLineup(
        setOf(ChannelId("hbo"), ChannelId("tvn")),
        PackageRef("play", "play-max"),
        Instant.parse("2026-09-28T09:00:00Z"),
    )

    @Test
    fun `document round trip keeps channels source and time`() {
        val document = FirestoreLineupMapper.toDocument(newer)

        assertThat(FirestoreLineupMapper.toDomain(document)).isEqualTo(newer)
        assertThat(document.fields.getValue("channelIds").arrayValue?.values?.map { it.stringValue })
            .isEqualTo(listOf("hbo", "tvn"))
    }

    @Test
    fun `lineup without source omits operator fields`() {
        val document = FirestoreLineupMapper.toDocument(older)

        assertThat(document.fields.keys).isEqualTo(setOf("channelIds", "updatedAt"))
        assertThat(FirestoreLineupMapper.toDomain(document)?.source).isNull()
    }

    @Test
    fun `firestore json with an empty array and extra metadata is understood`() {
        val json = """
            {"name":"projects/p/databases/(default)/documents/users/u/settings/lineup",
             "fields":{"channelIds":{"arrayValue":{}},"updatedAt":{"timestampValue":"2026-09-28T09:00:00.123456Z"}},
             "createTime":"2026-09-28T09:00:00Z"}
        """.trimIndent()

        val lineup = FirestoreLineupMapper.toDomain(FirestoreJson.decodeFromString(FirestoreDocument.serializer(), json))

        assertThat(lineup?.channelIds).isEqualTo(emptySet())
        assertThat(lineup?.updatedAt).isEqualTo(Instant.parse("2026-09-28T09:00:00.123456Z"))
    }

    @Test
    fun `document without timestamp is ignored`() {
        assertThat(FirestoreLineupMapper.toDomain(FirestoreDocument())).isNull()
    }

    @Test
    fun `merge prefers the later change and pushes when remote is missing`() {
        assertThat(LineupMerge.resolve(null, null)).isEqualTo(MergeAction.None)
        assertThat(LineupMerge.resolve(null, newer)).isEqualTo(MergeAction.TakeRemote(newer))
        assertThat(LineupMerge.resolve(older, null)).isEqualTo(MergeAction.Push(older))
        assertThat(LineupMerge.resolve(older, newer)).isEqualTo(MergeAction.TakeRemote(newer))
        assertThat(LineupMerge.resolve(newer, older)).isEqualTo(MergeAction.Push(newer))
        assertThat(LineupMerge.resolve(newer, newer)).isEqualTo(MergeAction.None)
    }

    @Test
    fun `saving marks the lineup dirty and schedules a sync`() = runTest {
        val env = Env(signedIn = true)

        env.repository.save(older)

        assertThat(env.dao.row.value?.dirty).isEqualTo(true)
        assertThat(env.scheduler.scheduled).isEqualTo(1)
    }

    @Test
    fun `sync without a session keeps everything local`() = runTest {
        val env = Env(signedIn = false)
        env.repository.save(older)

        assertThat(env.repository.sync()).isEqualTo(Outcome.Success(Unit))
        assertThat(env.remote.pushed).isEmpty()
    }

    @Test
    fun `sync pushes a newer local lineup and marks it clean`() = runTest {
        val env = Env(signedIn = true)
        env.remote.stored["uid-1"] = older
        env.repository.save(newer)

        assertThat(env.repository.sync()).isEqualTo(Outcome.Success(Unit))
        assertThat(env.remote.pushed).containsExactly(newer)
        assertThat(env.dao.row.value?.dirty).isEqualTo(false)
    }

    @Test
    fun `sync adopts a newer remote lineup`() = runTest {
        val env = Env(signedIn = true)
        env.remote.stored["uid-1"] = newer
        env.repository.save(older)

        env.repository.sync()

        assertThat(env.dao.row.value).isEqualTo(newer.toEntity())
        assertThat(env.remote.pushed).isEmpty()
    }

    @Test
    fun `first sign in on a new phone downloads the lineup`() = runTest {
        val env = Env(signedIn = true)
        env.remote.stored["uid-1"] = newer

        env.repository.sync()

        assertThat(env.dao.row.value?.channelIds).isEqualTo("hbo,tvn")
    }

    @Test
    fun `offline sync keeps the lineup dirty`() = runTest {
        val env = Env(signedIn = true)
        env.remote.failure = IOException("offline")
        env.repository.save(newer)

        assertThat(env.repository.sync()).isEqualTo(Outcome.Failure(DomainError.Network))
        assertThat(env.dao.row.value?.dirty).isEqualTo(true)
    }

    @Test
    fun `rejected sync is unauthorized`() = runTest {
        val env = Env(signedIn = true)
        env.remote.failure = FirestoreRejectedException(403)

        assertThat(env.repository.sync()).isEqualTo(Outcome.Failure(DomainError.Unauthorized))
    }

    @Test
    fun `a change saved during a push stays dirty`() = runTest {
        val env = Env(signedIn = true)
        env.repository.save(older)
        env.remote.onPush = { env.dao.row.value = newer.toEntity().copy(dirty = true) }

        env.repository.sync()

        assertThat(env.dao.row.value?.dirty).isEqualTo(true)
    }

    @Test
    fun `clear removes the local lineup`() = runTest {
        val env = Env(signedIn = true)
        env.repository.save(older)

        env.repository.clear()

        assertThat(env.dao.row.value).isNull()
    }

    @Test
    fun `timestamps in documents use the contract format`() {
        assertThat(FirestoreLineupMapper.toDocument(older).fields.getValue("updatedAt").timestampValue)
            .isEqualTo("2026-09-28T08:00:00Z")
        assertThat(FirestoreLineupMapper.toDocument(older).fields.containsKey("operatorId")).isFalse()
    }

    private class Env(signedIn: Boolean) {
        val dao = FakeLineupDao()
        val remote = FakeLineupRemoteSource()
        val scheduler = RecordingScheduler()
        val sessions = FakeSessionStore(
            if (signedIn) StoredSession("uid-1", "jan@example.com", "id", "refresh", Long.MAX_VALUE) else null,
        )
        val repository = LineupRepositoryImpl(dao, remote, sessions, scheduler)
    }

    private class RecordingScheduler : LineupSyncScheduler {
        var scheduled = 0

        override fun schedule() {
            scheduled++
        }
    }

    private class FakeLineupRemoteSource : LineupRemoteSource {
        val stored = mutableMapOf<String, ChannelLineup>()
        val pushed = mutableListOf<ChannelLineup>()
        var failure: Exception? = null
        var onPush: () -> Unit = {}

        override suspend fun fetch(uid: String): ChannelLineup? {
            failure?.let { throw it }
            return stored[uid]
        }

        override suspend fun push(uid: String, lineup: ChannelLineup) {
            failure?.let { throw it }
            pushed += lineup
            stored[uid] = lineup
            onPush()
        }
    }
}
