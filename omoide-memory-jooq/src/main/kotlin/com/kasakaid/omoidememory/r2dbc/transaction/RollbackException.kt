package com.kasakaid.omoidememory.r2dbc.transaction

/**
 * TransactionOperator 内で例外を発生させてロールバックさせる例外
 */
class RollbackException(
    val leftValue: Any,
) : RuntimeException("トランザクション例外が発生しました")
