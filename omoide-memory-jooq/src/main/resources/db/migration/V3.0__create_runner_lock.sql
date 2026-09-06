CREATE TABLE omoide_memory.runner_lock (
    runner_name VARCHAR PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
