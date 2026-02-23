CREATE TABLE omoide_memory.comment_omoide_video (
    id              BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    video_id        BIGINT          NOT NULL,
    commenter_id    BIGINT,
    comment_body    TEXT,
    commented_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    CONSTRAINT pk_comment_omoide_video PRIMARY KEY (id),
    CONSTRAINT fk_comment_omoide_video_video FOREIGN KEY (video_id) REFERENCES omoide_memory.synced_omoide_video (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_omoide_video_commenter FOREIGN KEY (commenter_id) REFERENCES omoide_memory.commenter (id) ON DELETE SET NULL
);

COMMENT ON TABLE  omoide_memory.comment_omoide_video IS '動画に対するコメント';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.id IS 'コメントID（サロゲートキー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.video_id IS '対象動画ID（外部キー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.commenter_id IS 'コメント投稿者ID（外部キー）';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.comment_body IS 'コメント本文';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.commented_at IS 'コメント投稿日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.comment_omoide_video.created_by IS 'レコード作成者';
