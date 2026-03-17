CREATE TABLE omoide_memory.comment_omoide (
    id              UUID NOT NULL,
    file_name        VARCHAR(255)          NOT NULL,
    commenter_id    BIGINT,
    comment_body    TEXT,
    commented_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    CONSTRAINT pk_comment_omoide PRIMARY KEY (id),
    -- H2 でサポートsれていないためコメントアウト
    -- CONSTRAINT uq_comment_omoide_comment_commenter UNIQUE (commenter_id, comment_body),
    CONSTRAINT fk_comment_omoide_commenter FOREIGN KEY (commenter_id) REFERENCES omoide_memory.commenter (id) ON DELETE SET NULL
);

COMMENT ON TABLE  omoide_memory.comment_omoide IS '動画に対するコメント';
COMMENT ON COLUMN omoide_memory.comment_omoide.id IS 'コメントID（サロゲートキー）';
COMMENT ON COLUMN omoide_memory.comment_omoide.file_name IS 'コメントをつけた写真の名前。パスは含まれない';
COMMENT ON COLUMN omoide_memory.comment_omoide.commenter_id IS 'コメント投稿者ID（外部キー）';
COMMENT ON COLUMN omoide_memory.comment_omoide.comment_body IS 'コメント本文';
COMMENT ON COLUMN omoide_memory.comment_omoide.commented_at IS 'コメント投稿日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide.created_by IS 'レコード作成者';
