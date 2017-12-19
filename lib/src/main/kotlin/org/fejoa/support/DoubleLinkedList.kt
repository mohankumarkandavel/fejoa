package org.fejoa.support

class DoubleLinkedList<T : DoubleLinkedList.Entry<T>> : Iterable<T> {
    open class Entry<T> {
        var previous: T? = null
        var next: T? = null
    }

    private var head: T? = null
    private var tail: T? = null
    private var nEntries = 0

    fun getHead(): T? {
        return head
    }

    fun getTail(): T? {
        return tail
    }

    fun size(): Int {
        return nEntries
    }

    fun addBefore(entry: T, next: T?) {
        nEntries++

        entry.next = null
        entry.previous = null
        if (head == null) {
            head = entry
            tail = head
            return
        }
        if (next == null)
            return
        if (next.previous != null)
            next.previous!!.next = entry
        entry.previous = next.previous
        entry.next = next
        next.previous = entry
        if (next === head)
            head = entry
    }

    fun addFirst(entry: T) {
        addBefore(entry, getHead())
    }

    fun addLast(entry: T) {
        nEntries++

        entry.next = null
        entry.previous = null
        if (head == null) {
            head = entry
            tail = head
            return
        }

        entry.previous = tail
        tail!!.next = entry
        tail = entry
    }

    fun remove(entry: Entry<T>?) {
        nEntries--

        if (entry!!.previous != null)
            entry.previous!!.next = entry.next
        else
            head = entry?.next

        if (entry === tail)
            tail = entry?.previous

        if (entry.next != null)
            entry.next!!.previous = entry.previous
    }

    fun removeHead(): T? {
        val head = getHead()
        remove(head)
        return head
    }

    fun removeTail(): T? {
        val tail = getTail()
        remove(tail)
        return tail
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            internal var current = head

            fun remove() {
                this@DoubleLinkedList.remove(current)
                current = current?.next
            }

            override fun hasNext(): Boolean {
                return current != null
            }

            override fun next(): T {
                val entry = current ?: throw Exception("Unexpected null")
                current = entry.next
                return entry
            }
        }
    }

}
