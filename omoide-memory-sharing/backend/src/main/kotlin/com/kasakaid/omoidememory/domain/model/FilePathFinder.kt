package com.kasakaid.omoidememory.domain.model

import java.nio.file.Path

/**
 * ファイルパスの存在検証および正規化を行うドメイン層のインターフェース。
 */
fun interface FilePathFinder {
    /**
     * 指定されたパス文字列が存在するか検証し、存在する場合は [Path] を返します。
     * パスが空である場合やファイルが存在しない場合は null を返します。
     */
    fun findPath(serverPath: String): Path?
}
