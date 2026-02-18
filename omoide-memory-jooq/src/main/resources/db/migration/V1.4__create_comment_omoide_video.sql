CREATE TABLE comment_omoide_video (
    id              BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    video_id       VARCHAR(255)    NOT NULL,
    comment_seq             INT             NOT NULL DEFAULT 1,
    commenter       VARCHAR(255),
    comment_body    TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    CONSTRAINT pk_comment_omoide_video PRIMARY KEY (id),
    CONSTRAINT fk_comment_omoide_video_video FOREIGN KEY (video_id) REFERENCES synced_omoide_video (id) ON DELETE CASCADE,
    CONSTRAINT uq_comment_omoide_video_seq UNIQUE (video_id, comment_seq)
);

COMMENT ON TABLE comment_omoide_video IS '動画に対するコメント';
COMMENT ON COLUMN comment_omoide_video.id IS 'コメントID（サロゲートキー）';
COMMENT ON COLUMN comment_omoide_video.video_id IS '対象ファイルのID（外部キー）';
COMMENT ON COLUMN comment_omoide_video.comment_seq IS '動画ごとのコメント連番';
COMMENT ON COLUMN comment_omoide_video.commenter IS '発言者名';
COMMENT ON COLUMN comment_omoide_video.comment_body IS 'コメント本文';
COMMENT ON COLUMN comment_omoide_video.created_at IS 'コメント作成日時';
COMMENT ON COLUMN comment_omoide_video.created_by IS 'レコード作成者';