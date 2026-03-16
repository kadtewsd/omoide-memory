package com.kasakaid.omoidememory.utility

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.NoArgGenerator
import java.util.UUID

object MyUUIDGenerator {
    private val uuidV7Generator: NoArgGenerator = Generators.timeBasedEpochGenerator()

    fun generateUUIDv7(): UUID {
        // Generates a time-based, sortable UUID version 7
        return uuidV7Generator.generate()
    }
}
