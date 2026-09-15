-- =====================================================================
-- R (repeatable) — Seed the Manafy Ops authorization catalog.
--
-- Seeds: the FULL permission catalog (Artifact #2 §2, exact codes), the 10 seed
-- roles (Artifact #2 §3) with assignability, foundation-relevant role→permission
-- mappings (identity/admin + organization), and foundation system_configuration.
--
-- Idempotent: every INSERT is guarded by NOT EXISTS on the natural key. Re-running
-- inserts only what is missing; never duplicates or deletes. Flyway re-runs this
-- whenever the file checksum changes.
--
-- Dialect-neutral: ${uuid_fn} is RANDOM_UUID() (H2) / gen_random_uuid() (PostgreSQL).
--
-- NOTE (Q-F3): the full permission catalog is seeded now (permissions are seeded
-- independently of roles, spec §102). Domain permissions (apartment/workforce/ops/
-- finance) are mapped to roles in their own later phases; here only the
-- foundation-relevant permissions are mapped to roles.
-- =====================================================================

-- ============================ PERMISSIONS ============================

-- ---- Identity & Administration ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'USER_VIEW', 'View users', 'USER', 'USER', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'USER_CREATE', 'Create user', 'USER', 'USER', 'CREATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'USER_UPDATE', 'Update user', 'USER', 'USER', 'UPDATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'USER_DISABLE', 'Disable user', 'USER', 'USER', 'DISABLE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER_DISABLE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ROLE_VIEW', 'View roles', 'ROLE', 'ROLE', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ROLE_CREATE', 'Create role', 'ROLE', 'ROLE', 'CREATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ROLE_UPDATE', 'Update role', 'ROLE', 'ROLE', 'UPDATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ROLE_ASSIGN', 'Assign role', 'ROLE', 'ROLE', 'ASSIGN', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE_ASSIGN');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PERMISSION_VIEW', 'View permissions', 'PERMISSION', 'PERMISSION', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PERMISSION_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PERMISSION_CHANGE', 'Change role permissions', 'PERMISSION', 'PERMISSION', 'CHANGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PERMISSION_CHANGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SCOPE_ASSIGN', 'Assign scope', 'SCOPE', 'SCOPE', 'ASSIGN', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SCOPE_ASSIGN');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'CONFIG_VIEW', 'View configuration', 'CONFIG', 'CONFIG', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CONFIG_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'CONFIG_UPDATE', 'Update configuration', 'CONFIG', 'CONFIG', 'UPDATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CONFIG_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AUDIT_VIEW', 'View audit log', 'AUDIT', 'AUDIT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AUDIT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FEATURE_FLAG_MANAGE', 'Manage feature flags', 'CONFIG', 'FEATURE_FLAG', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FEATURE_FLAG_MANAGE');

-- ---- Organization ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REGION_VIEW', 'View regions', 'REGION', 'REGION', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REGION_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REGION_CREATE', 'Create region', 'REGION', 'REGION', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REGION_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REGION_UPDATE', 'Update region', 'REGION', 'REGION', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REGION_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AREA_VIEW', 'View areas', 'AREA', 'AREA', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AREA_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AREA_CREATE', 'Create area', 'AREA', 'AREA', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AREA_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AREA_UPDATE', 'Update area', 'AREA', 'AREA', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AREA_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AREA_ASSIGN_OFFICER', 'Assign area field officer', 'AREA', 'AREA', 'ASSIGN_OFFICER', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AREA_ASSIGN_OFFICER');

-- ---- Reporting & Platform (foundation-relevant subset) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REPORT_VIEW', 'View reports', 'REPORT', 'REPORT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REPORT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REPORT_EXPORT', 'Export reports', 'REPORT', 'REPORT', 'EXPORT', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REPORT_EXPORT');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FINANCE_REPORT_VIEW', 'View finance reports', 'REPORT', 'FINANCE_REPORT', 'VIEW', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FINANCE_REPORT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'NOTIFICATION_TEMPLATE_MANAGE', 'Manage notification templates', 'PLATFORM', 'NOTIFICATION_TEMPLATE', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'NOTIFICATION_TEMPLATE_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SEARCH_GLOBAL', 'Global search', 'PLATFORM', 'SEARCH', 'GLOBAL', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SEARCH_GLOBAL');

-- ---- Apartments & Onboarding (catalog only; mapped to roles in later phases) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_VIEW', 'View apartments', 'APARTMENT', 'APARTMENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_CREATE', 'Create apartment', 'APARTMENT', 'APARTMENT', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_UPDATE', 'Update apartment', 'APARTMENT', 'APARTMENT', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_ONBOARD', 'Onboard apartment', 'APARTMENT', 'APARTMENT', 'ONBOARD', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_ONBOARD');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_VERIFY', 'Verify apartment', 'APARTMENT', 'APARTMENT', 'VERIFY', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_VERIFY');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_ACTIVATE', 'Activate apartment', 'APARTMENT', 'APARTMENT', 'ACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_ACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_SUSPEND', 'Suspend apartment', 'APARTMENT', 'APARTMENT', 'SUSPEND', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_SUSPEND');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_EXPORT', 'Export apartments', 'APARTMENT', 'APARTMENT', 'EXPORT', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_EXPORT');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'BUILDING_MANAGE', 'Manage buildings', 'APARTMENT', 'BUILDING', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'BUILDING_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'UNIT_MANAGE', 'Manage units', 'APARTMENT', 'UNIT', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'UNIT_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FACILITY_MANAGE', 'Manage facilities', 'APARTMENT', 'FACILITY', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FACILITY_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_CONTACT_MANAGE', 'Manage apartment contacts', 'APARTMENT', 'CONTACT', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_CONTACT_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_SERVICE_CONFIGURE', 'Configure apartment services', 'APARTMENT', 'SERVICE_CONFIG', 'CONFIGURE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_SERVICE_CONFIGURE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_HEALTH_VIEW', 'View apartment health', 'APARTMENT', 'HEALTH', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_HEALTH_VIEW');

-- ---- Workforce (catalog only) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'EMPLOYEE_VIEW', 'View employees', 'WORKFORCE', 'EMPLOYEE', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'EMPLOYEE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'EMPLOYEE_CREATE', 'Create employee', 'WORKFORCE', 'EMPLOYEE', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'EMPLOYEE_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'EMPLOYEE_UPDATE', 'Update employee', 'WORKFORCE', 'EMPLOYEE', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'EMPLOYEE_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_VIEW', 'View technicians', 'WORKFORCE', 'TECHNICIAN', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_CREATE', 'Create technician', 'WORKFORCE', 'TECHNICIAN', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_UPDATE', 'Update technician', 'WORKFORCE', 'TECHNICIAN', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_VERIFY', 'Verify technician', 'WORKFORCE', 'TECHNICIAN', 'VERIFY', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_VERIFY');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_ACTIVATE', 'Activate technician', 'WORKFORCE', 'TECHNICIAN', 'ACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_ACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_SUSPEND', 'Suspend technician', 'WORKFORCE', 'TECHNICIAN', 'SUSPEND', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_SUSPEND');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_EXPORT', 'Export technicians', 'WORKFORCE', 'TECHNICIAN', 'EXPORT', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_EXPORT');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_PII_VIEW', 'View technician PII', 'WORKFORCE', 'TECHNICIAN', 'PII_VIEW', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_PII_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_KYC_VIEW', 'View technician KYC', 'WORKFORCE', 'TECHNICIAN', 'KYC_VIEW', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_KYC_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_FINANCE_VIEW', 'View technician finance', 'WORKFORCE', 'TECHNICIAN', 'FINANCE_VIEW', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_FINANCE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_VIEW', 'View helpers', 'WORKFORCE', 'HELPER', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_CREATE', 'Create helper', 'WORKFORCE', 'HELPER', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_UPDATE', 'Update helper', 'WORKFORCE', 'HELPER', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_VIEW', 'View vendors', 'WORKFORCE', 'VENDOR', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_CREATE', 'Create vendor', 'WORKFORCE', 'VENDOR', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_UPDATE', 'Update vendor', 'WORKFORCE', 'VENDOR', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_VERIFY', 'Verify vendor', 'WORKFORCE', 'VENDOR', 'VERIFY', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_VERIFY');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_ACTIVATE', 'Activate vendor', 'WORKFORCE', 'VENDOR', 'ACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_ACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_SUSPEND', 'Suspend vendor', 'WORKFORCE', 'VENDOR', 'SUSPEND', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_SUSPEND');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_STAFF_MANAGE', 'Manage vendor staff', 'WORKFORCE', 'VENDOR_STAFF', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_STAFF_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SKILL_MANAGE', 'Manage skills', 'WORKFORCE', 'SKILL', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SKILL_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'CERTIFICATION_MANAGE', 'Manage certifications', 'WORKFORCE', 'CERTIFICATION', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CERTIFICATION_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_DOC_VIEW', 'View workforce documents', 'WORKFORCE', 'DOCUMENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_DOC_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_DOC_VERIFY', 'Verify workforce documents', 'WORKFORCE', 'DOCUMENT', 'VERIFY', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_DOC_VERIFY');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_AVAILABILITY_MANAGE', 'Manage workforce availability', 'WORKFORCE', 'AVAILABILITY', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_AVAILABILITY_MANAGE');

-- ---- Service catalog (catalog only) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_VIEW', 'View services', 'SERVICE', 'SERVICE', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_CREATE', 'Create service', 'SERVICE', 'SERVICE', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_UPDATE', 'Update service', 'SERVICE', 'SERVICE', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_CATEGORY_MANAGE', 'Manage service categories', 'SERVICE', 'CATEGORY', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_CATEGORY_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PRICING_VIEW', 'View pricing', 'SERVICE', 'PRICING', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRICING_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PRICING_MANAGE', 'Manage pricing', 'SERVICE', 'PRICING', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRICING_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SLA_VIEW', 'View SLA', 'SERVICE', 'SLA', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SLA_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SLA_MANAGE', 'Manage SLA', 'SERVICE', 'SLA', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SLA_MANAGE');

-- ---- Operations (catalog only) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_REQUEST_VIEW', 'View service requests', 'OPERATIONS', 'SERVICE_REQUEST', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_REQUEST_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_REQUEST_CREATE', 'Create service request', 'OPERATIONS', 'SERVICE_REQUEST', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_REQUEST_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_REQUEST_UPDATE', 'Update service request', 'OPERATIONS', 'SERVICE_REQUEST', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_REQUEST_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_REQUEST_CANCEL', 'Cancel service request', 'OPERATIONS', 'SERVICE_REQUEST', 'CANCEL', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_REQUEST_CANCEL');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SERVICE_REQUEST_REOPEN', 'Reopen service request', 'OPERATIONS', 'SERVICE_REQUEST', 'REOPEN', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SERVICE_REQUEST_REOPEN');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_VIEW', 'View assignments', 'OPERATIONS', 'ASSIGNMENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_CREATE', 'Create assignment', 'OPERATIONS', 'ASSIGNMENT', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_ACCEPT', 'Accept assignment', 'OPERATIONS', 'ASSIGNMENT', 'ACCEPT', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_ACCEPT');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_EXECUTE', 'Execute assignment', 'OPERATIONS', 'ASSIGNMENT', 'EXECUTE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_EXECUTE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_REASSIGN', 'Reassign assignment', 'OPERATIONS', 'ASSIGNMENT', 'REASSIGN', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_REASSIGN');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_CANCEL', 'Cancel assignment', 'OPERATIONS', 'ASSIGNMENT', 'CANCEL', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_CANCEL');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ASSIGNMENT_RESCHEDULE', 'Reschedule assignment', 'OPERATIONS', 'ASSIGNMENT', 'RESCHEDULE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ASSIGNMENT_RESCHEDULE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FIELD_VISIT_VIEW', 'View field visits', 'OPERATIONS', 'FIELD_VISIT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FIELD_VISIT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FIELD_VISIT_CREATE', 'Create field visit', 'OPERATIONS', 'FIELD_VISIT', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FIELD_VISIT_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FIELD_VISIT_UPDATE', 'Update field visit', 'OPERATIONS', 'FIELD_VISIT', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FIELD_VISIT_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INSPECTION_MANAGE', 'Manage inspections', 'OPERATIONS', 'INSPECTION', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INSPECTION_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INCIDENT_VIEW', 'View incidents', 'OPERATIONS', 'INCIDENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INCIDENT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INCIDENT_CREATE', 'Create incident', 'OPERATIONS', 'INCIDENT', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INCIDENT_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INCIDENT_UPDATE', 'Update incident', 'OPERATIONS', 'INCIDENT', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INCIDENT_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INCIDENT_ESCALATE', 'Escalate incident', 'OPERATIONS', 'INCIDENT', 'ESCALATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INCIDENT_ESCALATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INCIDENT_CLOSE', 'Close incident', 'OPERATIONS', 'INCIDENT', 'CLOSE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INCIDENT_CLOSE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'COMPLAINT_VIEW', 'View complaints', 'OPERATIONS', 'COMPLAINT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'COMPLAINT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'COMPLAINT_CREATE', 'Create complaint', 'OPERATIONS', 'COMPLAINT', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'COMPLAINT_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'COMPLAINT_UPDATE', 'Update complaint', 'OPERATIONS', 'COMPLAINT', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'COMPLAINT_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TICKET_VIEW', 'View tickets', 'OPERATIONS', 'TICKET', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TICKET_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TICKET_CREATE', 'Create ticket', 'OPERATIONS', 'TICKET', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TICKET_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TICKET_UPDATE', 'Update ticket', 'OPERATIONS', 'TICKET', 'UPDATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TICKET_UPDATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ESCALATION_VIEW', 'View escalations', 'OPERATIONS', 'ESCALATION', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ESCALATION_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ESCALATION_CREATE', 'Create escalation', 'OPERATIONS', 'ESCALATION', 'CREATE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ESCALATION_CREATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'ESCALATION_RESOLVE', 'Resolve escalation', 'OPERATIONS', 'ESCALATION', 'RESOLVE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ESCALATION_RESOLVE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REWORK_REQUEST', 'Request rework', 'OPERATIONS', 'REWORK', 'REQUEST', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REWORK_REQUEST');

-- ---- Finance (catalog only) ----
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INVOICE_VIEW', 'View invoices', 'FINANCE', 'INVOICE', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INVOICE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'INVOICE_MANAGE', 'Manage invoices', 'FINANCE', 'INVOICE', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'INVOICE_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PAYMENT_VIEW', 'View payments', 'FINANCE', 'PAYMENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PAYMENT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PAYMENT_MANAGE', 'Manage payments', 'FINANCE', 'PAYMENT', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PAYMENT_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REFUND_VIEW', 'View refunds', 'FINANCE', 'REFUND', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REFUND_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REFUND_REQUEST', 'Request refund', 'FINANCE', 'REFUND', 'REQUEST', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REFUND_REQUEST');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'REFUND_APPROVE', 'Approve refund', 'FINANCE', 'REFUND', 'APPROVE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REFUND_APPROVE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PAYOUT_VIEW', 'View payouts', 'FINANCE', 'PAYOUT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PAYOUT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'PAYOUT_APPROVE', 'Approve payout', 'FINANCE', 'PAYOUT', 'APPROVE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PAYOUT_APPROVE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_INVOICE_VIEW', 'View vendor invoices', 'FINANCE', 'VENDOR_INVOICE', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_INVOICE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_INVOICE_MANAGE', 'Manage vendor invoices', 'FINANCE', 'VENDOR_INVOICE', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_INVOICE_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FINANCE_EXPORT', 'Export finance data', 'FINANCE', 'FINANCE', 'EXPORT', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FINANCE_EXPORT');

-- ============================ ROLES ============================
-- The 10 seed roles (Artifact #2 §3) with assignability (§5, §119).
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'SUPER_ADMIN', 'Super Admin', 'Global administrator', TRUE, 'SUPER_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'SUPER_ADMIN');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'MANAFY_ADMIN', 'Manafy Admin', 'General administrator', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'MANAFY_ADMIN');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_ONBOARDER', 'Apartment Onboarder', 'Onboards apartment communities', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'APARTMENT_ONBOARDER');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'HR', 'HR', 'People / workforce onboarding', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'HR');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'OPERATIONS_COORDINATOR', 'Operations Coordinator', 'Central dispatch/operations', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'OPERATIONS_COORDINATOR');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'FIELD_OFFICER', 'Field Officer', 'Owns an operational area', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'FIELD_OFFICER');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'AREA_OPERATIONS_MANAGER', 'Area Operations Manager', 'Manages multiple areas / field officers', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'AREA_OPERATIONS_MANAGER');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'SUPPORT_AGENT', 'Support Agent', 'Customer/resident operational support', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'SUPPORT_AGENT');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'FINANCE', 'Finance', 'Financial operations', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'FINANCE');
INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status)
  SELECT ${uuid_fn}, FALSE, 0, 'REPORTING', 'Reporting', 'Reporting / analytics', TRUE, 'MANAFY_ADMIN', 'ACTIVE'
  WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'REPORTING');

-- ============================ ROLE -> PERMISSION (foundation-relevant only) ============================
-- SUPER_ADMIN: ALL permissions.
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPER_ADMIN'
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- MANAFY_ADMIN: identity/org admin (foundation subset; domain perms added later).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'MANAFY_ADMIN'
    AND p.code IN ('USER_VIEW','USER_UPDATE','ROLE_VIEW','ROLE_ASSIGN','PERMISSION_VIEW',
                   'SCOPE_ASSIGN','CONFIG_VIEW','CONFIG_UPDATE','AUDIT_VIEW',
                   'REGION_VIEW','REGION_CREATE','REGION_UPDATE',
                   'AREA_VIEW','AREA_CREATE','AREA_UPDATE','AREA_ASSIGN_OFFICER')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- APARTMENT_ONBOARDER: organization read (foundation subset).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'APARTMENT_ONBOARDER'
    AND p.code IN ('REGION_VIEW','AREA_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- OPERATIONS_COORDINATOR: organization read (foundation subset).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'OPERATIONS_COORDINATOR'
    AND p.code IN ('REGION_VIEW','AREA_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- FIELD_OFFICER: area read within own scope (foundation subset).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FIELD_OFFICER'
    AND p.code IN ('REGION_VIEW','AREA_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- AREA_OPERATIONS_MANAGER: area management within region (foundation subset).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'AREA_OPERATIONS_MANAGER'
    AND p.code IN ('REGION_VIEW','AREA_VIEW','AREA_UPDATE','AREA_ASSIGN_OFFICER')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- REPORTING: read reports + audit (foundation subset).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'REPORTING'
    AND p.code IN ('REGION_VIEW','AREA_VIEW','REPORT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- NOTE: HR, SUPPORT_AGENT and FINANCE receive their (domain) permissions in later
-- phases (Workforce / Operations / Finance). They intentionally have no foundation
-- role→permission rows beyond what the platform needs now.

-- ============================ SYSTEM CONFIGURATION (foundation) ============================
INSERT INTO system_configuration (id, deleted, version, config_key, config_value, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'AUDIT_RETENTION_DAYS', '3650', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM system_configuration WHERE config_key = 'AUDIT_RETENTION_DAYS');
INSERT INTO system_configuration (id, deleted, version, config_key, config_value, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'DEFAULT_TIMEZONE', 'Asia/Kolkata', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM system_configuration WHERE config_key = 'DEFAULT_TIMEZONE');

-- =====================================================================
-- PHASE 2 — Apartment domain permissions + role mappings (additive, idempotent).
-- Reconciled with the existing catalog: APARTMENT_VIEW/CREATE/UPDATE/ONBOARD/
-- VERIFY/ACTIVATE/SUSPEND/EXPORT/HEALTH_VIEW, BUILDING_MANAGE, UNIT_MANAGE,
-- FACILITY_MANAGE, APARTMENT_CONTACT_MANAGE, APARTMENT_SERVICE_CONFIGURE already
-- exist (seeded above). The following are the NEW Phase 2 permissions.
-- =====================================================================

INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_DELETE', 'Delete (soft) apartment', 'APARTMENT', 'APARTMENT', 'DELETE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_DELETE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_ASSIGN_FIELD_OFFICER', 'Assign apartment field officer', 'APARTMENT', 'APARTMENT', 'ASSIGN_FIELD_OFFICER', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_ASSIGN_FIELD_OFFICER');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_CONTACT_VIEW', 'View apartment contacts (PII)', 'APARTMENT', 'CONTACT', 'VIEW', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_CONTACT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_DOCUMENT_VIEW', 'View apartment documents', 'APARTMENT', 'DOCUMENT', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_DOCUMENT_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_DOCUMENT_MANAGE', 'Manage apartment documents', 'APARTMENT', 'DOCUMENT', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_DOCUMENT_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_SERVICE_VIEW', 'View apartment service config', 'APARTMENT', 'SERVICE_CONFIG', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_SERVICE_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'APARTMENT_ONBOARD_REJECT', 'Reject apartment onboarding', 'APARTMENT', 'ONBOARDING', 'REJECT', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APARTMENT_ONBOARD_REJECT');

-- ---- APARTMENT_ONBOARDER: full onboarding workflow (Artifact #2 §4.3) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'APARTMENT_ONBOARDER'
    AND p.code IN ('APARTMENT_VIEW','APARTMENT_CREATE','APARTMENT_UPDATE','APARTMENT_ONBOARD',
                   'APARTMENT_EXPORT','BUILDING_MANAGE','UNIT_MANAGE','FACILITY_MANAGE',
                   'APARTMENT_CONTACT_MANAGE','APARTMENT_CONTACT_VIEW','APARTMENT_SERVICE_CONFIGURE',
                   'APARTMENT_SERVICE_VIEW','APARTMENT_DOCUMENT_VIEW','APARTMENT_DOCUMENT_MANAGE',
                   'APARTMENT_HEALTH_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: Onboarder intentionally does NOT get VERIFY/ACTIVATE/REJECT (segregation of
-- duties §7.3); it CAN submit/resubmit via APARTMENT_ONBOARD.

-- ---- MANAFY_ADMIN: broad apartment management incl. verify/activate/reject ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'MANAFY_ADMIN'
    AND p.code IN ('APARTMENT_VIEW','APARTMENT_CREATE','APARTMENT_UPDATE','APARTMENT_ONBOARD',
                   'APARTMENT_VERIFY','APARTMENT_ACTIVATE','APARTMENT_SUSPEND','APARTMENT_EXPORT',
                   'APARTMENT_DELETE','APARTMENT_ASSIGN_FIELD_OFFICER','APARTMENT_ONBOARD_REJECT',
                   'BUILDING_MANAGE','UNIT_MANAGE','FACILITY_MANAGE',
                   'APARTMENT_CONTACT_MANAGE','APARTMENT_CONTACT_VIEW','APARTMENT_SERVICE_CONFIGURE',
                   'APARTMENT_SERVICE_VIEW','APARTMENT_DOCUMENT_VIEW','APARTMENT_DOCUMENT_MANAGE',
                   'APARTMENT_HEALTH_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- FIELD_OFFICER: view apartments in own area + operational health/service view ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FIELD_OFFICER'
    AND p.code IN ('APARTMENT_VIEW','APARTMENT_HEALTH_VIEW','APARTMENT_SERVICE_VIEW',
                   'APARTMENT_DOCUMENT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- AREA_OPERATIONS_MANAGER: region-scoped apartment view/update/suspend + FO assign ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'AREA_OPERATIONS_MANAGER'
    AND p.code IN ('APARTMENT_VIEW','APARTMENT_UPDATE','APARTMENT_SUSPEND',
                   'APARTMENT_ASSIGN_FIELD_OFFICER','APARTMENT_SERVICE_CONFIGURE',
                   'APARTMENT_SERVICE_VIEW','APARTMENT_HEALTH_VIEW','APARTMENT_CONTACT_VIEW',
                   'APARTMENT_DOCUMENT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- SUPPORT_AGENT: read-only apartment lookup (NO contact PII, §7.8) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPPORT_AGENT'
    AND p.code IN ('APARTMENT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: SUPPORT_AGENT deliberately does NOT get APARTMENT_CONTACT_VIEW (PII) or any
-- mutation; §7.8 restricts support to lookup.

-- ---- REPORTING: read-only apartment view for reports ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'REPORTING'
    AND p.code IN ('APARTMENT_VIEW','APARTMENT_HEALTH_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- SUPER_ADMIN: re-apply ALL permissions AFTER adding Phase 2 permissions ----
-- The earlier SUPER_ADMIN=ALL block runs before the Phase 2 permissions are
-- inserted (they are appended below the foundation catalog). Re-run the catch-all
-- here so SUPER_ADMIN also holds every Phase 2 permission. Idempotent (NOT EXISTS).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPER_ADMIN'
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- =====================================================================
-- PHASE 3 — Workforce & HR permissions + role mappings (additive, idempotent).
-- Existing catalog already has: EMPLOYEE_{VIEW,CREATE,UPDATE}, TECHNICIAN_{VIEW,
-- CREATE,UPDATE,VERIFY,ACTIVATE,SUSPEND,EXPORT,PII_VIEW,KYC_VIEW,FINANCE_VIEW},
-- HELPER_{VIEW,CREATE,UPDATE}, VENDOR_{VIEW,CREATE,UPDATE,VERIFY,ACTIVATE,SUSPEND,
-- STAFF_MANAGE}, SKILL_MANAGE, CERTIFICATION_MANAGE, WORKFORCE_DOC_{VIEW,VERIFY},
-- WORKFORCE_AVAILABILITY_MANAGE. The following are NEW Phase 3 permissions.
-- =====================================================================

-- Lifecycle actions not previously present.
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_DEACTIVATE', 'Deactivate technician', 'WORKFORCE', 'TECHNICIAN', 'DEACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_DEACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'TECHNICIAN_TERMINATE', 'Terminate technician', 'WORKFORCE', 'TECHNICIAN', 'TERMINATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'TECHNICIAN_TERMINATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_ACTIVATE', 'Activate helper', 'WORKFORCE', 'HELPER', 'ACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_ACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_SUSPEND', 'Suspend helper', 'WORKFORCE', 'HELPER', 'SUSPEND', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_SUSPEND');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_DEACTIVATE', 'Deactivate helper', 'WORKFORCE', 'HELPER', 'DEACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_DEACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'HELPER_TERMINATE', 'Terminate helper', 'WORKFORCE', 'HELPER', 'TERMINATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'HELPER_TERMINATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_DEACTIVATE', 'Deactivate vendor', 'WORKFORCE', 'VENDOR', 'DEACTIVATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_DEACTIVATE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'VENDOR_TERMINATE', 'Terminate vendor', 'WORKFORCE', 'VENDOR', 'TERMINATE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'VENDOR_TERMINATE');

-- Workforce association management (skills / areas assignment onto workforce).
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_SKILL_MANAGE', 'Assign/remove workforce skills', 'WORKFORCE', 'WORKFORCE_SKILL', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_SKILL_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_AREA_MANAGE', 'Assign/remove workforce areas', 'WORKFORCE', 'WORKFORCE_AREA', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_AREA_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_CERTIFICATION_MANAGE', 'Manage workforce certifications', 'WORKFORCE', 'CERTIFICATION', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_CERTIFICATION_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'WORKFORCE_DOC_MANAGE', 'Manage workforce documents', 'WORKFORCE', 'DOCUMENT', 'MANAGE', TRUE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'WORKFORCE_DOC_MANAGE');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'SKILL_VIEW', 'View skills master data', 'WORKFORCE', 'SKILL', 'VIEW', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SKILL_VIEW');
INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive)
  SELECT ${uuid_fn}, FALSE, 0, 'FIELD_OFFICER_MANAGE', 'Manage field officer records/area assignment', 'WORKFORCE', 'FIELD_OFFICER', 'MANAGE', FALSE
  WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'FIELD_OFFICER_MANAGE');

-- ---- HR: primary workforce-management persona (spec §5) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'HR'
    AND p.code IN (
      'EMPLOYEE_VIEW','EMPLOYEE_CREATE','EMPLOYEE_UPDATE',
      'TECHNICIAN_VIEW','TECHNICIAN_CREATE','TECHNICIAN_UPDATE','TECHNICIAN_VERIFY',
      'TECHNICIAN_ACTIVATE','TECHNICIAN_SUSPEND','TECHNICIAN_DEACTIVATE','TECHNICIAN_TERMINATE',
      'TECHNICIAN_PII_VIEW','TECHNICIAN_KYC_VIEW','TECHNICIAN_EXPORT',
      'HELPER_VIEW','HELPER_CREATE','HELPER_UPDATE','HELPER_ACTIVATE','HELPER_SUSPEND',
      'HELPER_DEACTIVATE','HELPER_TERMINATE',
      'VENDOR_VIEW','VENDOR_CREATE','VENDOR_UPDATE','VENDOR_VERIFY','VENDOR_ACTIVATE',
      'VENDOR_SUSPEND','VENDOR_DEACTIVATE','VENDOR_TERMINATE','VENDOR_STAFF_MANAGE',
      'SKILL_VIEW','SKILL_MANAGE','CERTIFICATION_MANAGE','WORKFORCE_CERTIFICATION_MANAGE',
      'WORKFORCE_SKILL_MANAGE','WORKFORCE_AREA_MANAGE','WORKFORCE_AVAILABILITY_MANAGE',
      'WORKFORCE_DOC_VIEW','WORKFORCE_DOC_MANAGE','WORKFORCE_DOC_VERIFY',
      'FIELD_OFFICER_MANAGE','EMPLOYEE_VIEW','REGION_VIEW','AREA_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: HR deliberately does NOT get TECHNICIAN_FINANCE_VIEW (finance-owned, §6/§19),
-- nor any Finance/payout/Super-Admin permissions.

-- ---- MANAFY_ADMIN: broad workforce view/manage (no finance-only KYC-vs-finance split changes) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'MANAFY_ADMIN'
    AND p.code IN (
      'EMPLOYEE_VIEW','EMPLOYEE_CREATE','EMPLOYEE_UPDATE',
      'TECHNICIAN_VIEW','TECHNICIAN_CREATE','TECHNICIAN_UPDATE','TECHNICIAN_VERIFY',
      'TECHNICIAN_ACTIVATE','TECHNICIAN_SUSPEND','TECHNICIAN_DEACTIVATE','TECHNICIAN_TERMINATE',
      'TECHNICIAN_PII_VIEW','TECHNICIAN_EXPORT',
      'HELPER_VIEW','HELPER_CREATE','HELPER_UPDATE','HELPER_ACTIVATE','HELPER_SUSPEND',
      'HELPER_DEACTIVATE','HELPER_TERMINATE',
      'VENDOR_VIEW','VENDOR_CREATE','VENDOR_UPDATE','VENDOR_VERIFY','VENDOR_ACTIVATE',
      'VENDOR_SUSPEND','VENDOR_DEACTIVATE','VENDOR_TERMINATE','VENDOR_STAFF_MANAGE',
      'SKILL_VIEW','SKILL_MANAGE','CERTIFICATION_MANAGE','WORKFORCE_CERTIFICATION_MANAGE',
      'WORKFORCE_SKILL_MANAGE','WORKFORCE_AREA_MANAGE','WORKFORCE_AVAILABILITY_MANAGE',
      'WORKFORCE_DOC_VIEW','WORKFORCE_DOC_MANAGE','FIELD_OFFICER_MANAGE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: MANAFY_ADMIN does NOT get TECHNICIAN_KYC_VIEW or TECHNICIAN_FINANCE_VIEW by
-- default (those remain HR / Finance owned respectively).

-- ---- OPERATIONS_COORDINATOR: read workforce for dispatch readiness (view only) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'OPERATIONS_COORDINATOR'
    AND p.code IN ('TECHNICIAN_VIEW','TECHNICIAN_PII_VIEW','HELPER_VIEW','VENDOR_VIEW','SKILL_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- FIELD_OFFICER: view workforce operating in their area (scope-limited at runtime) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FIELD_OFFICER'
    AND p.code IN ('TECHNICIAN_VIEW','HELPER_VIEW','VENDOR_VIEW','SKILL_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- AREA_OPERATIONS_MANAGER: view workforce + field officers in region ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'AREA_OPERATIONS_MANAGER'
    AND p.code IN ('TECHNICIAN_VIEW','HELPER_VIEW','VENDOR_VIEW','SKILL_VIEW',
                   'EMPLOYEE_VIEW','FIELD_OFFICER_MANAGE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- FINANCE: technician FINANCE view only (NOT KYC) — preserves KYC≠Finance split ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FINANCE'
    AND p.code IN ('TECHNICIAN_VIEW','TECHNICIAN_FINANCE_VIEW','VENDOR_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: FINANCE does NOT get TECHNICIAN_KYC_VIEW (§19).

-- ---- SUPPORT_AGENT: minimal workforce lookup (no KYC/finance/PII-manage) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPPORT_AGENT'
    AND p.code IN ('TECHNICIAN_VIEW','HELPER_VIEW','VENDOR_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: SUPPORT_AGENT deliberately does NOT get TECHNICIAN_KYC_VIEW / _FINANCE_VIEW /
-- _PII_VIEW; support gets non-sensitive lookup only (§19).

-- ---- REPORTING: read workforce for reports ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'REPORTING'
    AND p.code IN ('TECHNICIAN_VIEW','HELPER_VIEW','VENDOR_VIEW','SKILL_VIEW','TECHNICIAN_EXPORT')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- =====================================================================
-- PHASE 4A — SERVICE REQUEST role → permission mappings.
--
-- The SERVICE_REQUEST_* permissions are already in the catalog (OPERATIONS domain);
-- Phase 4A only MAPS them to roles. Only VIEW / CREATE / CANCEL are relevant to the
-- foundation (NEW -> CANCELLED). UPDATE / REOPEN stay unmapped (deferred). Listing
-- reuses SERVICE_REQUEST_VIEW (no separate _LIST permission, matching APARTMENT_VIEW).
-- Grants are intentionally minimal — not every role gets access.
-- =====================================================================

-- ---- OPERATIONS_COORDINATOR: central operations owns service requests ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'OPERATIONS_COORDINATOR'
    AND p.code IN ('SERVICE_REQUEST_VIEW','SERVICE_REQUEST_CREATE','SERVICE_REQUEST_CANCEL')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- MANAFY_ADMIN: administrative service-request management ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'MANAFY_ADMIN'
    AND p.code IN ('SERVICE_REQUEST_VIEW','SERVICE_REQUEST_CREATE','SERVICE_REQUEST_CANCEL')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- FIELD_OFFICER: raise + view + cancel requests in their own area (scope-limited at runtime) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FIELD_OFFICER'
    AND p.code IN ('SERVICE_REQUEST_VIEW','SERVICE_REQUEST_CREATE','SERVICE_REQUEST_CANCEL')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- AREA_OPERATIONS_MANAGER: view + cancel requests across their region ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'AREA_OPERATIONS_MANAGER'
    AND p.code IN ('SERVICE_REQUEST_VIEW','SERVICE_REQUEST_CREATE','SERVICE_REQUEST_CANCEL')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- SUPPORT_AGENT: raise + view requests on behalf of residents (NO cancel) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPPORT_AGENT'
    AND p.code IN ('SERVICE_REQUEST_VIEW','SERVICE_REQUEST_CREATE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- NOTE: SUPPORT_AGENT does NOT get SERVICE_REQUEST_CANCEL (cancellation is an
-- operations/field decision). FINANCE / HR / REPORTING / APARTMENT_ONBOARDER get NO
-- service-request access in Phase 4A.

-- =====================================================================
-- PHASE 4B — OPERATIONS & DISPATCH role → permission mappings.
--
-- ASSIGNMENT_* and FIELD_VISIT_* permissions are already in the catalog (OPERATIONS
-- domain); Phase 4B only MAPS them to internal roles. Technician login is DEFERRED,
-- so during MVP internal operations users perform the technician-side workflow
-- actions (accept/execute) on the technician's behalf. The model stays extensible:
-- a future technician-app identity can hold ASSIGNMENT_ACCEPT / ASSIGNMENT_EXECUTE
-- without any redesign. FINANCE / HR / REPORTING / APARTMENT_ONBOARDER / SUPPORT get
-- NO assignment permissions in this phase.
-- =====================================================================

-- ---- OPERATIONS_COORDINATOR: primary dispatch persona (full operational lifecycle) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'OPERATIONS_COORDINATOR'
    AND p.code IN ('SERVICE_REQUEST_UPDATE',
                   'ASSIGNMENT_VIEW','ASSIGNMENT_CREATE','ASSIGNMENT_ACCEPT','ASSIGNMENT_EXECUTE',
                   'ASSIGNMENT_REASSIGN','ASSIGNMENT_CANCEL','ASSIGNMENT_RESCHEDULE',
                   'FIELD_VISIT_VIEW','FIELD_VISIT_CREATE','FIELD_VISIT_UPDATE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- MANAFY_ADMIN: administrative dispatch management ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'MANAFY_ADMIN'
    AND p.code IN ('SERVICE_REQUEST_UPDATE',
                   'ASSIGNMENT_VIEW','ASSIGNMENT_CREATE','ASSIGNMENT_ACCEPT','ASSIGNMENT_EXECUTE',
                   'ASSIGNMENT_REASSIGN','ASSIGNMENT_CANCEL','ASSIGNMENT_RESCHEDULE',
                   'FIELD_VISIT_VIEW','FIELD_VISIT_CREATE','FIELD_VISIT_UPDATE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- AREA_OPERATIONS_MANAGER: dispatch within their region/area scope (+ escalation) ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'AREA_OPERATIONS_MANAGER'
    AND p.code IN ('ASSIGNMENT_VIEW','ASSIGNMENT_CREATE','ASSIGNMENT_REASSIGN',
                   'ASSIGNMENT_CANCEL','ASSIGNMENT_RESCHEDULE','FIELD_VISIT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- FIELD_OFFICER: operational execution in their own area (scope-limited at runtime) ----
-- FO acts on behalf of the technician on the ground during MVP (no technician login):
-- they can view, accept/decline, and run the execution milestones for their area's
-- requests, but not create/reassign/cancel (that is a coordinator decision).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'FIELD_OFFICER'
    AND p.code IN ('ASSIGNMENT_VIEW','ASSIGNMENT_ACCEPT','ASSIGNMENT_EXECUTE',
                   'ASSIGNMENT_RESCHEDULE','FIELD_VISIT_VIEW','FIELD_VISIT_UPDATE')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- REPORTING: read-only operational visibility ----
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'REPORTING'
    AND p.code IN ('ASSIGNMENT_VIEW','FIELD_VISIT_VIEW')
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- SUPER_ADMIN: re-apply ALL permissions AFTER adding Phase 4B mappings ----
-- Must run LAST so SUPER_ADMIN also holds every permission (idempotent).
INSERT INTO role_permission (id, deleted, version, role_id, permission_id)
  SELECT ${uuid_fn}, FALSE, 0, r.id, p.id
  FROM role r CROSS JOIN permission p
  WHERE r.code = 'SUPER_ADMIN'
    AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
