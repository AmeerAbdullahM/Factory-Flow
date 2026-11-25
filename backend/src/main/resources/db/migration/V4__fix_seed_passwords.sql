-- Update seeded users' password_hash to valid BCrypt(12) hash of 'password123'
UPDATE users
SET password_hash = '$2a$12$ngVrBci29hCWvqs2TgV.qeVFP7d1WMYVmA.mTXzMOxFY10RN3.PTe'
WHERE username IN ('salesman_a', 'salesman_b', 'admin', 'supervisor', 'auditor', 'owner');
