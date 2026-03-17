CREATE TABLE omoide_memory.location_cache (
    rounded_latitude NUMERIC(6, 3) NOT NULL,
    rounded_longitude NUMERIC(6, 3) NOT NULL,
    address TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (rounded_latitude, rounded_longitude)
);
