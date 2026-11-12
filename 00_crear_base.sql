-- Ejemplo para administración inicial de un servidor autorizado.
-- Sustituya la contraseña y ajuste el host ANTES de ejecutar CREATE USER.
CREATE DATABASE IF NOT EXISTS insercom CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- CREATE USER 'insercom_app'@'localhost' IDENTIFIED BY 'SUSTITUYA_POR_SU_CLAVE';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, REFERENCES, INDEX ON insercom.* TO 'insercom_app'@'localhost';
-- La aplicación crea las tablas de 01_esquema.sql al iniciar con --mysql.
