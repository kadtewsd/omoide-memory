package com.kasakaid.omoidememory.importfromlocal.service

import java.nio.file.Path

/**
 * import-from-local の動作モード。
 *
 * ## DBメンテナンスモード（[DbMaintenance]）
 * 環境変数 `OMOIDE_BACKUP_DIRECTORY` が未設定の場合に選択される。
 * ファイルの再配置（コピー）は行わず、取込元ディレクトリのパスをそのまま DB に登録する。
 * 不正なデータや重複データが生じた際に DB のレコードを洗い替えするための運用モード。
 *
 * ## ファイル取り込みモード（[FileImport]）
 * 環境変数 `OMOIDE_BACKUP_DIRECTORY` が設定されている場合に選択される。
 * 取込元ファイルを [FileImport.omoideBackupPath] 配下の正式な格納先（撮影年月 / メディア種別 のディレクトリ構成）に
 * コピーし、コピー先のパスを DB に登録する。取込元ファイルは削除しない。
 */
sealed interface ImportMode {
    val omoideBackupPath: Path

    /** DBメンテナンスモード: ファイルの再配置を行わず、取込元パスのままDBに登録する */
    class DbMaintenance(
        override val omoideBackupPath: Path,
    ) : ImportMode

    /** ファイル取り込みモード: ファイルを [omoideBackupPath] 配下に配置してからDBに登録する */
    class FileImport(
        override val omoideBackupPath: Path,
    ) : ImportMode
}
