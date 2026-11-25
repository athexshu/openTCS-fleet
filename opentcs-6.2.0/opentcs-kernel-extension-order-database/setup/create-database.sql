-- Creates the login and the database for the openTCS order database extension.
--
-- Easiest: run create-database.bat next to this file.
-- By hand, as a PostgreSQL superuser:
--   psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -v pw=YOUR_PASSWORD -f create-database.sql
-- In pgAdmin: run the two statements one at a time (CREATE DATABASE must run on its own),
-- with :'pw' replaced by the password in single quotes.
--
-- The extension creates its tables itself when the kernel connects.

CREATE ROLE fms_orders LOGIN PASSWORD :'pw';

CREATE DATABASE fms_orders OWNER fms_orders ENCODING 'UTF8';
