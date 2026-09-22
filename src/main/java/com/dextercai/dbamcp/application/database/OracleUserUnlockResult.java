package com.dextercai.dbamcp.application.database;

/** Deliberately minimal result: no roles, privileges, credentials, or SQL are returned. */
public record OracleUserUnlockResult(String username, String previousAccountStatus, String accountStatus) { }
