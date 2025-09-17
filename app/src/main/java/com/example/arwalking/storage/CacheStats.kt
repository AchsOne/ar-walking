package com.example.arwalking.storage

/**
 * Cache-Statistiken für Bitmap- und Thumbnail-Caches
 */
data class CacheStats(
    val bitmapCacheSize: Int,
    val bitmapCacheMaxSize: Int,
    val thumbnailCacheSize: Int,
    val thumbnailCacheMaxSize: Int,
    val bitmapCacheHitCount: Long,
    val bitmapCacheMissCount: Long,
    val thumbnailCacheHitCount: Long,
    val thumbnailCacheMissCount: Long
) {
    fun getBitmapHitRate(): Double {
        val total = bitmapCacheHitCount + bitmapCacheMissCount
        return if (total > 0) (bitmapCacheHitCount.toDouble() / total) * 100 else 0.0
    }

    fun getThumbnailHitRate(): Double {
        val total = thumbnailCacheHitCount + thumbnailCacheMissCount
        return if (total > 0) (thumbnailCacheHitCount.toDouble() / total) * 100 else 0.0
    }
}