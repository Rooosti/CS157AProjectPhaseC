-- create_and_populate.sql
-- CS157A Final Project - Warehouse / Customer / Purchasing Domain

-- 1. Clean up old objects ----------------------------------------------------
DROP DATABASE IF EXISTS cs157a_warehouse;
CREATE DATABASE cs157a_warehouse;
USE cs157a_warehouse;

-- if exists...
DROP TRIGGER IF EXISTS update_stock_after_purchase;

DROP VIEW IF EXISTS CustomerPurchaseSummary;

DROP PROCEDURE IF EXISTS AddNewCustomer;

DROP TABLE IF EXISTS TransactionLineItem;
DROP TABLE IF EXISTS Manages;
DROP TABLE IF EXISTS Purchases;
DROP TABLE IF EXISTS Employee;
DROP TABLE IF EXISTS Customer;
DROP TABLE IF EXISTS WarehouseItem;
DROP TABLE IF EXISTS Warehouse;

-- 2. Table definitions -----------------------------------------

-- Warehouse
CREATE TABLE Warehouse (
    WarehouseID INT PRIMARY KEY,
    Location    VARCHAR(100) NOT NULL
);

-- Employee
CREATE TABLE Employee (
    EmployeeID  INT PRIMARY KEY,
    WarehouseID INT,
    Name        VARCHAR(100) NOT NULL,
    Role        VARCHAR(50)  NOT NULL,
    FOREIGN KEY (WarehouseID) REFERENCES Warehouse(WarehouseID)
);

-- Customer
CREATE TABLE Customer (
    MembershipID INT PRIMARY KEY,
    Name      VARCHAR(100) NOT NULL,
    DateOfBirth  DATE NOT NULL,
    Email        VARCHAR(100) UNIQUE,
    Phone        VARCHAR(20)
);

-- Purchases
CREATE TABLE Purchases (
    TransactionID INT AUTO_INCREMENT PRIMARY KEY,
    MembershipID  INT NOT NULL,
    Date          DATE NOT NULL,
    Total         DECIMAL(10, 2) NOT NULL CHECK (Total >= 0),
    FOREIGN KEY (MembershipID) REFERENCES Customer(MembershipID) ON DELETE CASCADE
);

-- WarehouseItem
CREATE TABLE WarehouseItem (
    ItemID    INT PRIMARY KEY,
    UnitPrice DECIMAL(10, 2) NOT NULL CHECK (UnitPrice >= 0)
);

-- TransactionLineItem
CREATE TABLE TransactionLineItem (
 TransactionID INT     NOT NULL,
 LineNumber    INT     NOT NULL,
 ItemID        INT     NOT NULL,
 Quantity      INT     NOT NULL CHECK (Quantity > 0),
 CONSTRAINT pk_transactionlineitem PRIMARY KEY (TransactionID, LineNumber),
 CONSTRAINT fk_tli_transaction FOREIGN KEY (TransactionID)
     REFERENCES Purchases(TransactionID)
     ON DELETE CASCADE
     ON UPDATE CASCADE,
 CONSTRAINT fk_tli_item FOREIGN KEY (ItemID)
     REFERENCES WarehouseItem(ItemID)
     ON UPDATE CASCADE
);

-- Manages (Warehouse <-> Item stock)
CREATE TABLE Manages (
    WarehouseID INT NOT NULL,
    ItemID      INT NOT NULL,
    Stock       INT NOT NULL CHECK (Stock >= 0),
    PRIMARY KEY (WarehouseID, ItemID),
    FOREIGN KEY (WarehouseID) REFERENCES Warehouse(WarehouseID),
    FOREIGN KEY (ItemID)      REFERENCES WarehouseItem(ItemID)
);

-- 3. Indexes -----------------------------------------------------------

-- Index on Employee for lookup by Name, Role
CREATE INDEX emp_idx ON Employee (Name, Role);

-- 4. Trigger (from Phase B) -----------------------------------------------

DELIMITER //
CREATE TRIGGER update_stock_after_purchase
    AFTER INSERT ON TransactionLineItem
    FOR EACH ROW
BEGIN
    -- stock decrement: subtract from one matching row
    UPDATE Manages
    SET Stock = Stock - NEW.Quantity
    WHERE ItemID = NEW.ItemID
        LIMIT 1;
END//
DELIMITER ;

-- 5. View (Part 6 -------------------------------------------------

-- Shows customer, how many transactions they have,  total spent.
CREATE OR REPLACE VIEW CustomerPurchaseSummary AS
SELECT
    c.MembershipID,
    c.Name,
    COUNT(DISTINCT p.TransactionID) AS NumTransactions,
    COALESCE(SUM(p.Total), 0)      AS TotalSpent
FROM Customer c
         LEFT JOIN Purchases p ON c.MembershipID = p.MembershipID
GROUP BY c.MembershipID, c.Name;

-- 6. Stored Procedure (Part 6) -------------------------------------

DELIMITER //
CREATE PROCEDURE AddNewCustomer(
    IN p_MembershipID INT,
    IN p_Name         VARCHAR(100),
    IN p_DateOfBirth  DATE,
    IN p_Email        VARCHAR(100),
    IN p_Phone        VARCHAR(20)
)
BEGIN
INSERT INTO Customer(MembershipID, Name, DateOfBirth, Email, Phone)
VALUES (p_MembershipID, p_Name, p_DateOfBirth, p_Email, p_Phone);
END//
DELIMITER ;

-- 7. Sample data --------------------------------------------

-- warehouse
INSERT INTO Warehouse (WarehouseID, Location) VALUES
    (1, 'San Jose, CA'),
    (2, 'Los Angeles, CA'),
    (3, 'Sacramento, CA');

-- Employee
INSERT INTO Employee (EmployeeID, WarehouseID, Name, Role) VALUES
    (101, 1, 'Alex Johnson', 'Warehouse Manager'),
    (102, 1, 'Maria Lopez', 'Inventory Clerk'),
    (103, 2, 'Tyler Awender', 'Forklift Operator'),
    (104, 2, 'Sarah Lee', 'Supervisor'),
    (105, 3, 'David Nguyen', 'Technician');

-- Customer
INSERT INTO Customer (MembershipID, Name, DateOfBirth, Email, Phone) VALUES
    (1, 'Alice Johnson', '1990-05-14', 'alice.johnson@example.com', '555-123-4567'),
    (2, 'Brian Smith',   '1988-11-02', 'brian.smith@example.com',   '555-234-5678'),
    (3, 'Catherine Lee', '1993-08-21', 'catherine.lee@example.com', '555-345-6789'),
    (4, 'David Thompson','1978-01-09', 'david.thompson@example.com','555-456-7890'),
    (5, 'Emily Davis',   '1995-04-09', 'emily.davis@example.com',   '555-567-8901');

-- Purchases
INSERT INTO Purchases (TransactionID, MembershipID, Date, Total) VALUES
        (1001, 1, '2025-01-12', 59.99),
        (1002, 1, '2025-02-03', 42.50),
        (1003, 2, '2025-03-19', 89.00),
        (1004, 3, '2025-04-07', 120.49),
        (1005, 4, '2025-05-22', 39.99),
        (1006, 4, '2025-06-14', 101.23),
        (1007, 5, '2025-07-09', 75.00),
        (1008, 5, '2025-08-19', 49.99),
        (1009, 3, '2025-09-05', 56.80),
        (1010, 2, '2025-10-02', 129.90);

-- WarehouseItem
INSERT INTO WarehouseItem (ItemID, UnitPrice) VALUES
    (1, 89.99),
    (2, 99.00),
    (3, 49.50),
    (4, 35.95),
    (5, 119.75);

-- TransactionLineItem
INSERT INTO TransactionLineItem (TransactionID, LineNumber, ItemID, Quantity) VALUES
    (1001, 1, 1, 1),
    (1002, 1, 3, 1),
    (1002, 2, 4, 1),
    (1003, 1, 2, 1),
    (1004, 1, 1, 2),
    (1005, 1, 3, 1),
    (1006, 1, 5, 1),
    (1007, 1, 4, 2),
    (1008, 1, 3, 1),
    (1009, 1, 2, 1),
    (1010, 1, 5, 1);

-- Manages
INSERT INTO Manages (WarehouseID, ItemID, Stock) VALUES
    (1, 1, 120),
    (1, 2, 75),
    (1, 3, 300),
    (1, 4, 90),
    (2, 4, 40),
    (2, 5, 60),
    (3, 2, 200),
    (3, 3, 250),
    (3, 5, 180);
