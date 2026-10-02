import com.khaled.handover.data.*

fun main() {
    val statuses = listOf(Capture.CAPTURED,Capture.CAPTURED,Capture.SKIPPED,Capture.NOT_APPLICABLE,Capture.NOT_CAPTURED)
    val counts = countStatuses(statuses)
    check(counts.captured == 2 && counts.skipped == 1 && counts.notApplicable == 1 && counts.pending == 1)
    check(counts.total == 5)
    check(nextStatus(Progress.DRAFT, Phase.RETURN)==Progress.DRAFT)
    check(nextStatus(Progress.DRAFT, Phase.BASELINE)==Progress.BASELINE_DONE)
    check(nextStatus(Progress.AWAITING_RETURN, Phase.RETURN)==Progress.RETURN_DONE)
    check(nextStatus(Progress.ARCHIVED,Phase.RETURN)==Progress.ARCHIVED)
    listOf("VEHICLE","DEVICE","APARTMENT").forEach { category ->
        val points=Templates.forCategory(category)
        check(points.isNotEmpty())
        check(points.size == points.map { it.key }.toSet().size)
    }
    check(Templates.apartment(listOf("Bedroom","Kitchen")).size==12)
    check(Templates.vehicle.size >= 12 && Templates.device.size >= 8)
    println("PASS: 12 standalone model invariants (status counts, lifecycle, snapshots templates)")
}
