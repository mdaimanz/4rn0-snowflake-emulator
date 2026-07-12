package org.example.engine;

public enum StatementCategory {

    SELECT(0x1000),
    INSERT(0x3000 + 0x100),
    UPDATE(0x3000 + 0x200),
    DELETE(0x3000 + 0x300),
    MERGE(0x3000 + 0x400),
    MULTI_INSERT(0x3000 + 0x500),
    /**
     * Session control language: USE / SET / ALTER SESSION.
     */
    SCL(0x4000),
    /**
     * Transaction control language: BEGIN / COMMIT / ROLLBACK
     */
    TCL(0x5000),
    /**
     * Data definition language: CREATE / DROP / ALTER / GRANT ....
     */
    DDL(0x6000),
    UNKNOWN(0x0000);

    private final long typeId;

    StatementCategory(long typeId) { this.typeId = typeId; }

    public long typeId() { return typeId; }

    public boolean isDml() {
        return this == INSERT || this == UPDATE || this == DELETE || this == MERGE || this == MULTI_INSERT;
    }
}
