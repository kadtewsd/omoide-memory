package com.kasakaid.omoidememory.r2dbc.transaction

/**
 * トランザクションをロールバックさせるための例外です。
 * ロールバックを発生させたいユースケースはこのクラスを継承して例外を投げます。
 * というのも、エラー内容を呼び出し元に持ち帰らせたいのですが 、例外では T を保持できず
 * Any 型になってしまうためです。
 * 具象クラスで Any で値を保持してしまう問題を回避します。
 * 例外として投げないとロールバックができないが、エラーとしても扱いたいので、仕方なく二つの性質を持たせています。
 */
class TransactionRollbackException(
    // Any? にしないと渡せない
    val error: Any?,
) : RuntimeException("トランザクションをロールバック")

/**
 * トランザクションを実行したが失敗した
 */
interface TransactionRollback

sealed interface FatalTransactionRollback : TransactionRollback {
    val ex: Throwable
}

/**
 * 想定外のエラー
 * どうしようもない場合に発生
 */
class Unmanaged(
    override val ex: Throwable,
) : FatalTransactionRollback
