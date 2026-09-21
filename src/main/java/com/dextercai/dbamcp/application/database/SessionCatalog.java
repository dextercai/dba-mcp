package com.dextercai.dbamcp.application.database;

/**
 * Fixed, bounded Oracle dynamic-performance queries for diagnosing user-session
 * pressure and blocking.  They deliberately omit SQL text, client identifiers,
 * operating-system users, and transaction identifiers.
 */
public final class SessionCatalog {
    public static final String SESSION_SUMMARY_SQL = """
            SELECT username,
                   status,
                   COUNT(*) AS session_count,
                   MIN(logon_time) AS oldest_logon_time,
                   MAX(last_call_et) AS longest_last_call_seconds
              FROM v$session
             WHERE type = 'USER'
               AND username IS NOT NULL
             GROUP BY username, status
             ORDER BY username, status
            """;

    public static final String LONG_RUNNING_TRANSACTIONS_SQL = """
            SELECT s.sid,
                   s.serial# AS serial_number,
                   s.username,
                   s.status,
                   t.start_date,
                   TRUNC((SYSDATE - t.start_date) * 86400) AS elapsed_seconds,
                   t.used_ublk,
                   t.used_urec
             FROM v$transaction t
              JOIN v$session s ON s.taddr = t.addr
             WHERE s.type = 'USER'
               AND s.username IS NOT NULL
               AND t.start_date <= SYSDATE - (60 / 86400)
             ORDER BY t.start_date, s.sid
            """;

    public static final String BLOCKING_SESSIONS_SQL = """
            SELECT waiter.sid AS waiting_sid,
                   waiter.serial# AS waiting_serial_number,
                   waiter.username AS waiting_username,
                   waiter.event AS waiting_event,
                   waiter.wait_class,
                   waiter.seconds_in_wait,
                   waiter.blocking_instance,
                   waiter.blocking_session,
                   blocker.serial# AS blocking_serial_number,
                   blocker.username AS blocking_username,
                   blocker.status AS blocking_status
              FROM v$session waiter
              LEFT JOIN v$session blocker ON blocker.sid = waiter.blocking_session
             WHERE waiter.type = 'USER'
               AND waiter.username IS NOT NULL
               AND waiter.blocking_session_status = 'VALID'
             ORDER BY waiter.seconds_in_wait DESC, waiter.sid
            """;

    private SessionCatalog() {
    }
}
