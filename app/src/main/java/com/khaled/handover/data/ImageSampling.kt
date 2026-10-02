package com.khaled.handover.data

/**
 * Smallest power-of-two Android BitmapFactory inSampleSize that bounds BOTH
 * decoded dimensions. Avoid full-resolution decoding on the UI thread and
 * avoid integer overflow on corrupted or malicious image headers.
 */
fun boundedSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(maxEdge > 0) { "maxEdge must be positive" }
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while ((width.toLong() > maxEdge.toLong() * sample ||
            height.toLong() > maxEdge.toLong() * sample) && sample <= (1 shl 29)) {
        sample *= 2
    }
    return sample
}
