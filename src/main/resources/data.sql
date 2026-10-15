-- =====================================================================
-- OpenQ seed data. Idempotent: every statement uses ON CONFLICT DO NOTHING against a
-- UNIQUE constraint, so restarting the application never creates duplicates.
-- Do not use DO $$ ... $$ blocks here: Spring splits scripts on ';'.
-- =====================================================================

INSERT INTO doctor (name, specialization, qualification, clinic_name, email, active) VALUES
    ('Dr. Asha Menon',   'General Medicine', 'MBBS, MD (Internal Medicine)', 'OpenQ Central Clinic',  'asha.menon@openq.example',   TRUE),
    ('Dr. Rahul Iyer',   'Cardiology',       'MBBS, DM (Cardiology)',        'OpenQ Heart Centre',    'rahul.iyer@openq.example',   TRUE),
    ('Dr. Meera Kapoor', 'Pediatrics',       'MBBS, DCH',                    'OpenQ Children''s Clinic', 'meera.kapoor@openq.example', TRUE),
    ('Dr. Vikram Shah',  'Dermatology',      'MBBS, MD (Dermatology)',       'OpenQ Skin Care',       'vikram.shah@openq.example',  TRUE)
ON CONFLICT ON CONSTRAINT uq_doctor_email DO NOTHING;

-- Rolling window: the next 7 clinic-local days (starting tomorrow), 10 half-hour slots per day per doctor.
-- 'Asia/Kolkata' must match the openq.clinic-zone property (default Asia/Kolkata).
-- On a later restart on a later day, only the NEW days are inserted; existing rows are untouched.
INSERT INTO slot (doctor_id, start_time, end_time, status)
SELECT d.id,
       (((now() AT TIME ZONE 'Asia/Kolkata')::date + day_offset) + t.start_at),
       (((now() AT TIME ZONE 'Asia/Kolkata')::date + day_offset) + t.start_at) + INTERVAL '30 minutes',
       'AVAILABLE'
FROM doctor d
CROSS JOIN generate_series(1, 7) AS day_offset
CROSS JOIN (VALUES (TIME '09:00'), (TIME '09:30'), (TIME '10:00'), (TIME '10:30'), (TIME '11:00'),
                   (TIME '11:30'), (TIME '14:00'), (TIME '14:30'), (TIME '15:00'), (TIME '15:30')) AS t(start_at)
WHERE d.email LIKE '%@openq.example'
ON CONFLICT ON CONSTRAINT uq_slot_doctor_start DO NOTHING;
