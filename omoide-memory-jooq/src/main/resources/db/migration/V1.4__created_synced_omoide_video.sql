CREATE TABLE omoide_memory.synced_omoide_video (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    family_id          VARCHAR(255)    NOT NULL,
    file_name           VARCHAR(255)    NOT NULL,
    server_path         TEXT            NOT NULL,
    capture_time        TIMESTAMP WITH TIME ZONE,
    duration_seconds    DECIMAL(10, 3),
    video_width         INT,
    video_height        INT,
    frame_rate          DECIMAL(6, 3),
    video_codec         VARCHAR(50),
    video_bitrate_kbps  INT,
    audio_codec         VARCHAR(50),
    audio_bitrate_kbps  INT,
    audio_channels      SMALLINT,
    audio_sample_rate   INT,
    file_size     BIGINT,
    thumbnail_image     BYTEA,
    thumbnail_mime_type VARCHAR(50),
    drive_file_id        VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    CONSTRAINT pk_synced_omoide_video PRIMARY KEY (id),
    CONSTRAINT uq_synced_omoide_video_file_name UNIQUE (file_name)
);

COMMENT ON TABLE  omoide_memory.synced_omoide_video IS '同期済みおもいで動画';
COMMENT ON COLUMN omoide_memory.commenter.family_id IS '家族ID。OMOIDE_FOLDER_ID にはいっているドライブのIDがはいる';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.id IS 'サロゲートキー';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.file_name IS 'ファイル名（ユニーク）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.server_path IS 'サーバー上のパス';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.capture_time IS '撮影日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.duration_seconds IS '動画時間（秒）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.video_width IS '動画幅（px）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.video_height IS '動画高さ（px）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.frame_rate IS 'フレームレート（fps）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.video_codec IS '映像コーデック 例: H.264';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.video_bitrate_kbps IS '映像ビットレート（kbps）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.audio_codec IS '音声コーデック 例: AAC';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.audio_bitrate_kbps IS '音声ビットレート（kbps）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.audio_channels IS '音声チャンネル数 例: 2=ステレオ';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.audio_sample_rate IS '音声サンプリングレート（Hz）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.file_size IS 'ファイルサイズ（バイト）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.thumbnail_image IS 'サムネイル画像バイナリ（1秒目）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.thumbnail_mime_type IS 'サムネイルのMIMEタイプ 例: image/jpeg';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.drive_file_id IS '外部ストレージのファイルID';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.created_at IS 'レコード作成日時（タイムゾーン付き）';
COMMENT ON COLUMN omoide_memory.synced_omoide_video.created_by IS 'レコード作成者';
