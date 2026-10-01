package com.kasakaid.omoidememory.shared.spring

import org.springframework.core.env.Environment

fun Environment.familyId(): String =
    getProperty("omoide.family.id")
        ?: throw IllegalStateException("Environment property family.id must be set")
