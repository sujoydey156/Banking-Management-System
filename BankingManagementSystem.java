import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Scanner;

/**
 * Banking Management System
 *
 * Oracle Database
 * Username : bank_user
 * Password : bank123
 * Database : XE
 * Host     : localhost
 * Port     : 1521
 *
 * Compatible with old Oracle JDBC drivers such as ojdbc14.jar
 */
public class BankingManagementSystem {

    // =====================================================
    // DATABASE CONFIGURATION
    // =====================================================

    static final String DB_URL =
            "jdbc:oracle:thin:@localhost:1521:XE";

    static final String DB_USER =
            "bank_user";

    static final String DB_PASSWORD =
            "bank123";


    // =====================================================
    // CONSTANTS
    // =====================================================

    static final int MAX_NAME_LEN = 50;
    static final int MAX_PHONE_LEN = 15;
    static final int MAX_ADDRESS_LEN = 100;

    static final Scanner sc = new Scanner(System.in);

    static final ConnectionPool POOL =
            new ConnectionPool(
                    DB_URL,
                    DB_USER,
                    DB_PASSWORD,
                    5
            );


    // =====================================================
    // CONNECTION POOL
    // =====================================================

    static final class ConnectionPool
            implements AutoCloseable {

        private final Deque<Connection> available =
                new ArrayDeque<Connection>();

        private final String url;
        private final String user;
        private final String password;


        ConnectionPool(
                String url,
                String user,
                String password,
                int initialSize) {

            this.url = url;
            this.user = user;
            this.password = password;

            try {

                Class.forName(
                        "oracle.jdbc.driver.OracleDriver"
                );

            } catch (ClassNotFoundException e) {

                throw new IllegalStateException(
                        "Oracle JDBC driver not found.",
                        e
                );
            }


            // Create initial connections

            for (int i = 0; i < initialSize; i++) {

                try {

                    available.push(
                            openConnection()
                    );

                } catch (SQLException e) {

                    System.out.println(
                            "Warning: Could not create "
                            + "connection: "
                            + e.getMessage()
                    );

                    break;
                }
            }
        }


        // =================================================
        // OPEN CONNECTION
        // =================================================

        private Connection openConnection()
                throws SQLException {

            return DriverManager.getConnection(
                    url,
                    user,
                    password
            );
        }


        // =================================================
        // BORROW CONNECTION
        // =================================================

        synchronized Connection borrow()
                throws SQLException {

            while (!available.isEmpty()) {

                Connection con =
                        available.pop();

                if (isUsable(con)) {

                    return con;
                }
            }

            // Create new connection if pool is empty

            return openConnection();
        }


        // =================================================
        // RELEASE CONNECTION
        // =================================================

        synchronized void release(
                Connection con) {

            if (con == null) {
                return;
            }


            try {

                if (!con.getAutoCommit()) {

                    con.setAutoCommit(true);
                }


                if (isUsable(con)) {

                    available.push(con);

                    return;
                }

            } catch (SQLException ignored) {

            }


            closeQuietly(con);
        }


        // =================================================
        // CHECK CONNECTION
        //
        // IMPORTANT:
        // We DO NOT use con.isValid(2)
        // because ojdbc14.jar is an old JDBC driver.
        // =================================================

        private boolean isUsable(
                Connection con) {

            if (con == null) {

                return false;
            }


            try {

                if (con.isClosed()) {

                    return false;
                }


                /*
                 * Compatible with old Oracle JDBC driver.
                 *
                 * Instead of:
                 *
                 * con.isValid(2)
                 *
                 * we execute:
                 *
                 * SELECT 1 FROM dual
                 */

                PreparedStatement ps =
                        null;

                ResultSet rs =
                        null;


                try {

                    ps = con.prepareStatement(
                            "SELECT 1 FROM dual"
                    );

                    rs = ps.executeQuery();

                    return rs.next();

                } finally {

                    if (rs != null) {

                        try {
                            rs.close();
                        } catch (SQLException ignored) {
                        }
                    }


                    if (ps != null) {

                        try {
                            ps.close();
                        } catch (SQLException ignored) {
                        }
                    }
                }

            } catch (SQLException e) {

                return false;
            }
        }


        // =================================================
        // CLOSE CONNECTION
        // =================================================

        private void closeQuietly(
                Connection con) {

            try {

                if (con != null) {

                    con.close();
                }

            } catch (SQLException ignored) {

            }
        }


        // =================================================
        // CLOSE POOL
        // =================================================

        @Override
        public void close() {

            synchronized (this) {

                while (!available.isEmpty()) {

                    closeQuietly(
                            available.pop()
                    );
                }
            }
        }
    }


    // =====================================================
    // PIN UTILITY
    // =====================================================

    static final class PinUtil {

        private static final SecureRandom RNG =
                new SecureRandom();


        // =================================================
        // HASH PIN
        // =================================================

        static String hash(
                String pin) {

            byte[] salt =
                    new byte[16];

            RNG.nextBytes(salt);


            byte[] digest =
                    sha256(
                            salt,
                            pin
                    );


            return Base64.getEncoder()
                    .encodeToString(salt)
                    + ":"
                    + Base64.getEncoder()
                    .encodeToString(digest);
        }


        // =================================================
        // MATCH PIN
        // =================================================

        static boolean matches(
                String pin,
                String stored) {

            try {

                if (stored == null
                        || !stored.contains(":")) {

                    return false;
                }


                String[] parts =
                        stored.split(":", 2);


                byte[] salt =
                        Base64.getDecoder()
                                .decode(parts[0]);


                byte[] expected =
                        Base64.getDecoder()
                                .decode(parts[1]);


                byte[] actual =
                        sha256(
                                salt,
                                pin
                        );


                return constantTimeEquals(
                        expected,
                        actual
                );

            } catch (Exception e) {

                return false;
            }
        }


        // =================================================
        // SHA-256
        // =================================================

        private static byte[] sha256(
                byte[] salt,
                String pin) {

            try {

                MessageDigest md =
                        MessageDigest.getInstance(
                                "SHA-256"
                        );


                md.update(salt);


                return md.digest(
                        pin.getBytes("UTF-8")
                );

            } catch (
                    NoSuchAlgorithmException e) {

                throw new IllegalStateException(e);

            } catch (
                    java.io.UnsupportedEncodingException e) {

                throw new IllegalStateException(e);
            }
        }


        // =================================================
        // CONSTANT TIME COMPARISON
        // =================================================

        private static boolean constantTimeEquals(
                byte[] a,
                byte[] b) {

            if (a.length != b.length) {

                return false;
            }


            int result = 0;


            for (int i = 0;
                 i < a.length;
                 i++) {

                result |=
                        a[i] ^ b[i];
            }


            return result == 0;
        }
    }


    // =====================================================
    // INPUT METHODS
    // =====================================================

    static long readLong(
            String prompt) {

        while (true) {

            System.out.print(prompt);

            String line =
                    sc.nextLine().trim();


            try {

                return Long.parseLong(line);

            } catch (NumberFormatException e) {

                System.out.println(
                        "Please enter a valid whole number."
                );
            }
        }
    }


    static int readInt(
            String prompt) {

        while (true) {

            System.out.print(prompt);

            String line =
                    sc.nextLine().trim();


            try {

                return Integer.parseInt(line);

            } catch (NumberFormatException e) {

                System.out.println(
                        "Please enter a valid whole number."
                );
            }
        }
    }


    static double readDouble(
            String prompt) {

        while (true) {

            System.out.print(prompt);

            String line =
                    sc.nextLine().trim();


            try {

                return Double.parseDouble(line);

            } catch (NumberFormatException e) {

                System.out.println(
                        "Please enter a valid number."
                );
            }
        }
    }


    static String readLine(
            String prompt) {

        System.out.print(prompt);

        return sc.nextLine().trim();
    }


    static String readPin(
            String prompt) {

        while (true) {

            String pin =
                    readLine(prompt);


            if (pin.matches("\\d{4}")) {

                return pin;
            }


            System.out.println(
                    "PIN must be exactly 4 digits."
            );
        }
    }


    // =====================================================
    // REGISTER CUSTOMER
    // =====================================================

    static void registerCustomer() {

        System.out.println();
        System.out.println(
                "===== REGISTER CUSTOMER ====="
        );


        String name =
                readLine(
                        "Enter name: "
                );


        String phone =
                readLine(
                        "Enter phone: "
                );


        String address =
                readLine(
                        "Enter address: "
                );


        String dob =
                readLine(
                        "Enter DOB (YYYY-MM-DD): "
                );


        // Name validation

        if (name.isEmpty()) {

            System.out.println(
                    "Name cannot be empty."
            );

            return;
        }


        if (name.length() > MAX_NAME_LEN) {

            System.out.println(
                    "Name must be at most "
                            + MAX_NAME_LEN
                            + " characters."
            );

            return;
        }


        // Phone validation

        if (phone.length() > MAX_PHONE_LEN) {

            System.out.println(
                    "Phone must be at most "
                            + MAX_PHONE_LEN
                            + " characters."
            );

            return;
        }


        // Address validation

        if (address.length() > MAX_ADDRESS_LEN) {

            System.out.println(
                    "Address must be at most "
                            + MAX_ADDRESS_LEN
                            + " characters."
            );

            return;
        }


        // DOB validation

        java.sql.Date dobDate;


        try {

            dobDate =
                    java.sql.Date.valueOf(dob);

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "DOB must be in YYYY-MM-DD format."
            );

            return;
        }


        String sql =
                "INSERT INTO customer "
                        + "(customer_id, name, phone, "
                        + "address, dob) "
                        + "VALUES "
                        + "(seq_customer.NEXTVAL, "
                        + "?, ?, ?, ?)";


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            PreparedStatement ps =
                    null;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setString(1, name);

                ps.setString(2, phone);

                ps.setString(3, address);

                ps.setDate(4, dobDate);


                ps.executeUpdate();


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


            System.out.println(
                    "Customer registered successfully!"
            );


        } catch (SQLException e) {

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // CREATE ACCOUNT
    // =====================================================

    static void createAccount() {

        System.out.println();
        System.out.println(
                "===== CREATE ACCOUNT ====="
        );


        int customerId =
                readInt(
                        "Enter customer ID: "
                );


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            // Check customer

            String checkSQL =
                    "SELECT customer_id "
                            + "FROM customer "
                            + "WHERE customer_id = ?";


            PreparedStatement checkPS =
                    null;


            try {

                checkPS =
                        con.prepareStatement(
                                checkSQL
                        );


                checkPS.setInt(
                        1,
                        customerId
                );


                ResultSet rs =
                        null;


                try {

                    rs =
                            checkPS.executeQuery();


                    if (!rs.next()) {

                        System.out.println(
                                "Customer not found!"
                        );

                        return;
                    }

                } finally {

                    if (rs != null) {

                        rs.close();
                    }
                }

            } finally {

                if (checkPS != null) {

                    checkPS.close();
                }
            }


            // Account type

            String accountType =
                    readLine(
                            "Enter account type "
                                    + "(SAVINGS/CURRENT): "
                    )
                            .toUpperCase();


            if (!accountType.equals(
                    "SAVINGS")
                    &&
                    !accountType.equals(
                            "CURRENT")) {

                System.out.println(
                        "Invalid account type."
                );

                return;
            }


            // Initial balance

            double balance =
                    readDouble(
                            "Enter initial deposit: "
                    );


            if (balance < 0) {

                System.out.println(
                        "Initial deposit cannot "
                                + "be negative."
                );

                return;
            }


            // PIN

            String pin =
                    readPin(
                            "Create 4-digit PIN: "
                    );


            String pinHash =
                    PinUtil.hash(pin);


            String sql =
                    "INSERT INTO account "
                            + "(account_no, customer_id, "
                            + "account_type, balance, "
                            + "pin, status) "
                            + "VALUES "
                            + "(seq_account.NEXTVAL, "
                            + "?, ?, ?, ?, 'ACTIVE')";


            PreparedStatement ps =
                    null;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setInt(
                        1,
                        customerId
                );


                ps.setString(
                        2,
                        accountType
                );


                ps.setDouble(
                        3,
                        balance
                );


                ps.setString(
                        4,
                        pinHash
                );


                ps.executeUpdate();

            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


            System.out.println(
                    "Account created successfully!"
            );


        } catch (SQLException e) {

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // DEPOSIT
    // =====================================================

    static void deposit() {

        System.out.println();
        System.out.println(
                "===== DEPOSIT MONEY ====="
        );


        long accountNo =
                readLong(
                        "Enter account number: "
                );


        double amount =
                readDouble(
                        "Enter amount: "
                );


        if (amount <= 0) {

            System.out.println(
                    "Amount must be greater than 0."
            );

            return;
        }


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            con.setAutoCommit(false);


            String sql =
                    "UPDATE account "
                            + "SET balance = balance + ? "
                            + "WHERE account_no = ? "
                            + "AND status = 'ACTIVE'";


            PreparedStatement ps =
                    null;


            int rows;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setDouble(
                        1,
                        amount
                );


                ps.setLong(
                        2,
                        accountNo
                );


                rows =
                        ps.executeUpdate();


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


            if (rows == 0) {

                con.rollback();


                System.out.println(
                        "Account not found or inactive."
                );

                return;
            }


            addTransaction(
                    con,
                    accountNo,
                    "DEPOSIT",
                    amount,
                    "Deposit"
            );


            con.commit();


            System.out.println(
                    "Rs. "
                            + amount
                            + " deposited successfully."
            );


        } catch (SQLException e) {

            rollbackQuietly(con);


            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // WITHDRAW
    // =====================================================

    static void withdraw() {

        System.out.println();
        System.out.println(
                "===== WITHDRAW MONEY ====="
        );


        long accountNo =
                readLong(
                        "Enter account number: "
                );


        String pin =
                readPin(
                        "Enter PIN: "
                );


        double amount =
                readDouble(
                        "Enter amount: "
                );


        if (amount <= 0) {

            System.out.println(
                    "Amount must be greater than 0."
            );

            return;
        }


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            con.setAutoCommit(false);


            // Verify PIN

            if (!verifyPin(
                    con,
                    accountNo,
                    pin)) {

                con.rollback();


                System.out.println(
                        "Invalid account/PIN."
                );

                return;
            }


            String sql =
                    "UPDATE account "
                            + "SET balance = balance - ? "
                            + "WHERE account_no = ? "
                            + "AND status = 'ACTIVE' "
                            + "AND balance >= ?";


            PreparedStatement ps =
                    null;


            int rows;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setDouble(
                        1,
                        amount
                );


                ps.setLong(
                        2,
                        accountNo
                );


                ps.setDouble(
                        3,
                        amount
                );


                rows =
                        ps.executeUpdate();


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


            if (rows == 0) {

                con.rollback();


                System.out.println(
                        "Insufficient balance."
                );

                return;
            }


            addTransaction(
                    con,
                    accountNo,
                    "WITHDRAW",
                    amount,
                    "Withdrawal"
            );


            con.commit();


            System.out.println(
                    "Rs. "
                            + amount
                            + " withdrawn successfully."
            );


        } catch (SQLException e) {

            rollbackQuietly(con);


            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // CHECK BALANCE
    // =====================================================

    static void checkBalance() {

        System.out.println();
        System.out.println(
                "===== BALANCE ENQUIRY ====="
        );


        long accountNo =
                readLong(
                        "Enter account number: "
                );


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            String sql =
                    "SELECT account_no, "
                            + "balance, status "
                            + "FROM account "
                            + "WHERE account_no = ?";


            PreparedStatement ps =
                    null;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setLong(
                        1,
                        accountNo
                );


                ResultSet rs =
                        null;


                try {

                    rs =
                            ps.executeQuery();


                    if (rs.next()) {

                        System.out.println(
                                "Account No : "
                                        + rs.getLong(
                                        "account_no")
                        );


                        System.out.println(
                                "Balance    : Rs. "
                                        + rs.getDouble(
                                        "balance")
                        );


                        System.out.println(
                                "Status     : "
                                        + rs.getString(
                                        "status")
                        );

                    } else {

                        System.out.println(
                                "Account not found."
                        );
                    }


                } finally {

                    if (rs != null) {

                        rs.close();
                    }
                }


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


        } catch (SQLException e) {

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // TRANSFER
    // =====================================================

    static void transfer() {

        System.out.println();
        System.out.println(
                "===== MONEY TRANSFER ====="
        );


        long fromAccount =
                readLong(
                        "Sender account: "
                );


        long toAccount =
                readLong(
                        "Receiver account: "
                );


        if (fromAccount == toAccount) {

            System.out.println(
                    "Sender and receiver "
                            + "cannot be same."
            );

            return;
        }


        String pin =
                readPin(
                        "Sender PIN: "
                );


        double amount =
                readDouble(
                        "Enter amount: "
                );


        if (amount <= 0) {

            System.out.println(
                    "Amount must be greater than 0."
            );

            return;
        }


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            con.setAutoCommit(false);


            // Verify sender PIN

            if (!verifyPin(
                    con,
                    fromAccount,
                    pin)) {

                con.rollback();


                System.out.println(
                        "Invalid sender account/PIN."
                );

                return;
            }


            // Withdraw from sender

            String withdrawSQL =
                    "UPDATE account "
                            + "SET balance = balance - ? "
                            + "WHERE account_no = ? "
                            + "AND status = 'ACTIVE' "
                            + "AND balance >= ?";


            PreparedStatement ps1 =
                    null;


            int rows1;


            try {

                ps1 =
                        con.prepareStatement(
                                withdrawSQL
                        );


                ps1.setDouble(
                        1,
                        amount
                );


                ps1.setLong(
                        2,
                        fromAccount
                );


                ps1.setDouble(
                        3,
                        amount
                );


                rows1 =
                        ps1.executeUpdate();


            } finally {

                if (ps1 != null) {

                    ps1.close();
                }
            }


            if (rows1 == 0) {

                con.rollback();


                System.out.println(
                        "Insufficient balance."
                );

                return;
            }


            // Deposit into receiver

            String depositSQL =
                    "UPDATE account "
                            + "SET balance = balance + ? "
                            + "WHERE account_no = ? "
                            + "AND status = 'ACTIVE'";


            PreparedStatement ps2 =
                    null;


            int rows2;


            try {

                ps2 =
                        con.prepareStatement(
                                depositSQL
                        );


                ps2.setDouble(
                        1,
                        amount
                );


                ps2.setLong(
                        2,
                        toAccount
                );


                rows2 =
                        ps2.executeUpdate();


            } finally {

                if (ps2 != null) {

                    ps2.close();
                }
            }


            if (rows2 == 0) {

                con.rollback();


                System.out.println(
                        "Receiver account "
                                + "not found."
                );

                return;
            }


            // Log sender transaction

            addTransaction(
                    con,
                    fromAccount,
                    "TRANSFER",
                    amount,
                    "Money Sent"
            );


            // Log receiver transaction

            addTransaction(
                    con,
                    toAccount,
                    "TRANSFER",
                    amount,
                    "Money Received"
            );


            // Commit complete transfer

            con.commit();


            System.out.println(
                    "Rs. "
                            + amount
                            + " transferred successfully."
            );


        } catch (SQLException e) {

            rollbackQuietly(con);


            System.out.println(
                    "Transfer failed: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // VERIFY PIN
    // =====================================================

    static boolean verifyPin(
            Connection con,
            long accountNo,
            String pin)
            throws SQLException {


        String sql =
                "SELECT pin "
                        + "FROM account "
                        + "WHERE account_no = ? "
                        + "AND status = 'ACTIVE'";


        PreparedStatement ps =
                null;


        try {

            ps =
                    con.prepareStatement(sql);


            ps.setLong(
                    1,
                    accountNo
            );


            ResultSet rs =
                    null;


            try {

                rs =
                        ps.executeQuery();


                if (!rs.next()) {

                    return false;
                }


                return PinUtil.matches(
                        pin,
                        rs.getString("pin")
                );


            } finally {

                if (rs != null) {

                    rs.close();
                }
            }


        } finally {

            if (ps != null) {

                ps.close();
            }
        }
    }


    // =====================================================
    // ADD TRANSACTION
    // =====================================================

    static void addTransaction(
            Connection con,
            long accountNo,
            String type,
            double amount,
            String description)
            throws SQLException {


        String sql =
                "INSERT INTO bank_transaction "
                        + "(transaction_id, "
                        + "account_no, "
                        + "transaction_type, "
                        + "amount, "
                        + "description) "
                        + "VALUES "
                        + "(seq_transaction.NEXTVAL, "
                        + "?, ?, ?, ?)";


        PreparedStatement ps =
                null;


        try {

            ps =
                    con.prepareStatement(sql);


            ps.setLong(
                    1,
                    accountNo
            );


            ps.setString(
                    2,
                    type
            );


            ps.setDouble(
                    3,
                    amount
            );


            ps.setString(
                    4,
                    description
            );


            ps.executeUpdate();


        } finally {

            if (ps != null) {

                ps.close();
            }
        }
    }


    // =====================================================
    // ROLLBACK
    // =====================================================

    static void rollbackQuietly(
            Connection con) {

        try {

            if (con != null) {

                con.rollback();
            }

        } catch (SQLException e) {

            System.out.println(
                    "Rollback error: "
                            + e.getMessage()
            );
        }
    }


    // =====================================================
    // TRANSACTION HISTORY
    // =====================================================

    static void transactionHistory() {

        System.out.println();
        System.out.println(
                "===== TRANSACTION HISTORY ====="
        );


        long accountNo =
                readLong(
                        "Enter account number: "
                );


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            String sql =
                    "SELECT transaction_id, "
                            + "transaction_type, "
                            + "amount, "
                            + "transaction_date, "
                            + "description "
                            + "FROM bank_transaction "
                            + "WHERE account_no = ? "
                            + "ORDER BY transaction_date DESC";


            PreparedStatement ps =
                    null;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setLong(
                        1,
                        accountNo
                );


                ResultSet rs =
                        null;


                try {

                    rs =
                            ps.executeQuery();


                    boolean found = false;


                    while (rs.next()) {

                        found = true;


                        System.out.println(
                                "ID: "
                                        + rs.getLong(
                                        "transaction_id")
                                        + " | Type: "
                                        + rs.getString(
                                        "transaction_type")
                                        + " | Amount: Rs. "
                                        + rs.getDouble(
                                        "amount")
                                        + " | Date: "
                                        + rs.getTimestamp(
                                        "transaction_date")
                                        + " | Description: "
                                        + rs.getString(
                                        "description")
                        );
                    }


                    if (!found) {

                        System.out.println(
                                "No transactions found."
                        );
                    }


                } finally {

                    if (rs != null) {

                        rs.close();
                    }
                }


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


        } catch (SQLException e) {

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // CUSTOMER DETAILS
    // =====================================================

    static void customerDetails() {

        System.out.println();
        System.out.println(
                "===== CUSTOMER DETAILS ====="
        );


        int customerId =
                readInt(
                        "Enter customer ID: "
                );


        Connection con = null;


        try {

            con =
                    POOL.borrow();


            String sql =
                    "SELECT customer_id, "
                            + "name, phone, "
                            + "address, dob "
                            + "FROM customer "
                            + "WHERE customer_id = ?";


            PreparedStatement ps =
                    null;


            try {

                ps =
                        con.prepareStatement(sql);


                ps.setInt(
                        1,
                        customerId
                );


                ResultSet rs =
                        null;


                try {

                    rs =
                            ps.executeQuery();


                    if (rs.next()) {

                        System.out.println(
                                "Customer ID : "
                                        + rs.getInt(
                                        "customer_id")
                        );


                        System.out.println(
                                "Name        : "
                                        + rs.getString(
                                        "name")
                        );


                        System.out.println(
                                "Phone       : "
                                        + rs.getString(
                                        "phone")
                        );


                        System.out.println(
                                "Address     : "
                                        + rs.getString(
                                        "address")
                        );


                        System.out.println(
                                "DOB         : "
                                        + rs.getDate(
                                        "dob")
                        );

                    } else {

                        System.out.println(
                                "Customer not found."
                        );
                    }


                } finally {

                    if (rs != null) {

                        rs.close();
                    }
                }


            } finally {

                if (ps != null) {

                    ps.close();
                }
            }


        } catch (SQLException e) {

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );


        } finally {

            POOL.release(con);
        }
    }


    // =====================================================
    // MAIN
    // =====================================================

    public static void main(
            String[] args) {


        Connection testCon = null;


        try {

            testCon =
                    POOL.borrow();


            System.out.println();

            System.out.println(
                    "===================================="
            );

            System.out.println(
                    " Database Connected Successfully!"
            );

            System.out.println(
                    " User : " + DB_USER
            );

            System.out.println(
                    "===================================="
            );


        } catch (SQLException e) {

            System.out.println();

            System.out.println(
                    "Database connection failed!"
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            return;


        } finally {

            POOL.release(testCon);
        }


        // =================================================
        // MENU
        // =================================================

        try {

            while (true) {

                System.out.println();

                System.out.println(
                        "===================================="
                );

                System.out.println(
                        "      BANKING MANAGEMENT SYSTEM"
                );

                System.out.println(
                        "===================================="
                );

                System.out.println(
                        "1. Register Customer"
                );

                System.out.println(
                        "2. Create Account"
                );

                System.out.println(
                        "3. Deposit Money"
                );

                System.out.println(
                        "4. Withdraw Money"
                );

                System.out.println(
                        "5. Check Balance"
                );

                System.out.println(
                        "6. Transfer Money"
                );

                System.out.println(
                        "7. Transaction History"
                );

                System.out.println(
                        "8. Customer Details"
                );

                System.out.println(
                        "9. Exit"
                );

                System.out.println(
                        "===================================="
                );


                int choice =
                        readInt(
                                "Enter your choice: "
                        );


                switch (choice) {

                    case 1:

                        registerCustomer();

                        break;


                    case 2:

                        createAccount();

                        break;


                    case 3:

                        deposit();

                        break;


                    case 4:

                        withdraw();

                        break;


                    case 5:

                        checkBalance();

                        break;


                    case 6:

                        transfer();

                        break;


                    case 7:

                        transactionHistory();

                        break;


                    case 8:

                        customerDetails();

                        break;


                    case 9:

                        System.out.println();

                        System.out.println(
                                "Thank you for using "
                                        + "Banking Management System!"
                        );

                        return;


                    default:

                        System.out.println(
                                "Invalid choice!"
                        );
                }
            }


        } finally {

            sc.close();

            POOL.close();
        }
    }
}