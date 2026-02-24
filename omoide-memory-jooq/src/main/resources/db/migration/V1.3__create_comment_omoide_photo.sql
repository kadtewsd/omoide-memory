-- Create table for storing comments on files
CREATE TABLE omoide_memory.comment_omoide_photo (
    id              UUID  NOT NULL,
    photo_id        UUID          NOT NULL,
    commenter_id    BIGINT,
    comment_body    TEXT,
    commented_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    CONSTRAINT pk_comment_omoide_photo PRIMARY KEY (id),
    CONSTRAINT fk_comment_omoide_photo_photo FOREIGN KEY (photo_id) REFERENCES omoide_memory.synced_omoide_photo (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_omoide_photo_commenter FOREIGN KEY (commenter_id) REFERENCES omoide_memory.commenter (id) ON DELETE SET NULL
);

COMMENT ON TABLE  omoide_memory.comment_omoide_photo IS '写真に対するコメント';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.id IS 'コメントID（サロゲートキー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.photo_id IS '対象写真ID（外部キー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.commenter_id IS 'コメント投稿者ID（外部キー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.comment_body IS 'コメント本文';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.commented_at IS 'コメント投稿日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide_photo.created_by IS 'レコード作成者';
