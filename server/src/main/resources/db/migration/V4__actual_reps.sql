-- Day 15: what you actually managed, when it wasn't what the plan said.
-- Null means "did it as written", which is the common case.
ALTER TABLE completions ADD COLUMN actual_reps INTEGER;
