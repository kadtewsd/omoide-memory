-- Create table for storing comments on files
CREATE TABLE comment_omoide_photo (
    id              BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    photo_id       VARCHAR(255)    NOT NULL,
    comment_seq             INT    NOT NULL DEFAULT 1,
    commenter       VARCHAR(255),
    comment_body    TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    CONSTRAINT pk_comment_omoide_photo PRIMARY KEY (id),
    CONSTRAINT fk_comment_omoide_photo_photo FOREIGN KEY (photo_id) REFERENCES synced_omoide_photo (id) ON DELETE CASCADE,
    CONSTRAINT uq_comment_omoide_photo_seq UNIQUE (photo_id, comment_seq)
);

COMMENT ON TABLE comment_omoide_photo IS '写真に対するコメント';
COMMENT ON COLUMN comment_omoide_photo.id IS 'コメントID（サロゲートキー）';
COMMENT ON COLUMN comment_omoide_photo.photo_id IS '対象ファイルのID（外部キー）';
COMMENT ON COLUMN comment_omoide_photo.comment_seq IS '写真ごとのコメント連番';
COMMENT ON COLUMN comment_omoide_photo.commenter IS '発言者名';
COMMENT ON COLUMN comment_omoide_photo.comment_body IS 'コメント本文';
COMMENT ON COLUMN comment_omoide_photo.created_at IS 'コメント作成日時';
COMMENT ON COLUMN comment_omoide_photo.created_by IS 'レコード作成者';