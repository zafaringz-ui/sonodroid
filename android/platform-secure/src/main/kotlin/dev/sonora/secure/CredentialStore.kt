package dev.sonora.secure

/**
 * Provider credentials on top of [SecretsVault].
 *
 * Keys are namespaced by provider slug; there is deliberately no API to enumerate
 * secrets. Callers get a value or nothing, and only redacted text ever reaches the
 * UI or the logs.
 */
class CredentialStore(
    private val vault: SecretsVault,
) {
    fun save(providerSlug: String, credential: String) {
        vault.put(keyFor(providerSlug), credential)
    }

    fun load(providerSlug: String): String? = vault.get(keyFor(providerSlug))

    fun has(providerSlug: String): Boolean = !load(providerSlug).isNullOrBlank()

    fun clear(providerSlug: String) = vault.remove(keyFor(providerSlug))

    fun clearAll() = vault.clear()

    /** Safe for Settings and logcat. */
    fun redacted(providerSlug: String): String? =
        load(providerSlug)?.let(::describe)

    private fun describe(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "«empty»"
        return "${trimmed.take(4)}…(${trimmed.length} chars)"
    }

    private fun keyFor(providerSlug: String) = "credential.$providerSlug"
}
