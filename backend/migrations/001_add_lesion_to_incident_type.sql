-- ============================================================================
-- Migración 001 — añade el valor 'lesion' al ENUM `incident_type`
-- ----------------------------------------------------------------------------
-- Por qué: SQLAlchemy declara el Enum en Python pero MySQL ya tenía la columna
-- creada con el conjunto antiguo. Cambiar solo el código no actualiza la BBDD,
-- por lo que MySQL truncaba o rechazaba el valor 'lesion'.
--
-- Cómo ejecutar:
--   mysql -u <user> -p <database> < migrations/001_add_lesion_to_incident_type.sql
-- ============================================================================

ALTER TABLE incidents
  MODIFY COLUMN incident_type
    ENUM('molestia', 'dolor_agudo', 'fatiga', 'contractura', 'lesion', 'otro')
    NOT NULL DEFAULT 'molestia';

-- Verificación: la siguiente query debe listar todos los valores incluyendo 'lesion'
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_NAME = 'incidents' AND COLUMN_NAME = 'incident_type';
