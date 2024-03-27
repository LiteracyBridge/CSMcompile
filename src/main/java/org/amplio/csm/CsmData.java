package org.amplio.csm;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.repeat;


/**
 * Encapsulates the data that describes a Control State Machine (CSM) for TBv2.
 * <p>
 * The TBv2 CSM is a typical CSM; various states, each responding to various events, with actions triggered
 * by state transitions.
 * <p>
 * Because many states respond to the same events with the same transitions, the CSM supports Control Groups (CGroups)
 * which are simply lists of event:newState pairs. Each Control State (CState) has a list of zero or more Actions
 * that are performed when the CState is transitioned to, and a set of events to which it responds, with corresponding
 * new states. Some of these events may be defined by CGroups, while others are defined individually.
 * <p>
 * Actions can optionally have an argument, which is a simple string, interpreted according to the needs of the 
 * Action. There is no support for multiple arguments; if this is required it could be implemented, or could be
 * simulated by a delimited string.
 * <p>
 * For space efficiency in the binary file, rather than expanding the CGroups into every CState in which they're
 * referenced, the CStates keep separate lists of event:state pairs and relevant CGroups. This class stores CGroups
 * and event:state pairs separately as well.
 */
@SuppressWarnings("unused")
public class CsmData implements CsmItem {
    // When true, toString() is more yaml like; when false, more json like.
    public boolean asYaml = true;
    
    // Members of a CState that are lists of actions, lists of groups. all other members should
    // be event:newState.
    static final List<String> cStateNonEventKeys = Arrays.asList("Actions", "CGroups");

    // Names of all of the events. The ordinal in the array is also the event id.
    public static List<String> eventNames = Arrays.stream(CsmEnums.tknEvent.values())
        .map(Enum::name)
        .collect(Collectors.toList());
    public static int eventNameMaxLen = eventNames.stream().map(String::length).max(Integer::compareTo).orElse(12);

    // Names of all of the actions. The ordinal in the array is also the action id.
    public static List<String> actionNames = Arrays.stream(CsmEnums.tknAction.values())
        .map(Enum::name)
        .collect(Collectors.toList());
    public static int actionNameMaxLen = actionNames.stream().map(String::length).max(Integer::compareTo).orElse(12);
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    final Map<String, Object> propsIn = new HashMap<>();

    // The compiled CGroups and cStates.
    private final IndexedLinkedHashMap<String, CGroup> cGroups = new IndexedLinkedHashMap<>();
    private final IndexedLinkedHashMap<String, CState> cStates = new IndexedLinkedHashMap<>();
    private int groupNameMaxLen;
    private int stateNameMaxLen;
    
    public Map<String, CGroup> CGroups() {
        return Collections.unmodifiableMap(this.cGroups);
    }
    public Map<String, CState> CStates() {
        return Collections.unmodifiableMap(this.cStates);
    }

    // Cacheable value of data validity. Cleared when data modified.
    Boolean ok = null;

    int actionsStringLen = 60; // semi-reasonable default value.

    public CsmData() {
    }
    
    public CGroup addCGroup(String groupName) {
        CGroup newGroup = new CGroup(groupName);
        this.cGroups.put(groupName, newGroup);
        if (groupName.length() > groupNameMaxLen) groupNameMaxLen = groupName.length();
        return newGroup;
    }
    
    public CState addCState(String stateName) {
        CState newState = new CState(stateName);
        this.cStates.put(stateName, newState);
        if (stateName.length() > stateNameMaxLen) stateNameMaxLen = stateName.length();
        return newState;
    }
    
    public void putCGroup(CGroup cGroup) {
        cGroups.put(cGroup.name, cGroup);
        if (cGroup.name.length() > groupNameMaxLen) groupNameMaxLen = cGroup.name.length();
    }
    
    public void putCState(CState cState) {
        cStates.put(cState.name, cState);
        if (cState.name.length() > stateNameMaxLen) stateNameMaxLen = cState.name.length();
    }

    public void addProps(Map<String, Object> newProps) {
        propsIn.putAll(newProps);
    }

    @Override
    public String toString() {
        this.actionsStringLen = cStates.values()
                                       .stream()
                                       .map(CState::actionsString)
                                       .map(String::length)
                                       .max(Integer::compareTo)
                                       .orElse(60);
        StringBuilder result = new StringBuilder();
        if (!isValid()) {
            result.append("# CsmData contains errors. Listing may not be accurate.\n");
        }
        if (asYaml) {
            if (!propsIn.isEmpty()) {
                result.append("Props:\n");
                propsIn.forEach((k,v)-> result.append("  ").append(k).append(':').append(v).append("\n"));
            }
        } else {
            result.append("{\n");
            if (!propsIn.isEmpty()) {
                result.append("  Props: ");
                result.append(propsIn);
                result.append(",\n");
            }
        }
        result.append(asYaml ? "CGroups:\n" : "  CGroups: {\n");
        for (Map.Entry<String, CGroup> groupEntry : cGroups.entrySet()) {
            result.append(groupEntry.getValue().toString()).append(asYaml ? "\n" : ",\n");
        }
        result.append(asYaml ? "CStates:\n" : "  },\n  CStates: {\n");
        for (CState state : cStates.values()) {
            result.append(state.toString()).append(asYaml ? "\n" : ",\n");
        }
        result.append(asYaml ? "\n" : "  }\n}");
        return result.toString();
    }

    /**
     * Convenience function to emit this CsmData, via CsmWriter.
     * @param os The output stream to which to write.
     * @throws IOException if the stream can't be written.
     */
    public void emit(OutputStream os) throws IOException {
        if (!isValid()) {
            throw new IllegalStateException("CsmData must be valid.");
        }
        CsmWriter csmWriter = new CsmWriter(this, os, false);
        csmWriter.emit();
    }

    /**
     * @return true if this CsmData (CGroups and CStates) is valid.
     */
    @Override
    public boolean isValid() {
        if (ok != null) return ok;
        for (CGroup cGroup : cGroups.values()) {
            if (!cGroup.isValid()) {
                ok = false;
                return false;
            }
        }
        for (CState cState : cStates.values()) {
            if (!cState.isValid()) {
                ok = false;
                return false;
            }
        }
        ok = true;
        return true;
    }

    @Override
    public void fillErrors(List<String> errors) {
        // Examine all the groups.
        for (CGroup cGroup : cGroups.values()) {
            cGroup.fillErrors(errors);
        }
        for (CState cState : cStates.values()) {
            cState.fillErrors(errors);
        }
    }

    /**
     * Encapsulate an action and it's optional argument.
     */
    public class CAction implements CsmItem {
        public final String action;
        public final String args;

        public CAction(String action, String args) {
            ok = null;
            this.action = action;
            this.args = StringUtils.isNotBlank(args) ? args : "";
        }

        public CAction(String action) {
            this(action, "");
        }

        public int index() {
            return actionNames.indexOf(action);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(action);
            if (StringUtils.isNotEmpty(args)) {
                result.append('(')
                    .append(args)
                    .append(')');
            }
            return result.toString();
        }

        @Override
        public boolean isValid() {
            if (action.equals("enterState") &&
                !cStates.containsKey(args)) {
                return false;
            }
            if (action.equals("exitScript") &&
                !eventNames.contains(args)) {
                return false;
            }
            return CsmData.actionNames.contains(action);
        }

        @Override
        public void fillErrors(List<String> errors) {
            if (action.equals("enterState") &&
                !cStates.containsKey(args)) {
                errors.add("Not a valid state for 'enterState': " + args);
            }
            if (action.equals("exitScript") &&
                !eventNames.contains(args)) {
                errors.add("Not a valid event for 'exitScript': " + args);
            }
            if (!CsmData.actionNames.contains(action)) {
                errors.add("Unknown action '" + action + "'");
            }
        }
    }

    /**
     * Encapsulate an event and the new state to which it transitions.
     */
    public class CEvent implements CsmItem {
        public final String event;
        public final String newState;

        public CEvent(String event, String newState) {
            ok = null;
            this.event = event;
            this.newState = newState;
        }

        public int eventIndex() {
            return eventNames.indexOf(event);
        }
        public int stateIndex() {
            return cStates.getIndexOf(newState);
        }

        @Override
        public String toString() {
            if (asYaml) return event + ": " + newState;
            return event + repeat(' ', eventNameMaxLen - event.length()) + ": " + newState;
        }

        public String toString(boolean pad) {
            if (asYaml) return event + ": " + newState;
            int p = pad ? eventNameMaxLen - event.length() : 0;
            return event + repeat(' ', p) + ": " + newState;
        }
        
        @Override
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isValid() {
            return event != null && newState != null &&
                eventNames.contains(event) && cStates.containsKey(newState);
        }
        
        @Override
        public void fillErrors(List<String> errors) {
            if (event == null || newState == null) {
                errors.add("Unrecognizable event or state: '" + event + ":" + newState + "'");
            } else {
                if (!eventNames.contains(event)) errors.add("Unknown event '" + event + "'");
                if (!cStates.containsKey(newState)) errors.add("Unknown state '" + newState + "'");
            }

        }
    }

    /**
     * Encapsulate a CGroup's transitions.
     */
    public class CGroup implements CsmItem {
        public String name;
        public List<CEvent> eventMapping;

        public CGroup(String name) {
            ok = null;
            this.name = name;
            this.eventMapping = new ArrayList<>();
        }

        public CEvent addEvent(String event, String newState) {
            CEvent newEvent = new CEvent(event, newState);
            this.eventMapping.add(newEvent);
            return newEvent;
        }

        public CGroup addEvents(CEvent... events) {
            Collections.addAll(this.eventMapping, events);
            return this;
        }


        @Override
        public String toString() {
            int groupNamePad = groupNameMaxLen - name.length();
            StringBuilder result = new StringBuilder();
            if (asYaml) {
                result.append("  ").append(name).append(":\n");
                eventMapping.forEach(ev-> result.append("    ").append(ev.event).append(": ").append(ev.newState).append("\n"));
            } else {
                result.append("   ").append(name).append(':')
                    .append(repeat(' ', groupNamePad)).append('{');
                boolean first = true;
                for (CEvent mapping : eventMapping) {
                    result.append(first ? " " : "  ").append(mapping.toString()).append(",\n    ")
                        .append(repeat(' ', groupNameMaxLen));
                    first = false;
                }
                result.append("}");
            }
            return result.toString();
        }

        @Override
        public boolean isValid() {
            // Ensure that all of the event transitions in this cGroup are valid.
            for (CsmData.CEvent event : eventMapping) {
                // Is the event name valid? Does the new state exist?
                if (!event.isValid()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void fillErrors(List<String> errors) {
            // Let any cGroup with errors report them here.
            for (CsmData.CEvent event : eventMapping) {
                event.fillErrors(errors);
            }
        }
    }

    /**
     * Encapsulate a state, with entry actions and transitions out.
     */
    public class CState implements CsmItem {
        public String name;
        public List<CAction> actions;
        public List<String> groups;
        public List<CEvent> eventMapping;

        public CState(String name) {
            ok = null;
            this.name = name;
            this.actions = new ArrayList<>();
            this.groups = new ArrayList<>();
            this.eventMapping = new ArrayList<>();
        }

        public CAction addAction(String action, String args) {
            CAction newAction = new CAction(action, args);
            this.actions.add(newAction);
            return newAction;
        }

        public CAction addAction(String action) {
            return addAction(action, "");
        }

        public CState addActions(CAction... actions) {
            Collections.addAll(this.actions, actions);
            return this;
        }

        public CEvent addEvent(String event, String newState) {
            CEvent newEvent = new CEvent(event, newState);
            this.eventMapping.add(newEvent);
            return newEvent;
        }

        public CState addEvents(CEvent... events) {
            Collections.addAll(this.eventMapping, events);
            return this;
        }

        public void addGroup(String groupName) {
            groups.add(groupName);
        }

        public CState addGroups(String... groupNames) {
            Collections.addAll(this.groups, groupNames);
            return this;
        }

        public int groupIndex(String groupName) {
            return cGroups.getIndexOf(groupName);
        }

        @Override
        public boolean isValid() {
            // Ensure that all the actions are valid.
            for (CsmData.CAction action : actions) {
                if (!action.isValid()) {
                    return false;
                }
            }
            // Ensure that all the event transitions in this cState are valid.
            for (CsmData.CEvent event : eventMapping) {
                // Is the event name valid? Does the new state exist?
                if (!event.isValid()) {
                    return false;
                }
            }
            // Ensure that all the groups in this cState are valid.
            for (String groupName : groups) {
                if (!cGroups.containsKey(groupName)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void fillErrors(List<String> errors) {
            // Let any CActions with errors fill them here.
            for (CsmData.CAction action : actions) {
                action.fillErrors(errors);
            }
            // Let any CEvents with errors fill them here.
            for (CsmData.CEvent event : eventMapping) {
                // Is the event name valid? Does the new state exist?
                event.fillErrors(errors);
            }
            // For any non-existant CGroup, report that here.
            for (String groupName : groups) {
                if (!cGroups.containsKey(groupName)) {
                    errors.add("Unknown group '" + groupName + "'");
                }
            }
        }

        String actionsString() {
            if (!actions.isEmpty()) {
                StringBuilder result = new StringBuilder("Actions: [ ");
                result.append(actions.stream().map(CAction::toString).collect(Collectors.joining(", ")));
                result.append(" ]");
                return result.toString();
            }
            return "";
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (asYaml) {
                result.append("  ").append(name).append(":\n");
                if (!actions.isEmpty()) result.append("    Actions:\n");
                actions.forEach(act-> result.append("      - ").append(act).append('\n'));
                if (!groups.isEmpty()) result.append("    CGroups:\n");
                groups.forEach(cg-> result.append("      - ").append(cg).append('\n'));
                eventMapping.forEach(em-> result.append("    ").append(em).append('\n'));
            }   else {
                result.append("    ").append(name).append(':')
                    .append(repeat(' ', stateNameMaxLen - name.length())).append("{");
                if (!actions.isEmpty()) {
                    String actionsString = actionsString();
                    result.append(' ').append(actionsString).append(',')
                        .append(repeat(' ', actionsStringLen - actionsString.length()));
                } else {
                    result.append(repeat(' ', actionsStringLen + 2));
                }
                if (!groups.isEmpty()) {
                    result.append("CGroups: [ ").append(String.join(", ", groups)).append(" ], ");
                }
                if (!eventMapping.isEmpty()) {
                    for (CEvent mapping : eventMapping) {
                        result.append(mapping.toString(false)).append(", ");
                    }
                }
                result.append("}");
            }
            return result.toString();
        }
    }

    /**
     * From <a href="https://stackoverflow.com/questions/10387290/how-to-get-position-of-key-value-in-linkedhashmap-using-its-key#10387318">...</a>
     * <p>
     * A LinkedHashMap that provides easy access to the index of items.
     * @param <K> The type of the Key.
     * @param <V> The type of the Value.
     */
    public static class IndexedLinkedHashMap<K,V> extends LinkedHashMap<K,V> {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        ArrayList<K> al_Index = new ArrayList<>();

        @Override
        public V put(K key,V val) {
            if (!super.containsKey(key)) al_Index.add(key);
            return super.put(key,val);
        }

        public V getValueAtIndex(int i){
            return super.get(al_Index.get(i));
        }

        public K getKeyAtIndex(int i) {
            return al_Index.get(i);
        }

        public int getIndexOf(K key) {
            return al_Index.indexOf(key);
        }

        @Override
        public void clear() {
            super.clear();
            al_Index.clear();
        }

        @Override
        public V remove(Object key) {
            int ix = getIndexOf((K)key);
            if (ix >= 0)
                al_Index.remove(ix);
            return super.remove(key);
        }

        @Override
        public boolean remove(Object key, Object value) {
            if (super.remove(key, value)) {
                al_Index.remove(key);
                return true;
            }
            return false;
        }
    }
}
