package com.kasakaid.omoidememory.extension

import kotlin.reflect.KClass

/**
 * sealed クラス / インターフェースを再帰的に探索して
 * すべての末端（leaf）の [objectInstance] を収集するヘルパーオブジェクト。
 *
 * inline 関数は再帰呼び出しできないため、再帰処理はこのオブジェクトの通常の関数で行います。
 */
object SealedClasses {
    @Suppress("UNCHECKED_CAST")
    fun <T> find(kClass: KClass<*>): List<T> =
        kClass.sealedSubclasses.flatMap { subClass ->
            if (subClass.sealedSubclasses.isEmpty()) {
                listOf(subClass.objectInstance as T)
            } else {
                find(subClass)
            }
        }
}

/**
 * 基底の sealed interface の Companion オブジェクト から sealed クラス / インターフェースのインスタンスから、
 * その型 [T] に属するすべての末端（leaf）の [objectInstance] を再帰的に収集して [List<T>] として返します。
 * JVM 環境ではないと動かない場合があり。
 */
inline fun <reified T, Y : Any> T.sealedClassesFromCompanion(): List<Y> {
    val declaration = T::class.java.declaringClass
    return SealedClasses.find(declaration.kotlin)
}

/**
 * sealed な [KClass] から再帰的にすべての末端（leaf）の [objectInstance] を収集して [List<T>] として返します。
 */
inline fun <reified T : Any> KClass<T>.sealedClasses(): List<T> = SealedClasses.find(this)
