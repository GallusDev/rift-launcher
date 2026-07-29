-- Global requirement catalog seed (org_id NULL). This is the product's brain:
-- each row is a federally mandated (or audit-expected) item with its CFR
-- citation and recurrence semantics. Review with a compliance consultant
-- before launch; correctness here is a top product risk.

insert into public.requirement_catalog
  (code, name, description, regulation_ref, applies_to, recurrence_kind,
   interval_months, calendar_rule, required, sort_order)
values
  -- ------------------------------------------------------------------
  -- Driver qualification file (49 CFR 391.51) + drug & alcohol program
  -- ------------------------------------------------------------------
  ('employment_application',
   'Employment application',
   'Completed and signed driver employment application, retained in the DQ file.',
   '49 CFR 391.21', 'driver', 'one_time', null, null, true, 10),

  ('mvr_at_hire',
   'MVR at hire',
   'Motor vehicle record from each state where the driver held a license in the prior 3 years, obtained within 30 days of hire.',
   '49 CFR 391.23(a)(1)', 'driver', 'one_time', null, null, true, 20),

  ('safety_performance_history',
   'Safety performance history investigation',
   'Investigation of the driver''s safety performance history with previous DOT-regulated employers, completed within 30 days of hire.',
   '49 CFR 391.23(d)', 'driver', 'one_time', null, null, true, 30),

  ('road_test',
   'Road test certificate or CDL equivalent',
   'Certificate of road test, or a copy of the CDL accepted as its equivalent.',
   '49 CFR 391.31 / 391.33', 'driver', 'one_time', null, null, true, 40),

  ('cdl_copy',
   'CDL copy',
   'Copy of the driver''s current commercial driver''s license; expires on the license expiration date.',
   '49 CFR 383.23', 'driver', 'document_expiry', null, null, true, 50),

  ('medical_certificate',
   'Medical examiner''s certificate',
   'Current medical examiner''s certificate (valid up to 24 months; often issued shorter).',
   '49 CFR 391.43 / 391.45', 'driver', 'document_expiry', null, null, true, 60),

  ('annual_mvr',
   'Annual MVR / review of driving record',
   'Motor vehicle record obtained and reviewed at least once every 12 months, with the annual review note retained.',
   '49 CFR 391.25', 'driver', 'fixed_interval', 12, null, true, 70),

  ('clearinghouse_preemployment',
   'Clearinghouse pre-employment query',
   'Full Drug & Alcohol Clearinghouse query conducted before the driver first performs safety-sensitive functions.',
   '49 CFR 382.701(a)', 'driver', 'one_time', null, null, true, 80),

  ('clearinghouse_annual',
   'Clearinghouse annual query',
   'Limited Drug & Alcohol Clearinghouse query conducted at least annually for each employed CDL driver.',
   '49 CFR 382.701(b)', 'driver', 'fixed_interval', 12, null, true, 90),

  ('preemployment_drug_test',
   'Pre-employment drug test result',
   'Negative pre-employment controlled substances test result received before performing safety-sensitive functions.',
   '49 CFR 382.301', 'driver', 'one_time', null, null, true, 100),

  ('consortium_enrollment',
   'Random testing consortium enrollment',
   'Proof of current enrollment in a random drug & alcohol testing program/consortium (annual certificate).',
   '49 CFR 382.305', 'driver', 'fixed_interval', 12, null, true, 110),

  -- ------------------------------------------------------------------
  -- Vehicle records
  -- ------------------------------------------------------------------
  ('annual_dot_inspection',
   'Annual DOT inspection',
   'Periodic (annual) inspection report for the vehicle; most recent report retained.',
   '49 CFR 396.17', 'vehicle', 'fixed_interval', 12, null, true, 200),

  ('vehicle_registration',
   'Registration / cab card',
   'Current vehicle registration or IRP cab card; expires on the stated date.',
   'State / IRP', 'vehicle', 'document_expiry', null, null, true, 210),

  ('vehicle_insurance_cert',
   'Insurance certificate (vehicle)',
   'Optional per-vehicle certificate of insurance; primary liability coverage is tracked at the company level.',
   '49 CFR 387', 'vehicle', 'document_expiry', null, null, false, 220),

  -- ------------------------------------------------------------------
  -- Company-level items
  -- ------------------------------------------------------------------
  ('company_insurance',
   'Primary liability insurance',
   'Current certificate of primary liability (and cargo, if applicable) insurance; expires on the policy date.',
   '49 CFR 387', 'company', 'document_expiry', null, null, true, 300),

  ('ucr_registration',
   'UCR registration',
   'Unified Carrier Registration, renewed each calendar year (due December 31 for the following year).',
   'UCR Act / 49 USC 14504a', 'company', 'calendar_rule', null,
   '{"rule": "annual_fixed_date", "month": 12, "day": 31}', true, 310),

  ('mcs150_biennial',
   'MCS-150 biennial update',
   'Biennial update of carrier registration; due month and year are determined by the USDOT number (next-to-last digit sets the year parity, last digit sets the month).',
   '49 CFR 390.19', 'company', 'calendar_rule', null,
   '{"rule": "mcs150_biennial"}', true, 320),

  ('boc3',
   'BOC-3 process agent designation',
   'Designation of process agents on file with FMCSA (one-time; verify a copy is retained).',
   '49 CFR 366', 'company', 'one_time', null, null, true, 330),

  ('ifta_license',
   'IFTA license & decals',
   'IFTA license and decals, renewed annually (calendar year). Tracking only — FleetDocket does not calculate IFTA.',
   'IFTA (state program)', 'company', 'calendar_rule', null,
   '{"rule": "annual_fixed_date", "month": 12, "day": 31}', false, 340),

  ('da_policy',
   'Drug & alcohol policy',
   'Written drug & alcohol testing policy provided to each driver, with signed receipt retained.',
   '49 CFR 382.601', 'company', 'one_time', null, null, true, 350),

  ('supervisor_training',
   'Supervisor reasonable-suspicion training',
   'Documentation of 60 minutes of alcohol misuse and 60 minutes of controlled substances training for supervisors of CDL drivers.',
   '49 CFR 382.603', 'company', 'one_time', null, null, true, 360)
on conflict do nothing;
