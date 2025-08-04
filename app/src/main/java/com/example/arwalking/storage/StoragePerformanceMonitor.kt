package com.example.arwalking.storage

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Überwacht die Performance der Speicher-Operationen
 * Verfolgt die Ziele aus STORAGE_ARCHITECTURE.md:
 * - Bild laden: 5-15ms
 * - Thumbnail: 1-3ms  
 * - Suche: <1ms
 * - Upload: 50-200ms
 */
class StoragePerformanceMonitor {
    
    private val TAG = "StoragePerformanceMonitor"
    
    // Performance-Metriken
    private val performanceMetrics = ConcurrentHashMap<String, MutableList<Long>>()
    private val _performanceStats = MutableStateFlow(PerformanceStats())
    val performanceStats: StateFlow<PerformanceStats> = _performanceStats
    
    // Aktuelle Operationen
    private val activeOperations = ConcurrentHashMap<String, Long>()
    
    companion object {
        // Operation-Types
        const val OP_LOAD_FULL_IMAGE = "load_full_image"
        const val OP_LOAD_THUMBNAIL = "load_thumbnail"
        const val OP_SEARCH_INDEX = "search_index"
        const val OP_SAVE_IMAGE = "save_image"
        const val OP_COMPRESS_IMAGE = "compress_image"
        const val OP_GENERATE_THUMBNAIL = "generate_thumbnail"
        const val OP_SAVE_METADATA = "save_metadata"
        const val OP_CACHE_HIT = "cache_hit"
        const val OP_CACHE_MISS = "cache_miss"
        
        // Performance-Ziele (in Millisekunden)
        private val PERFORMANCE_TARGETS = mapOf(
            OP_LOAD_FULL_IMAGE to 15L,
            OP_LOAD_THUMBNAIL to 3L,
            OP_SEARCH_INDEX to 1L,
            OP_SAVE_IMAGE to 200L,
            OP_COMPRESS_IMAGE to 200L,
            OP_GENERATE_THUMBNAIL to 50L,
            OP_SAVE_METADATA to 10L,
            OP_CACHE_HIT to 1L,
            OP_CACHE_MISS to 15L
        )
    }
    
    /**
     * Startet die Zeitmessung für eine Operation
     */
    fun startOperation(operationId: String, operationType: String): String {
        val fullOperationId = "${operationType}_${operationId}_${System.currentTimeMillis()}"
        activeOperations[fullOperationId] = System.currentTimeMillis()
        return fullOperationId
    }
    
    /**
     * Beendet die Zeitmessung für eine Operation
     */
    fun endOperation(fullOperationId: String): Long {
        val startTime = activeOperations.remove(fullOperationId) ?: return -1L
        val duration = System.currentTimeMillis() - startTime
        
        val operationType = fullOperationId.split("_")[0] + "_" + fullOperationId.split("_")[1]
        recordMetric(operationType, duration)
        
        return duration
    }
    
    /**
     * Misst die Ausführungszeit einer Operation
     */
    suspend fun <T> measureOperation(operationType: String, operationId: String = "", operation: suspend () -> T): T {
        var result: T
        val duration = measureTimeMillis {
            result = operation()
        }
        
        recordMetric(operationType, duration)
        
        // Warnung bei Performance-Problemen
        val target = PERFORMANCE_TARGETS[operationType]
        if (target != null && duration > target) {
            Log.w(TAG, "Performance-Ziel verfehlt: $operationType dauerte ${duration}ms (Ziel: ${target}ms)")
        }
        
        return result
    }
    
    /**
     * Zeichnet eine Metrik auf
     */
    internal fun recordMetric(operationType: String, duration: Long) {
        performanceMetrics.computeIfAbsent(operationType) { mutableListOf() }.add(duration)
        
        // Behalte nur die letzten 100 Messungen pro Operation
        val metrics = performanceMetrics[operationType]
        if (metrics != null && metrics.size > 100) {
            metrics.removeAt(0)
        }
        
        updatePerformanceStats()
    }
    
    /**
     * Aktualisiert die Performance-Statistiken
     */
    private fun updatePerformanceStats() {
        val stats = PerformanceStats()
        
        performanceMetrics.forEach { (operationType, durations) ->
            if (durations.isNotEmpty()) {
                val avg = durations.average()
                val min = durations.minOrNull() ?: 0L
                val max = durations.maxOrNull() ?: 0L
                val target = PERFORMANCE_TARGETS[operationType] ?: 0L
                val successRate = if (target > 0) {
                    durations.count { it <= target }.toDouble() / durations.size * 100
                } else 100.0
                
                val operationStats = OperationStats(
                    operationType = operationType,
                    averageDurationMs = avg,
                    minDurationMs = min,
                    maxDurationMs = max,
                    targetDurationMs = target,
                    successRate = successRate,
                    totalOperations = durations.size
                )
                
                when (operationType) {
                    OP_LOAD_FULL_IMAGE -> stats.loadFullImageStats = operationStats
                    OP_LOAD_THUMBNAIL -> stats.loadThumbnailStats = operationStats
                    OP_SEARCH_INDEX -> stats.searchIndexStats = operationStats
                    OP_SAVE_IMAGE -> stats.saveImageStats = operationStats
                    OP_COMPRESS_IMAGE -> stats.compressImageStats = operationStats
                    OP_GENERATE_THUMBNAIL -> stats.generateThumbnailStats = operationStats
                    OP_SAVE_METADATA -> stats.saveMetadataStats = operationStats
                    OP_CACHE_HIT -> stats.cacheHitStats = operationStats
                    OP_CACHE_MISS -> stats.cacheMissStats = operationStats
                }
            }
        }
        
        // Berechne Cache-Hit-Rate
        val cacheHits = performanceMetrics[OP_CACHE_HIT]?.size ?: 0
        val cacheMisses = performanceMetrics[OP_CACHE_MISS]?.size ?: 0
        val totalCacheOperations = cacheHits + cacheMisses
        
        stats.cacheHitRate = if (totalCacheOperations > 0) {
            cacheHits.toDouble() / totalCacheOperations * 100
        } else 0.0
        
        // Berechne Gesamt-Performance-Score
        stats.overallPerformanceScore = calculateOverallScore(stats)
        
        _performanceStats.value = stats
    }
    
    /**
     * Berechnet einen Gesamt-Performance-Score (0-100)
     */
    private fun calculateOverallScore(stats: PerformanceStats): Double {
        val operationStats = listOf(
            stats.loadFullImageStats,
            stats.loadThumbnailStats,
            stats.searchIndexStats,
            stats.saveImageStats,
            stats.compressImageStats,
            stats.generateThumbnailStats,
            stats.saveMetadataStats
        ).filterNotNull()
        
        if (operationStats.isEmpty()) return 100.0
        
        val avgSuccessRate = operationStats.map { it.successRate }.average()
        val cacheBonus = if (stats.cacheHitRate > 80) 10.0 else 0.0
        
        return (avgSuccessRate + cacheBonus).coerceAtMost(100.0)
    }
    
    /**
     * Gibt detaillierte Performance-Statistiken zurück
     */
    fun getDetailedStats(): Map<String, OperationStats> {
        val detailedStats = mutableMapOf<String, OperationStats>()
        
        performanceMetrics.forEach { (operationType, durations) ->
            if (durations.isNotEmpty()) {
                val avg = durations.average()
                val min = durations.minOrNull() ?: 0L
                val max = durations.maxOrNull() ?: 0L
                val target = PERFORMANCE_TARGETS[operationType] ?: 0L
                val successRate = if (target > 0) {
                    durations.count { it <= target }.toDouble() / durations.size * 100
                } else 100.0
                
                detailedStats[operationType] = OperationStats(
                    operationType = operationType,
                    averageDurationMs = avg,
                    minDurationMs = min,
                    maxDurationMs = max,
                    targetDurationMs = target,
                    successRate = successRate,
                    totalOperations = durations.size
                )
            }
        }
        
        return detailedStats
    }
    
    /**
     * Setzt alle Metriken zurück
     */
    fun resetMetrics() {
        performanceMetrics.clear()
        activeOperations.clear()
        _performanceStats.value = PerformanceStats()
        Log.i(TAG, "Performance-Metriken zurückgesetzt")
    }
    
    /**
     * Loggt Performance-Zusammenfassung
     */
    fun logPerformanceSummary() {
        val stats = _performanceStats.value
        
        Log.i(TAG, "=== STORAGE PERFORMANCE SUMMARY ===")
        Log.i(TAG, "Overall Score: ${String.format("%.1f", stats.overallPerformanceScore)}%")
        Log.i(TAG, "Cache Hit Rate: ${String.format("%.1f", stats.cacheHitRate)}%")
        
        listOf(
            stats.loadFullImageStats,
            stats.loadThumbnailStats,
            stats.searchIndexStats,
            stats.saveImageStats
        ).filterNotNull().forEach { operationStats ->
            Log.i(TAG, "${operationStats.operationType}: " +
                      "avg=${String.format("%.1f", operationStats.averageDurationMs)}ms " +
                      "(target=${operationStats.targetDurationMs}ms, " +
                      "success=${String.format("%.1f", operationStats.successRate)}%)")
        }
        
        Log.i(TAG, "=====================================")
    }
}

/**
 * Statistiken für eine Operation
 */
data class OperationStats(
    val operationType: String,
    val averageDurationMs: Double,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val targetDurationMs: Long,
    val successRate: Double,
    val totalOperations: Int
) {
    val isPerformingWell: Boolean
        get() = successRate >= 80.0
    
    val performanceRating: String
        get() = when {
            successRate >= 95.0 -> "Excellent"
            successRate >= 80.0 -> "Good"
            successRate >= 60.0 -> "Fair"
            else -> "Poor"
        }
}

/**
 * Gesamt-Performance-Statistiken
 */
data class PerformanceStats(
    var loadFullImageStats: OperationStats? = null,
    var loadThumbnailStats: OperationStats? = null,
    var searchIndexStats: OperationStats? = null,
    var saveImageStats: OperationStats? = null,
    var compressImageStats: OperationStats? = null,
    var generateThumbnailStats: OperationStats? = null,
    var saveMetadataStats: OperationStats? = null,
    var cacheHitStats: OperationStats? = null,
    var cacheMissStats: OperationStats? = null,
    var cacheHitRate: Double = 0.0,
    var overallPerformanceScore: Double = 100.0
) {
    val isHealthy: Boolean
        get() = overallPerformanceScore >= 80.0 && cacheHitRate >= 70.0
}