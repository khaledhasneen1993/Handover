package com.khaled.handover.data

import java.util.UUID

/** Stable domain values are never translated or used as UI labels. */
object Phase { const val BASELINE = "BASELINE"; const val RETURN = "RETURN" }
object Progress { const val DRAFT = "DRAFT"; const val BASELINE_DONE = "BASELINE_DONE"; const val AWAITING_RETURN = "AWAITING_RETURN"; const val RETURN_DONE = "RETURN_DONE"; const val ARCHIVED = "ARCHIVED" }
object Capture { const val NOT_CAPTURED = "NOT_CAPTURED"; const val CAPTURED = "CAPTURED"; const val NOT_APPLICABLE = "NOT_APPLICABLE"; const val SKIPPED = "SKIPPED" }
object Assessment { const val NONE = "NONE"; const val NO_VISIBLE_DIFFERENCE = "NO_VISIBLE_DIFFERENCE"; const val PRE_EXISTING = "PRE_EXISTING"; const val RETURN_DIFFERENCE = "RETURN_DIFFERENCE"; const val CANNOT_COMPARE = "CANNOT_COMPARE" }

/** No professional 'inspection score': captured and skipped counts stay distinct. */
data class CaptureCounts(val captured: Int, val skipped: Int, val notApplicable: Int, val pending: Int) {
    val total: Int get() = captured + skipped + notApplicable + pending
}
fun countStatuses(statuses: List<String>): CaptureCounts = CaptureCounts(
    statuses.count { it == Capture.CAPTURED }, statuses.count { it == Capture.SKIPPED },
    statuses.count { it == Capture.NOT_APPLICABLE }, statuses.count { it == Capture.NOT_CAPTURED }
)

fun nextStatus(current: String, completedPhase: String): String = when {
    current == Progress.ARCHIVED -> Progress.ARCHIVED
    completedPhase == Phase.RETURN && current in setOf(Progress.BASELINE_DONE, Progress.AWAITING_RETURN, Progress.RETURN_DONE) -> Progress.RETURN_DONE
    current == Progress.DRAFT && completedPhase == Phase.BASELINE -> Progress.BASELINE_DONE
    else -> current
}

data class PointSpec(val key: String, val group: String, val label: String, val hint: String)

object Templates {
    val vehicle = listOf(
        PointSpec("front", "EXTERIOR", "Front", "Photograph the entire front"),
        PointSpec("rear", "EXTERIOR", "Rear", "Photograph the entire rear"),
        PointSpec("right", "EXTERIOR", "Right side", "Photograph the whole right side"),
        PointSpec("left", "EXTERIOR", "Left side", "Photograph the whole left side"),
        PointSpec("windshield", "EXTERIOR", "Windshield", "Include all corners"),
        PointSpec("wheels", "EXTERIOR", "Wheels and tires", "Capture tire condition and rims"),
        PointSpec("cabin", "INTERIOR", "Interior", "Show dashboard and cabin"),
        PointSpec("seats", "INTERIOR", "Seats", "Show every seat"),
        PointSpec("odometer", "ACCESSORIES", "Odometer", "Make reading visible"),
        PointSpec("fuel", "ACCESSORIES", "Fuel or battery", "Capture level"),
        PointSpec("keys", "ACCESSORIES", "Keys and accessories", "Group all accessories"),
        PointSpec("extra", "OTHER", "Additional area", "Photograph another relevant area")
    )
    val device = listOf(
        PointSpec("front", "BODY", "Front", "Photograph the complete front"),
        PointSpec("back", "BODY", "Back", "Photograph the complete back"),
        PointSpec("edges", "BODY", "Edges", "Show all edges"),
        PointSpec("screen", "BODY", "Screen", "Show screen on and off if possible"),
        PointSpec("ports", "BODY", "Ports", "Capture any visible damage"),
        PointSpec("charger", "ACCESSORIES", "Charger and cable", "Include supplied accessories"),
        PointSpec("operation", "CONDITION", "Operating condition", "Document any displayed errors"),
        PointSpec("fault", "CONDITION", "Reported fault", "Photograph the reported issue")
    )
    fun apartment(roomNames: List<String>): List<PointSpec> = roomNames.flatMapIndexed { roomIndex, room ->
        listOf("Walls", "Floor", "Doors and windows", "Furniture and appliances", "Meters", "Keys and accessories")
            .mapIndexed { i, label -> PointSpec("room_${roomIndex}_$i", room, "$room · $label", "Photograph $label") }
    }
    fun forCategory(category: String, rooms: List<String> = listOf("Living room", "Kitchen", "Bathroom")): List<PointSpec> = when (category) {
        "VEHICLE" -> vehicle
        "DEVICE" -> device
        "APARTMENT" -> apartment(rooms)
        else -> emptyList()
    }
}
fun newId(): String = UUID.randomUUID().toString()
