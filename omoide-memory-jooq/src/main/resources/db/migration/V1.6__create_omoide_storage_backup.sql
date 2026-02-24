CREATE TABLE omoide_memory.omoide_storage_backup (
    id                  UUID NOT NULL,
    source_id           UUID NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    backup_path        VARCHAR(1000) NOT NULL,
    backed_up_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_omoide_storage_backup PRIMARY KEY (id),
    CONSTRAINT uq_omoide_storage_backup_source_id_path UNIQUE (source_id, backup_path)
);

COMMENT ON TABLE  omoide_memory.omoide_storage_backup IS '外付けドライブへのバックアップ記録';
COMMENT ON COLUMN omoide_memory.omoide_storage_backup.id IS 'サロゲートキー (UUID)';
COMMENT ON COLUMN omoide_memory.omoide_storage_backup.source_id IS '元レコードのID (synced_omoide_photo / synced_omoide_video の ID)';
COMMENT ON COLUMN omoide_memory.omoide_storage_backup.file_name IS 'ファイル名';
COMMENT ON COLUMN omoide_memory.omoide_storage_backup.backup_path IS 'バックアップ先のフルパス';
COMMENT ON COLUMN omoide_memory.omoide_storage_backup.backed_up_at IS 'バックアップ実行日時';
