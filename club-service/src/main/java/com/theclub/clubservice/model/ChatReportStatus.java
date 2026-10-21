package com.theclub.clubservice.model;

public enum ChatReportStatus {
    OPEN,
    /** An admin removed the message */
    REMOVED,
    /** An admin kept the message */
    DISMISSED
}
