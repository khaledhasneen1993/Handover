package com.khaled.handover.backup

/** Only app-owned, precisely named restore scratch files may be removed at startup. */
object RestoreScratchPolicy {
    private val stage = Regex("restore-[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")
    private val scratch = Regex("handover-(?:incoming|decrypted)-[0-9]+\\.(?:bak|zip)")
    fun isStage(name: String): Boolean = stage.matches(name)
    fun isScratch(name: String): Boolean = scratch.matches(name)
}
