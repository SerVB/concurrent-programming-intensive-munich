@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val v = array[index]
        if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            val status = v.status.get()!!
            return when (status) {
                SUCCESS -> if (v.index1 == index) v.update1 else v.update2
                FAILED, UNDECIDED -> if (v.index1 == index) v.expected1 else v.expected2
            } as E
        }
        return v as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private fun installDesc(index: Int, expected: E): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) {
                    return status.get() == SUCCESS
                }
                when (val previous = array[index]) {
                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {  // set our descriptor if still needed
                            return true
                        }
                    }

                    this -> {
                        return true  // our descriptor is already set successfully
                    }

                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                        previous.apply()  // other desc found, help applying it
                    }

                    else -> {
                        return false  // some other value found, report failure
                    }
                }
            }
        }

        private fun installDescs(): Int {
            if (installDesc(index1, expected1)) {
                if (installDesc(index2, expected2)) {
                    return 2
                }
                return 1
            }
            return 0
        }

        private fun updateStatusLogicalUpdate(installed: Int): Boolean {
            val new = when (installed) {
                2 -> SUCCESS
                else -> FAILED
            }
            return status.compareAndSet(UNDECIDED, new)
        }

        private fun updateCellsPhysicalUpdate() {
            when (status.get()!!) {
                SUCCESS -> {
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }

                FAILED -> {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }

                UNDECIDED -> throw IllegalStateException("we set another status before calling this method")
            }
        }

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            if (status.get() == UNDECIDED) {
                val installed = installDescs()
                updateStatusLogicalUpdate(installed)
            }
            updateCellsPhysicalUpdate()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}