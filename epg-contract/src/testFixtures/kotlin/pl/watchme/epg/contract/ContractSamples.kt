package pl.watchme.epg.contract

object ContractSamples {
    const val CHANNELS = "contract/channels.json"
    const val OPERATORS = "contract/operators.json"
    const val GUIDE = "contract/epg-tvp-1.json"

    fun read(path: String): String =
        checkNotNull(ContractSamples::class.java.classLoader.getResource(path)) { "Missing sample $path" }.readText()
}
