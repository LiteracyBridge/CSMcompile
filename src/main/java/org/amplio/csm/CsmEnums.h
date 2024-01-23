package org.amplio.csm;

// This file is run through the C pre-processor to create CsmEnums.java, for 
// example:
//    cd src/main/java/org/amplio/csm/
//    gcc -I ~/workspace/tbv2/src/inc -E CsmEnums.h | grep -v -E '^#' > CsmEnums.java
// CSM_EVENT_LIST and CSM_ACTION_LIST are defined in tb_enum_list.h, and are
// simply a sequence of "X(event)" and "X(action)".
// This scheme ensures that the Java list is in sync with the C list.
#include "tb_enum_list.h"

public class CsmEnums{
    #define X(v) v,
    public enum tknEvent {
        CSM_EVENT_LIST
    }
    public enum tknAction {
        CSM_ACTION_LIST
    }
    #undef X
}
