package com.example.arwalking.storage

/**
 * Sealed class für Speicher-Operationen
 * Repräsentiert das Ergebnis einer Speicher-Operation
 */
sealed class SaveResult {
    /**
     * Erfolgreiche Speicher-Operation
     */
    data class Success(val message: String) : SaveResult()
    
    /**
     * Fehlgeschlagene Speicher-Operation
     */
    data class Error(val message: String) : SaveResult()
}