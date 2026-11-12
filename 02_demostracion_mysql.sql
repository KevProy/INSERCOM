-- MySQL dump 10.13  Distrib 8.4.13, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: insercom_demo
-- ------------------------------------------------------
-- Server version	8.4.13

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `insercom_demo`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `insercom_demo` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `insercom_demo`;

--
-- Table structure for table `app_meta`
--

DROP TABLE IF EXISTS `app_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_meta` (
  `id` int NOT NULL,
  `schema_version` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_meta`
--

LOCK TABLES `app_meta` WRITE;
/*!40000 ALTER TABLE `app_meta` DISABLE KEYS */;
INSERT INTO `app_meta` VALUES (1,2);
/*!40000 ALTER TABLE `app_meta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_users`
--

DROP TABLE IF EXISTS `app_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `project_id` (`project_id`),
  CONSTRAINT `app_users_ibfk_1` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
  CONSTRAINT `app_users_chk_1` CHECK (((case `role` when _utf8mb4'ADMIN' then 1 when _utf8mb4'BODEGA' then 1 when _utf8mb4'SUPERVISOR' then 1 when _utf8mb4'TRABAJADOR' then 1 when _utf8mb4'INVITADO' then 1 else 0 end) = 1)),
  CONSTRAINT `app_users_chk_2` CHECK (((`project_id` is not null) or ((case `role` when _utf8mb4'ADMIN' then 1 when _utf8mb4'BODEGA' then 1 else 0 end) = 1)))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_users`
--

LOCK TABLES `app_users` WRITE;
/*!40000 ALTER TABLE `app_users` DISABLE KEYS */;
INSERT INTO `app_users` VALUES (1,'admin','Administración Demo','ADMIN',NULL,1,'pbkdf2-sha256$210000$8Q1DVNh8pBwA1BRmK012qA==$sSGmgwWZK065htvTZxcpGqklU3igbPfvXNiNNtT3IC8=',NULL),(2,'bodega','Bodega Demo','BODEGA',NULL,1,'pbkdf2-sha256$210000$/R/1gkGwqAE/Y9HGTSZmQQ==$axhy3dv8VOV1L/u1lLo/uq+Lhp207r66VETVDQIW5Uo=',NULL),(3,'supervisor','Supervisión Demo','SUPERVISOR',1,1,'pbkdf2-sha256$210000$PrycKdZgVycFaIxh1BV40g==$TgbuJ2aclsbR1ZX+rADSw/RvITop8W1ABFqObl4eLLs=',NULL),(4,'trabajador','Trabajador Demo','TRABAJADOR',1,1,'pbkdf2-sha256$210000$2OOSpadURJBqJMJH539jeA==$xgZDhXoFmiakI2V7DntuA7CRECwHas+jTOzzHkcGGsM=',NULL),(5,'invitado','Invitado Demo','INVITADO',1,1,'pbkdf2-sha256$210000$bG3WbSkWCnXbNZ+urL0ABw==$uNYY8n6iF3DbrnMNMMmWIpqqa/nannJvnjFI5riu4kQ=',NULL),(6,'trabajador2','Trabajador Norte Demo','TRABAJADOR',2,1,'pbkdf2-sha256$210000$t01M8PuZg6qvDVc6sKTFSw==$16I5IhQTGugFReHtzUSFPAnXfWjIBnjwhSoIHSNiD1c=',NULL);
/*!40000 ALTER TABLE `app_users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` bigint NOT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `detail` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `actor_id` (`actor_id`),
  CONSTRAINT `audit_log_ibfk_1` FOREIGN KEY (`actor_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,1,'DEMO_INICIAL','SISTEMA',1,'Datos ficticios para demostración','2026-10-24 01:11:54');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `deleted_records`
--

DROP TABLE IF EXISTS `deleted_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deleted_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `entity_label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_by` bigint NOT NULL,
  `deleted_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `deleted_by` (`deleted_by`),
  CONSTRAINT `deleted_records_ibfk_1` FOREIGN KEY (`deleted_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `deleted_records_chk_1` CHECK ((`expires_at` > `deleted_at`)),
  CONSTRAINT `deleted_records_chk_2` CHECK (((case `entity_type` when _utf8mb4'PROYECTO' then 1 when _utf8mb4'USUARIO' then 1 when _utf8mb4'HERRAMIENTA' then 1 when _utf8mb4'MATERIAL' then 1 else 0 end) = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `deleted_records`
--

LOCK TABLES `deleted_records` WRITE;
/*!40000 ALTER TABLE `deleted_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `deleted_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `incidents`
--

DROP TABLE IF EXISTS `incidents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `incidents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `loan_id` bigint NOT NULL,
  `reporter_id` bigint NOT NULL,
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resolved` tinyint(1) NOT NULL DEFAULT '0',
  `resolution_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resource_id` (`resource_id`),
  KEY `project_id` (`project_id`),
  KEY `loan_id` (`loan_id`),
  KEY `reporter_id` (`reporter_id`),
  KEY `resolved_by` (`resolved_by`),
  CONSTRAINT `incidents_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`),
  CONSTRAINT `incidents_ibfk_2` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
  CONSTRAINT `incidents_ibfk_3` FOREIGN KEY (`loan_id`) REFERENCES `loans` (`id`),
  CONSTRAINT `incidents_ibfk_4` FOREIGN KEY (`reporter_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `incidents_ibfk_5` FOREIGN KEY (`resolved_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `incidents`
--

LOCK TABLES `incidents` WRITE;
/*!40000 ALTER TABLE `incidents` DISABLE KEYS */;
/*!40000 ALTER TABLE `incidents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `loans`
--

DROP TABLE IF EXISTS `loans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `loans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `borrower_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `returned_quantity` int NOT NULL DEFAULT '0',
  `due_date` date NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVO',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `closed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `request_id` (`request_id`),
  KEY `resource_id` (`resource_id`),
  KEY `project_id` (`project_id`),
  KEY `borrower_id` (`borrower_id`),
  CONSTRAINT `loans_ibfk_1` FOREIGN KEY (`request_id`) REFERENCES `requests` (`id`),
  CONSTRAINT `loans_ibfk_2` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`),
  CONSTRAINT `loans_ibfk_3` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
  CONSTRAINT `loans_ibfk_4` FOREIGN KEY (`borrower_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `loans_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `loans_chk_2` CHECK (((`returned_quantity` >= 0) and (`returned_quantity` <= `quantity`))),
  CONSTRAINT `loans_chk_3` CHECK (((case `status` when _utf8mb4'ACTIVO' then 1 when _utf8mb4'CERRADO' then 1 else 0 end) = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `loans`
--

LOCK TABLES `loans` WRITE;
/*!40000 ALTER TABLE `loans` DISABLE KEYS */;
/*!40000 ALTER TABLE `loans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `movements`
--

DROP TABLE IF EXISTS `movements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `loan_id` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity` int NOT NULL,
  `resource_condition` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `resource_id` (`resource_id`),
  KEY `project_id` (`project_id`),
  KEY `request_id` (`request_id`),
  KEY `loan_id` (`loan_id`),
  KEY `actor_id` (`actor_id`),
  CONSTRAINT `movements_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`),
  CONSTRAINT `movements_ibfk_2` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
  CONSTRAINT `movements_ibfk_3` FOREIGN KEY (`request_id`) REFERENCES `requests` (`id`),
  CONSTRAINT `movements_ibfk_4` FOREIGN KEY (`loan_id`) REFERENCES `loans` (`id`),
  CONSTRAINT `movements_ibfk_5` FOREIGN KEY (`actor_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `movements_chk_1` CHECK ((`quantity` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `movements`
--

LOCK TABLES `movements` WRITE;
/*!40000 ALTER TABLE `movements` DISABLE KEYS */;
INSERT INTO `movements` VALUES (1,1,NULL,NULL,NULL,1,'ENTRADA',20,NULL,'Carga inicial ficticia de demostración','2026-10-24 01:11:54'),(2,2,NULL,NULL,NULL,1,'ENTRADA',80,NULL,'Carga inicial ficticia de demostración','2026-10-24 01:11:54');
/*!40000 ALTER TABLE `movements` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `projects`
--

DROP TABLE IF EXISTS `projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `projects`
--

LOCK TABLES `projects` WRITE;
/*!40000 ALTER TABLE `projects` DISABLE KEYS */;
INSERT INTO `projects` VALUES (1,'OBR-001','Obra demostración Centro',1,NULL),(2,'OBR-002','Obra demostración Norte',1,NULL);
/*!40000 ALTER TABLE `projects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `requests`
--

DROP TABLE IF EXISTS `requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `due_date` date DEFAULT NULL,
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `decided_at` timestamp NULL DEFAULT NULL,
  `dispatched_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resource_id` (`resource_id`),
  KEY `project_id` (`project_id`),
  KEY `requester_id` (`requester_id`),
  KEY `decided_by` (`decided_by`),
  CONSTRAINT `requests_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`),
  CONSTRAINT `requests_ibfk_2` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
  CONSTRAINT `requests_ibfk_3` FOREIGN KEY (`requester_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `requests_ibfk_4` FOREIGN KEY (`decided_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `requests_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `requests_chk_2` CHECK (((case `status` when _utf8mb4'PENDIENTE' then 1 when _utf8mb4'APROBADA' then 1 when _utf8mb4'RECHAZADA' then 1 when _utf8mb4'DESPACHADA' then 1 else 0 end) = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `requests`
--

LOCK TABLES `requests` WRITE;
/*!40000 ALTER TABLE `requests` DISABLE KEYS */;
/*!40000 ALTER TABLE `requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `resources`
--

DROP TABLE IF EXISTS `resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `minimum_quantity` int NOT NULL DEFAULT '0',
  `available_quantity` int NOT NULL DEFAULT '0',
  `damaged_quantity` int NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  CONSTRAINT `resources_chk_1` CHECK (((case `type` when _utf8mb4'HERRAMIENTA' then 1 when _utf8mb4'MATERIAL' then 1 else 0 end) = 1)),
  CONSTRAINT `resources_chk_2` CHECK ((`minimum_quantity` >= 0)),
  CONSTRAINT `resources_chk_3` CHECK ((`available_quantity` >= 0)),
  CONSTRAINT `resources_chk_4` CHECK ((`damaged_quantity` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `resources`
--

LOCK TABLES `resources` WRITE;
/*!40000 ALTER TABLE `resources` DISABLE KEYS */;
INSERT INTO `resources` VALUES (1,'TAL-001','Taladro percutor','HERRAMIENTA','unidad','Bodega central · Estante A',5,20,0,1,NULL),(2,'CEM-001','Cemento gris','MATERIAL','bulto','Bodega central · Zona B',15,80,0,1,NULL);
/*!40000 ALTER TABLE `resources` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
