package org.amplio.csm;

import java.util.List;

/**
 * The CsmData objects like CGroup and CState implement this interface to make validation easier.
 */
public interface CsmItem {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isValid();
    void fillErrors(List<String> errors);
}

