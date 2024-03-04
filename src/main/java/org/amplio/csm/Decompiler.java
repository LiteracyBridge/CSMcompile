package org.amplio.csm;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a .csm file and recreates the CsmData that will produce it.
 */
public class Decompiler {
    public File input;
    private final CsmInputStream cis;

    int version;
    int numStates;
    int numGroups;
    int[] stateOffsets;
    int[] groupOffsets;
    int stringPoolOffset;
    int stringPoolLength;

    List<GroupBits> groupBits = new ArrayList<>();
    List<StateBits> stateBits = new ArrayList<>();
    Map<Integer,String> stringPool = new LinkedHashMap<>();
    Set<Integer> stringsUsed = new HashSet<>();

    public Decompiler(File input) throws FileNotFoundException {
        this.input = input;
        FileInputStream fis = new FileInputStream(input);
        cis = new CsmInputStream(fis);
    }

    /**
     * Reads the sections of the Csm, in the order they were written.
     * @throws IOException if an error occurs reading the .csm file.
     */
    public void read() throws IOException {
        readHeader();
        readStates();
        readGroups();
        readStringPool();
    }

    /**
     * Creates a CsmData object corresponding to the bits read from a .csm file.
     * @return the CsmData object.
     */
    public CsmData getCsmData() {
        CsmData csmData = new CsmData();
        for (GroupBits gb : groupBits) {
            gb.addGroupTo(csmData);
        }
        for (StateBits sb : stateBits) {
            sb.addStateTo(csmData);
        }
        List<String> errors = new ArrayList<>();
        csmData.fillErrors(errors);
        return csmData;
    }

    /**
     * Reads the header.
     * TODO: use the read offsets to validate the actual offsets as groups and states are read.
     * @throws IOException if the .csm file can't be read.
     */
    private void readHeader() throws IOException {
        version = cis.readU16();
        numStates = cis.readU8();
        numGroups = cis.readU8();
        stateOffsets = new int[numStates];
        for (int i=0; i<numStates; ++i) stateOffsets[i] = cis.readU16();
        groupOffsets = new int[numGroups];
        for (int i=0; i<numGroups; ++i) groupOffsets[i] = cis.readU16();
        stringPoolOffset = cis.readU16();
        stringPoolLength = (int)(input.length() - stringPoolLength);
    }

    /**
     * Reads the states of the CSM. The string pool has not been read, so actual name of the states and groups, and the
     * actions' parameter strings aren't yet known, only the offsets of those strings.
     * @throws IOException if the .csm file can't be read.
     */
    private void readStates() throws IOException {
        for (int iState=0; iState<numStates; ++iState) {
            // About the state.
            int stateNameOffset = cis.readU16();
            int numHandlersInState = cis.readU8();
            int numActionsInState = cis.readU8();
            StateBits stateBits = new StateBits(stateNameOffset, numHandlersInState, numActionsInState);
            // Read actions
            for (int iAction=0; iAction < numActionsInState; ++iAction) {
                int actionIx = cis.readU16();
                int stringOffset = cis.readU16();
                stateBits.addAction(actionIx, stringOffset);
            }
            // Read event handlers and inherited groups
            for (int iHandler=0; iHandler < numHandlersInState; ++iHandler) {
                int eventIx = cis.readU8();
                int newStateIx = cis.readU8();
                if (eventIx == 0xff) {
                    // Inherited group.
                    stateBits.addGroup(newStateIx);
                } else {
                    stateBits.addEvent(eventIx, newStateIx);
                }
            }
            this.stateBits.add(stateBits);
        }
    }

    /**
     * Reads the groups of the CSM. The string pool has not been read, so actual name of the states and groups aren't
     * yet known, only the offsets of those strings.
     * @throws IOException if the .csm file can't be read.
     */
    private void readGroups() throws IOException {
        for (int iGroup=0; iGroup<numGroups; ++iGroup) {
            int groupNameOffset = cis.readU16();
            int numHandlersInGroup = cis.readU8();
            int numActionsInGroup = cis.readU8(); // zero
            GroupBits groupBits = new GroupBits(groupNameOffset, numHandlersInGroup);
            for (int iHandler=0; iHandler < numHandlersInGroup; ++iHandler) {
                int eventIx = cis.readU8();
                int newStateIx = cis.readU8();
                groupBits.addEvent(eventIx, newStateIx);
            }
            this.groupBits.add(groupBits);
        }
    }

    private class GroupBits {
        final int nameOffset;
        final int numHandlers;
        final List<EventBits> events = new ArrayList<>();

        GroupBits(int nameOffset, int numHandlers) {
            this.nameOffset = nameOffset;
            this.numHandlers = numHandlers;
        }
        void addEvent(int eventIx, int newStateIx) {
            events.add(new EventBits(eventIx, newStateIx));
        }

        String getName() {
            return getString(nameOffset);
        }
        void addGroupTo(CsmData csmData) {
            CsmData.CGroup newGroup = csmData.addCGroup(getName());
            for (EventBits eb : events) {
                newGroup.eventMapping.add(csmData.new CEvent(eb.getEventName(), eb.getNewStateName()));
            }
        }
    }

    /**
     * CStates consist of name (offset), count of groups & event handlers, count of actions.
     */
    private class StateBits extends GroupBits {
        final int numActions;
        final List<ActionBits> actions = new ArrayList<>();
        final LinkedList<Integer> groups = new LinkedList<>();

        private StateBits(int nameOffset, int numHandlers, int numActions) {
            super(nameOffset, numHandlers);
            this.numActions = numActions;
        }
        void addAction(int actionIx, int paramStringOffset) {
            actions.add(new ActionBits(actionIx, paramStringOffset));
        }
        void addGroup(int groupIx) {
            // Groups are in the CSM reversed from the source.
            //  --> first matched == last specified; LIFO
            groups.addFirst(groupIx);
        }

        void addStateTo(CsmData csmData) {
            CsmData.CState newState = csmData.addState(getName());
            for (ActionBits ab : actions) {
                newState.actions.add(csmData.new CAction(ab.getActionName(), ab.getParamString()));
            }
            for (int groupIx : groups) {
                GroupBits gb = groupBits.get(groupIx);
                newState.cGroups.add(gb.getName());
            }
            for (EventBits eb : events) {
                newState.eventMapping.add(csmData.new CEvent(eb.getEventName(), eb.getNewStateName()));
            }
        }
    }

    /**
     * Actions consist of the Action id and a parameter string (offset of).
     */
    private class ActionBits {
        int actionIx;
        int paramStringOffset;

        ActionBits(int actionIx, int stringOffset) {
            this.actionIx = actionIx;
            this.paramStringOffset = stringOffset;
        }
        String getActionName() {
            return CsmEnums.tknAction.values()[actionIx].name();
        }

        String getParamString() {
            return getString(paramStringOffset);
        }

    }

    /**
     * Event handlers in CGroups and CStates consist of an event id and a new state id.
     */
    private class EventBits {
        final int eventIx;
        final int newStateIx;

        EventBits(int eventIx, int newStateIx) {
            this.eventIx = eventIx;
            this.newStateIx = newStateIx;
        }

        /**
         * Name of the event.
         * @return the name of the event from CsmEnums.tknEvent;
         */
        String getEventName() {
            return CsmEnums.tknEvent.values()[eventIx].name();
        }

        /**
         * Name of the state that this event triggers.
         * @return the name of the new state.
         */
        String getNewStateName() {
            StateBits sb = stateBits.get(newStateIx);
            return sb.getName();
        }
    }

    /**
     * Reads the string pool from the CsmInputStream. The string pool is built for a C consumer, and so
     * consists of \0 terminated strings. This method scans to pool, accumulating characters, and saves
     * the string when the terminator is encountered.
     * @throws IOException if the underlying InputStream throws. This is unexpected, because the documented
     * behaviour is to return -1.
     */
    private void readStringPool() throws IOException {
        // Should now be positioned at the start of the string pool.
        byte[] buffer = new byte[stringPoolLength];
        cis.read(buffer);
        int nextStart = 0;
        StringBuilder nextString = new StringBuilder();
        for (int curOffset=0; curOffset<stringPoolLength; ++curOffset) {
            if (buffer[curOffset] == 0) {
                // End of string.
                stringPool.put(nextStart, nextString.toString());
                nextString.delete(0, nextString.length()); // StringBuilder.clear() anyone?
                nextStart = curOffset + 1;
            } else {
                nextString.append((char)buffer[curOffset]);
            }
        }
    }

    public String getString(int offset) {
        if (!stringPool.containsKey(offset)) return "Not known!";
        stringsUsed.add(offset);
        return stringPool.get(offset);
    }

    /**
     * A variation on DataInputStream that implements little endian uint16.
     */
    private static class CsmInputStream extends DataInputStream {
        /**
         * Creates a DataInputStream that uses the specified
         * underlying InputStream.
         *
         * @param in the specified input stream
         */
        public CsmInputStream(InputStream in) {
            super(in);
        }

        public int readU8() throws IOException {
            return super.readUnsignedByte();
        }
        public int readU16() throws IOException {
            // Little end first
            int lo = readUnsignedByte();
            int hi = readUnsignedByte();
            return hi<<8 | lo;
        }
    }
}
