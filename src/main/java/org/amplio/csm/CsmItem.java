package org.amplio.csm;

import java.util.List;

/**
 * The CsmData objects like CGroup and CState implement this interface to make validation easier.
 */
public interface CsmItem {
    /**
     * @return True if the item is valid, false otherwise.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isValid();

    /**
     * Adds any errors in the item to the given list. Specific errors depend on the item.
     * Does nothing if the item is valid.
     *
     * @param errors A list to be appended with any errors.
     */
    void fillErrors(List<String> errors);
}

