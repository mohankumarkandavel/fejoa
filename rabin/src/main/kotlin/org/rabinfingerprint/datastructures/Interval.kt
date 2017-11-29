package org.rabinfingerprint.datastructures

import kotlin.math.max
import kotlin.math.min


/**
 * A Parameter-Style Object that contains a start and end index.
 *
 * The indices are in common set notation where the start index is inclusive and
 * the end offset is exclusive. This allows us to easily represent zero-width
 * intervals -- in this case, anything where start == end;
 *
 * The default comparator sorts first be the start index, then by the end index.
 *
 */
class Interval(
        /**
         * Returns the inclusive start offset
         */
        val start: Long,
        /**
         * Returns the exclusive end offset
         */
        val end: Long) : Comparable<Interval> {

    init {
        if (start.compareTo(end) > 0)
            throw IllegalArgumentException("Interval indeces out of order")
    }

    val size: Long?
        get() = end - start

    /**
     * Return the overlapping region of this interval and the input. If the
     * intervals do not overlap, null is returned.
     */
    fun intersection(interval: Interval): Interval? {
        val istart = interval.start
        val iend = interval.end
        return if (istart >= end || start >= iend) null else Interval(max(start, istart), min(end, iend)) // no overlap
    }

    /**
     * Returns the smallest interval that contains both this interval and the
     * input. Note that this not a strict union since indices not included in
     * either interval can be included in resulting interval.
     */
    fun union(interval: Interval): Interval {
        val istart = interval.start
        val iend = interval.end
        return Interval(min(start, istart), max(end, iend))
    }

    /**
     * Tests whether this is an empty (a.k.a. zero-length) interval
     */
    val isEmpty: Boolean
        get() = start == end

    /**
     * Tests whether the input interval overlaps this interval. Adjacency does
     * not count as overlapping.
     */
    fun isOverlap(interval: Interval): Boolean {
        if (interval.start >= this.end)
            return false
        return if (this.start >= interval.end) false else true
    }

    /**
     * Tests whether this interval completely contains the input interval.
     */
    operator fun contains(interval: Interval): Boolean {
        return this.start <= interval.start && this.end >= interval.end
    }

    /**
     * Tests whether this interval contains the input index.
     */
    operator fun contains(index: Long): Boolean {
        return this.start <= index && this.end > index
    }

    /**
     * Object override for printing
     */
    override fun toString(): String {
        return "[$start, $end)"
    }

    /**
     * Comparable<Interval> Implementation
    </Interval> */
    override fun compareTo(other: Interval): Int {
        return START_END_COMPARATOR.compare(this, other)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (end.hashCode())
        result = prime * result + (start.hashCode())
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other == null)
            return false
        if (other !is Interval)
            return false
        if (end != other.end)
            return false
        if (start != other.start)
            return false
        return true
    }

    companion object {

        /**
         * The default comparator. Sorts first be the start index, then by the end
         * index.
         */
        val START_END_COMPARATOR: Comparator<Interval> = Comparator { o1, o2 ->
            if (o1 === o2)
                return@Comparator 0
            val cmp = o1.start.compareTo(o2.start)
            if (cmp != 0) cmp else o1.end.compareTo(o2.end)
        }

        /**
         * This comparator is used for comparing intervals in
         * [FastSentenceParagraphInfo].
         */
        val START_END_INV_COMPARATOR: Comparator<Interval> = Comparator<Interval> { o1, o2 ->
            if (o1 === o2)
                return@Comparator 0
            val cmp = o1.start.compareTo(o2.start)
            if (cmp != 0) cmp else o2.end.compareTo(o1.end)
        }

        fun createUndirected(start: Long?, end: Long?): Interval {
            return if (start!!.compareTo(end!!) > 0) Interval(end, start) else Interval(start, end)
        }
    }
}
