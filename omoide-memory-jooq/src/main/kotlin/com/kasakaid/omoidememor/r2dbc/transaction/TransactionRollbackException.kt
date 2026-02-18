package com.kasakaid.omoidememor.r2dbc.transaction

/**
 * トランザクションをロールバックさせるための例外です。
 * ロールバックを発生させたいユースケースはこのクラスを継承して例外を投げます。
 * というのも、エラー内容を呼び出し元に持ち帰らせたいのですが 、例外では T を保持できず
 * Any 型になってしまうためです。
 * 具象クラスで Any で値を保持してしまう問題を回避します。
 * 例外として投げないとロールバックができないが、エラーとしても扱いたいので、仕方なく二つの性質を持たせています。
 */
abstract class TransactionRollbackException(
    message: String,
) : RuntimeException(message),
    TransactionAttemptFailure

/**
 * トランザクションを実行したが失敗した
 */
interface TransactionAttemptFailure {
    /**
     * 想定外のエラー
     * どうしようもない場合に発生
     */
    class Unmanaged(
        val ex: Throwable,
    ) : TransactionAttemptFailure
}
