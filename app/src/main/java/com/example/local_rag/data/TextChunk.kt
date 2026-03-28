package com.example.local_rag.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class TextChunk(
    @Id var id: Long = 0,
    var sourceName: String = "",
    var content: String = "",
    
    // all-MiniLM-L6-v2 outputs a 384-dimensional vector
    @HnswIndex(
        dimensions = 384,
        distanceType = VectorDistanceType.COSINE, 
        neighborsPerNode = 16,
        indexingSearchCount = 100
    )
    var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TextChunk
        return id == other.id && content == other.content && 
               embedding?.contentEquals(other.embedding) == true
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceName.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
