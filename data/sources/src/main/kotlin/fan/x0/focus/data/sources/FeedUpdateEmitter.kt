package fan.x0.focus.data.sources

interface FeedUpdateEmitter {
    suspend fun feedUpdated(added: Int)
}
