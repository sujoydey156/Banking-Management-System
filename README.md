# Banking Management System

A console-based **Banking Management System** developed using **Java, JDBC, and Oracle Database**. The application provides basic banking operations such as customer registration, account creation, deposits, withdrawals, money transfers, balance enquiry, transaction history, and customer details.

## Features

* Customer Registration
* Customer Details
* Bank Account Creation
* Savings and Current Account Support
* Deposit Money
* Withdraw Money
* Balance Enquiry
* Money Transfer
* Transaction History
* PIN-based Authentication
* Input Validation
* Oracle Database Connectivity using JDBC
* Transaction Management with Commit and Rollback

## Technology Stack

* **Programming Language:** Java
* **Database:** Oracle Database
* **Database Connectivity:** JDBC
* **JDBC Driver:** Oracle JDBC Driver
* **Interface:** Console / Command Line

## Project Structure

```text
Banking-Management-System/
│
├── BankingManagementSystem.java
└── README.md
```

## Database Tables

The application uses the following Oracle database tables:

### CUSTOMER

Stores customer information.

| Column      | Description           |
| ----------- | --------------------- |
| CUSTOMER_ID | Unique customer ID    |
| NAME        | Customer name         |
| PHONE       | Customer phone number |
| ADDRESS     | Customer address      |
| DOB         | Date of birth         |

### ACCOUNT

Stores bank account information.

| Column       | Description             |
| ------------ | ----------------------- |
| ACCOUNT_NO   | Unique account number   |
| CUSTOMER_ID  | Customer ID             |
| ACCOUNT_TYPE | SAVINGS or CURRENT      |
| BALANCE      | Current account balance |
| PIN          | Account PIN             |
| STATUS       | Account status          |

### BANK_TRANSACTION

Stores transaction records.

| Column           | Description             |
| ---------------- | ----------------------- |
| TRANSACTION_ID   | Unique transaction ID   |
| ACCOUNT_NO       | Account number          |
| TRANSACTION_TYPE | Transaction type        |
| AMOUNT           | Transaction amount      |
| TRANSACTION_DATE | Date and time           |
| DESCRIPTION      | Transaction description |

## Application Menu

```text
====================================
      BANKING MANAGEMENT SYSTEM
====================================
1. Register Customer
2. Create Account
3. Deposit Money
4. Withdraw Money
5. Check Balance
6. Transfer Money
7. Transaction History
8. Customer Details
9. Exit
====================================
```

## Requirements

Before running the application, install/configure:

1. Java JDK
2. Oracle Database
3. Oracle SQL*Plus or another Oracle SQL client
4. Oracle JDBC Driver

## Oracle Database Setup

Create an Oracle user:

```sql
CREATE USER bank_user IDENTIFIED BY bank123;

GRANT CONNECT, RESOURCE TO bank_user;
```

Connect to the database:

```sql
CONNECT bank_user/bank123
```

Create the required tables and sequences according to your database configuration.

The Java application expects the Oracle database URL:

```text
jdbc:oracle:thin:@localhost:1521:XE
```

## JDBC Configuration

The application requires three database values:

```text
DB_URL
DB_USER
DB_PASSWORD
```

Example for Windows Command Prompt:

```cmd
set DB_URL=jdbc:oracle:thin:@localhost:1521:XE
set DB_USER=bank_user
set DB_PASSWORD=bank123
```

Do not commit database passwords or other credentials to a public GitHub repository.

## Compile

If the Oracle JDBC driver is available as `ojdbc14.jar`, compile using:

```cmd
javac -cp ".;path\to\ojdbc14.jar" BankingManagementSystem.java
```

Example:

```cmd
javac -cp ".;D:\app\oracle\product\10.2.0\server\jdbc\lib\ojdbc14.jar" BankingManagementSystem.java
```

## Run

```cmd
java -cp ".;path\to\ojdbc14.jar" BankingManagementSystem
```

Example:

```cmd
java -cp ".;D:\app\oracle\product\10.2.0\server\jdbc\lib\ojdbc14.jar" BankingManagementSystem
```

## Sample Workflow

### Register Customer

```text
Enter your choice: 1

===== REGISTER CUSTOMER =====
Enter name: Sujoy Dey
Enter phone: 6293557893
Enter address: Kolkata
Enter DOB (YYYY-MM-DD): 2004-09-23

Customer registered successfully!
```

### Create Account

```text
Enter your choice: 2

===== CREATE ACCOUNT =====
Enter customer ID: 1001
Enter account type (SAVINGS/CURRENT): SAVINGS
Enter initial deposit: 1000
Create 4-digit PIN: ****

Account created successfully!
```

### Deposit

```text
Enter your choice: 3

===== DEPOSIT MONEY =====
Enter account number: 10001
Enter amount: 500

Rs. 500 deposited successfully.
```

### Withdraw

```text
Enter your choice: 4

===== WITHDRAW MONEY =====
Enter account number: 10001
Enter PIN: ****
Enter amount: 200

Rs. 200 withdrawn successfully.
```

### Transfer

```text
Enter your choice: 6

===== MONEY TRANSFER =====
Sender account: 10001
Receiver account: 10002
Sender PIN: ****
Enter amount: 300

Rs. 300 transferred successfully.
```

## Security

The project is designed to avoid storing database credentials directly in the source code.

For a production banking application, additional security measures should be implemented, including:

* Strong password/PIN hashing such as Argon2 or bcrypt
* Secure credential management
* Role-based access control
* HTTPS/TLS for network communication
* Audit logging
* Account lockout after repeated failed PIN attempts
* Proper monetary types such as `BigDecimal` instead of `double`

## Learning Outcomes

This project demonstrates practical understanding of:

* Core Java
* Object-oriented programming
* JDBC
* Oracle Database
* SQL
* Prepared Statements
* Exception Handling
* Database Transactions
* Commit and Rollback
* CRUD Operations
* Input Validation
* Relational Database Design

## Future Enhancements

* GUI using JavaFX or Swing
* Web application using Spring Boot
* REST APIs
* Admin login
* Customer login
* Fund transfer receipt
* Account statement generation
* Email/SMS notifications
* Multiple branch support
* Role-based authorization
* Automated testing

## Author

**Sujoy Dey**

B.Tech Computer Science and Engineering Student

## License

This project is intended for educational and portfolio purposes.
