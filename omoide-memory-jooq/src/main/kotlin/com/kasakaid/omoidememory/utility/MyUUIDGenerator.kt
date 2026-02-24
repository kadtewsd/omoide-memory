package com.kasakaid.omoidememory.utility

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.TimeBasedGenerator
import java.util.UUID

object MyUUIDGenerator {
    private val timeBasedGenerator: TimeBasedGenerator = Generators.timeBasedGenerator()

    fun generateUUIDv7(): UUID {
        // Generates a time-based, sortable UUID version 7
        return timeBasedGenerator.generate()
    }
}
