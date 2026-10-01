-- Create table for storing albums
CREATE TABLE omoide_memory.album (
    id                     UUID NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    family_id              VARCHAR(255) NOT NULL,
    album_created_date     DATE,
    album_vendor           VARCHAR(255),
    completion_evidence_url TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(255),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_album PRIMARY KEY (id)
);

COMMENT ON TABLE  omoide_memory.album IS 'アルバム';
COMMENT ON COLUMN omoide_memory.album.id IS 'サロゲートキー';
COMMENT ON COLUMN omoide_memory.album.name IS 'アルバム名';
COMMENT ON COLUMN omoide_memory.album.family_id IS '家族ID';
COMMENT ON COLUMN omoide_memory.album.album_created_date IS 'アルバム作成日';
COMMENT ON COLUMN omoide_memory.album.album_vendor IS 'アルバム作成業者';
COMMENT ON COLUMN omoide_memory.album.completion_evidence_url IS '依頼完了証跡URL';
COMMENT ON COLUMN omoide_memory.album.created_at IS 'レコード作成日時';
COMMENT ON COLUMN omoide_memory.album.created_by IS 'レコード作成者';
