import com.khaled.handover.data.*

fun assertStatus(expected: String, actual: String) = check(expected == actual) { "$expected != $actual" }

val counts = countStatuses(listOf(Capture.CAPTURED, Capture.SKIPPED, Capture.NOT_APPLICABLE, Capture.NOT_CAPTURED, Capture.CAPTURED))
check(counts.captured == 2 && counts.skipped == 1 && counts.notApplicable == 1 && counts.pending == 1 && counts.total == 5)
assertStatus(Progress.BASELINE_DONE, nextStatus(Progress.DRAFT, Phase.BASELINE))
assertStatus(Progress.RETURN_DONE, nextStatus(Progress.AWAITING_RETURN, Phase.RETURN))
assertStatus(Progress.ARCHIVED, nextStatus(Progress.ARCHIVED, Phase.RETURN))
listOf("VEHICLE", "DEVICE", "APARTMENT").forEach { kind ->
    val template = Templates.forCategory(kind)
    check(template.isNotEmpty() && template.map { it.key }.distinct().size == template.size)
}
check(Templates.apartment(listOf("Bedroom", "Kitchen")).any { it.label.startsWith("Bedroom") })
val testCases = listOf(Triple(0 to 200, 800, 1), Triple(800 to 750, 800, 1),
    Triple(1500 to 500, 800, 2), Triple(6000 to 4000, 800, 8),
    Triple(12000 to 9000, 800, 16), Triple(Int.MAX_VALUE to Int.MAX_VALUE, 800, 1 shl 22))
testCases.forEach { (dimensions, limit, expected) ->
    val sample = boundedSampleSize(dimensions.first, dimensions.second, limit)
    check(sample == expected) { "Sampling $dimensions at $limit got $sample vs $expected" }
    check(dimensions.first <= 0 || (dimensions.first.toLong() <= sample.toLong() * limit && dimensions.second.toLong() <= sample.toLong() * limit))
}
try { boundedSampleSize(100, 100, 0); error("Non-positive image limit accepted") } catch (_: IllegalArgumentException) { }
println("PASS: counts (5 states), state machine (3 paths), all templates, 6 image sample cases + invalid limit")
