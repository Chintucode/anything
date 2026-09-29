-- Day-by-day plans ("### Day 9: Naming Thoughts"): courses worked through in
-- order rather than pinned to weekdays. Existing plans are all weekly, so every
-- new column defaults to what they already mean.

ALTER TABLE plans ADD COLUMN schedule VARCHAR(10) NOT NULL DEFAULT 'WEEKLY';
-- Length in days for a day-by-day plan; null for a weekly one (its length is weeks).
ALTER TABLE plans ADD COLUMN total_days INTEGER;

-- In a day-by-day plan a phase's range is in days, stored in the same columns.
ALTER TABLE plan_phases ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '';

-- A day is a weekday (weekly) or a number (day-by-day), never both.
ALTER TABLE plan_days ALTER COLUMN weekday DROP NOT NULL;
ALTER TABLE plan_days ADD COLUMN day_number INTEGER;
-- The paragraph under the day heading. For a meditation day, the practice itself.
ALTER TABLE plan_days ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '';
