CREATE TABLE omoide_memory.commenter (
    id          BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    family_id   VARCHAR(255)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    icon   TEXT    NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    CONSTRAINT pk_commenter PRIMARY KEY (id),
    CONSTRAINT uq_commenter_family_name UNIQUE (family_id, name)
);

COMMENT ON TABLE  omoide_memory.commenter IS 'コメント投稿者';
COMMENT ON COLUMN omoide_memory.commenter.id IS 'コメント投稿者ID（サロゲートキー）';
COMMENT ON COLUMN omoide_memory.commenter.family_id IS '家族ID';
COMMENT ON COLUMN omoide_memory.commenter.name IS 'コメント投稿者名';
COMMENT ON COLUMN omoide_memory.commenter.icon IS 'コメント投稿者のアイコン。Base64エンコード済み文字列で格納される。Bytea だと永続化時にバイナリにしないといけないが、投入用ツールがないため';
COMMENT ON COLUMN omoide_memory.commenter.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.commenter.created_by IS 'レコード作成者';