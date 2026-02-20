package com.kasakaid.omoidememory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OmoideMemoryMigration

fun main(args: Array<String>) {
    MigrationSqlCopier.copyDdlFromJooqProject()
    runApplication<OmoideMemoryMigration>(*args)
}
