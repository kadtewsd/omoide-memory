CREATE TABLE omoide_memory.download_error (
    file_name     VARCHAR(255)             NOT NULL,
    error_message TEXT                     NOT NULL,
    stack_trace   TEXT                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_download_error PRIMARY KEY (file_name)
);

COMMENT ON TABLE  omoide_memory.download_error IS 'ダウンロードエラーログ';
COMMENT ON COLUMN omoide_memory.download_error.file_name IS 'ファイル名';
COMMENT ON COLUMN omoide_memory.download_error.error_message IS 'エラー内容';
COMMENT ON COLUMN omoide_memory.download_error.stack_trace IS 'スタックトレース (抜粋)';
COMMENT ON COLUMN omoide_memory.download_error.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.download_error.updated_at IS 'レコード更新日時（タイムゾーン付き）';
