package com.willfp.libreforge

import org.bukkit.Bukkit
import org.bukkit.Location

// Chunk sampling step (blocks) for the region-ownership walk along a ray.
private const val CHUNK_STEP = 16.0

// True only if every chunk the ray can touch is owned by the current region thread.
// We sample along the ray one chunk at a time (plus the exact endpoint), each check
// covering a [radiusChunks] square radius to absorb the ray-trace AABB expansion
// (raySize). A Folia region is a contiguous block of chunks, so sampling each chunk
// plus the endpoint is sufficient to prove the whole ray stays region-local. If any
// sample is cross-region we must NOT ray-trace, because moonrise's tick-thread check
// would log-then-throw before we could catch it.
//
// The ray direction is read from [origin]'s yaw/pitch, so callers whose ray direction
// differs from the origin's facing must pass a Location whose direction has been set to
// the ray direction (Location.setDirection).
internal fun rayIsRegionLocal(origin: Location, maxDistance: Double, radiusChunks: Int): Boolean {
    val direction = origin.direction
    var travelled = 0.0
    while (travelled < maxDistance) {
        val point = origin.clone().add(direction.clone().multiply(travelled))
        if (!Bukkit.isOwnedByCurrentRegion(point, radiusChunks)) {
            return false
        }
        travelled += CHUNK_STEP
    }
    val end = origin.clone().add(direction.clone().multiply(maxDistance))
    return Bukkit.isOwnedByCurrentRegion(end, radiusChunks)
}
