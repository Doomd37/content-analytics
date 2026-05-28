package com.contentanalytics.entity;

public enum UserStatus {
    ACTIVE,    // User can login
    INACTIVE,  // User exists but cannot login
    BANNED     // User is banned (security incident, violation, etc)
}
