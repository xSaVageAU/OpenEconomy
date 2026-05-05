package savage.openeconomy.api;

/**
 * Represents the result of an account save operation.
 */
public enum SaveStatus {
    /**
     * The account was saved successfully.
     */
    SUCCESS,
    /**
     * The save failed because the account has been modified elsewhere (revision mismatch).
     */
    VERSION_COLLISION,
    /**
     * The save failed due to a technical error (e.g., I/O, database down).
     */
    ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
