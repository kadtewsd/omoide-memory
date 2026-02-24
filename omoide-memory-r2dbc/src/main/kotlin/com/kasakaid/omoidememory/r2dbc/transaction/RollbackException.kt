package com.kasakaid.omoidememory.r2dbc.transaction

class RollbackException(
    val leftValue: Any,
) : RuntimeException()
