-- Create table for storing photo albums
CREATE TABLE omoide_memory.album_photo (
    id                     UUID NOT NULL,
    album_id               UUID NOT NULL,
    album_name             VARCHAR(255) NOT NULL,
    photo_id               UUID NOT NULL,
    family_id              VARCHAR(255) NOT NULL,
    album_created_date     DATE,
    album_vendor           VARCHAR(255),
    completion_evidence_url TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(255),
    CONSTRAINT pk_album_photo PRIMARY KEY (id),
    CONSTRAINT fk_album_photo_photo FOREIGN KEY (photo_id)
        REFERENCES omoide_memory.synced_omoide_photo (id)
);

COMMENT ON TABLE  omoide_memory.album_photo IS 'アルバム写真紐付け';
COMMENT ON COLUMN omoide_memory.album_photo.id IS 'サロゲートキー';
COMMENT ON COLUMN omoide_memory.album_photo.album_id IS 'アルバム識別用ID';
COMMENT ON COLUMN omoide_memory.album_photo.album_name IS 'アルバム名';
COMMENT ON COLUMN omoide_memory.album_photo.photo_id IS '写真ID';
COMMENT ON COLUMN omoide_memory.album_photo.family_id IS '家族ID';
COMMENT ON COLUMN omoide_memory.album_photo.album_created_date IS 'アルバム作成日';
COMMENT ON COLUMN omoide_memory.album_photo.album_vendor IS 'アルバム作成業者';
COMMENT ON COLUMN omoide_memory.album_photo.completion_evidence_url IS '依頼完了証跡URL';
COMMENT ON COLUMN omoide_memory.album_photo.created_at IS 'レコード作成日時';
COMMENT ON COLUMN omoide_memory.album_photo.created_by IS 'レコード作成者';
