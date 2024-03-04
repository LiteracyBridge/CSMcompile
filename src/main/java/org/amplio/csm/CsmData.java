package org.amplio.csm;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.repeat;


@SuppressWarnings("unused")
public class CsmData implements CsmItem {
    public boolean asYaml = true;
    
    // Members of a CState that are lists of actions, lists of groups. all other members should
    // be event:newState.
    static final List<String> stateKeys = Arrays.asList("Actions", "CGroups");

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

    final Map<String, Map<String, String>> CGroupsIn;
    final Map<String, Map<String, Object>> CStatesIn;
    final Map<String, Object> propsIn = new HashMap<String,Object>();

    List<String> groupNames = new ArrayList<>();
    private int groupNameMaxLen;
    List<String> stateNames = new ArrayList<>();
    private int stateNameMaxLen;

    // The compiled CGroups and CStates.
    final LinkedHashMap<String, CGroup> CGroups = new LinkedHashMap<>();
    final LinkedHashMap<String, CState> CStates = new LinkedHashMap<>();


    Boolean ok = null;

    int actionsStringLen = 60; // semi-reasonable default value.

    public CsmData(Map csmdef) {
        this.CGroupsIn = (Map<String, Map<String, String>>) csmdef.get("CGroups");
        this.CStatesIn = (Map<String, Map<String, Object>>) csmdef.get("CStates");
        this.propsIn.putAll((Map<String,Object>) csmdef.getOrDefault("Props", new HashMap<String,Object>()));

        groupNames.addAll(this.CGroupsIn.keySet());
        this.groupNameMaxLen = groupNames.stream().map(String::length).max(Integer::compareTo).orElse(12);
        stateNames.addAll(this.CStatesIn.keySet());
        this.stateNameMaxLen = stateNames.stream().map(String::length).max(Integer::compareTo).orElse(12);
    }

    public CsmData() {
        CGroupsIn = null;
        CStatesIn = null;
    }
    
    public CGroup addCGroup(String groupName) {
        CGroup newGroup = new CGroup(groupName);
        this.CGroups.put(groupName, newGroup);
        groupNames.add(groupName);
        if (groupName.length() > groupNameMaxLen) groupNameMaxLen = groupName.length();
        return newGroup;
    }
    
    public CState addState(String stateName) {
        CState newState = new CState(stateName);
        this.CStates.put(stateName, newState);
        stateNames.add(stateName);
        if (stateName.length() > stateNameMaxLen) stateNameMaxLen = stateName.length();
        return newState;
    }
    
    public String toString() {
        this.actionsStringLen = CStates.values()
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
                propsIn.forEach((k,v)->{result.append("  ").append(k).append(':').append(v).append("\n");});
            }
        } else {
            result.append("{\n");
            if (!propsIn.isEmpty()) {
                result.append("  Props: ");
                result.append(propsIn.toString());
                result.append(",\n");
            }
        }
        result.append(asYaml ? "CGroups:\n" : "  CGroups: {\n");
        for (Map.Entry<String, CGroup> groupEntry : CGroups.entrySet()) {
            result.append(groupEntry.getValue().toString()).append(asYaml ? "\n" : ",\n");
        }
        result.append(asYaml ? "CStates:\n" : "  CStates: {\n");
        for (CState state : CStates.values()) {
            result.append(state.toString()).append(asYaml ? "\n" : ",\n");
        }
        result.append(asYaml ? "\n" : "  }\n}");
        return result.toString();
    }

    @Override
    public boolean isValid() {
        if (ok != null) return ok;
        for (CGroup cGroup : CGroups.values()) {
            if (!cGroup.isValid()) {
                ok = false;
                return false;
            }
        }
        for (CState cState : CStates.values()) {
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
        errors.clear();
        // Examine all the groups.
        for (CGroup cGroup : CGroups.values()) {
            cGroup.fillErrors(errors);
        }
        for (CState cState : CStates.values()) {
            cState.fillErrors(errors);
        }
    }

    /**
     * Encapsulate an action and it's argument.
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

        public String toString() {
            StringBuilder result = new StringBuilder(action);
            if (StringUtils.isNotEmpty(args)) {
                result.append('(')
                    .append(args)
                    .append(')');
            }
            return result.toString();
        }

        public boolean isValid() {
            if (action.equals("enterState") &&
                !CStates.containsKey(args)) {
                return false;
            }
            if (!CsmData.actionNames.contains(action)) {
                return false;
            }
            return true;
        }

        public void fillErrors(List<String> errors) {
            if (action.equals("enterState") &&
                !CStates.containsKey(args)) {
                errors.add("Not a valid state for 'enterState: " + args);
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
            return stateNames.indexOf(newState);
        }

        public String toString() {
            if (asYaml) return event + ": " + newState;
            return event + repeat(' ', eventNameMaxLen - event.length()) + ": " + newState;
        }

        public String toString(boolean pad) {
            if (asYaml) return event + ": " + newState;
            int p = pad ? eventNameMaxLen - event.length() : 0;
            return event + repeat(' ', p) + ": " + newState;
        }
        
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isValid() {
            return event != null && newState != null &&
                eventNames.contains(event) && stateNames.contains(newState);
        }
        
        public void fillErrors(List<String> errors) {
            if (event == null || newState == null) {
                errors.add("Unrecognizable event or state: '" + event + ":" + newState + "'");
            } else {
                if (!eventNames.contains(event)) errors.add("Unknown event '" + event + "'");
                if (!stateNames.contains(newState)) errors.add("Unknown state '" + newState + "'");
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

        public int emitSize() {
            // cgroup 0:
            //   offset of name
            //   num_event/new_state pairs
            //   event 0/new state 0
            //   ...
            return 2 + 2 + 2 * eventMapping.size();
        }

        public String toString() {
            int groupNamePad = groupNameMaxLen - name.length();
            StringBuilder result = new StringBuilder();
            if (asYaml) {
                result.append("  ").append(name).append(":\n");
                eventMapping.forEach(ev->{result.append("    ").append(ev.event).append(": ").append(ev.newState).append("\n");});
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
        public List<String> cGroups;
        public List<CEvent> eventMapping;

        public CState(String name) {
            ok = null;
            this.name = name;
            this.actions = new ArrayList<>();
            this.cGroups = new ArrayList<>();
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
            cGroups.add(groupName);
        }

        public CState addGroups(String... groupNames) {
            Collections.addAll(this.cGroups, groupNames);
            return this;
        }

        public int groupIndex(String groupName) {
            return groupNames.indexOf(groupName);
        }

        public int emitSize() {
            // cstate 0:
            //   offset of name
            //   num_event/new_state pairs | num_actions << 8
            //   action 0
            //   offset of arg 0
            //   ...
            //   action n
            //   offset of arg n
            //   event 0/new_state 0
            //   ...
            //   event j/new_state j
            //   ff / cgroup 0    last cgroup specified
            //   ...
            //   ff / cgroup n    first cgroup specified
            // ...
            return 2 + 2 + 4 * actions.size() + 2 * cGroups.size() + 2 * eventMapping.size();
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
            for (String groupName : cGroups) {
                if (!groupNames.contains(groupName)) {
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
            for (String groupName : cGroups) {
                if (!groupNames.contains(groupName)) {
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

        public String toString() {
            StringBuilder result = new StringBuilder();
            if (asYaml) {
                result.append("  ").append(name).append(":\n");
                if (!actions.isEmpty()) result.append("    Actions:\n");
                actions.forEach(act->{result.append("      - ").append(act).append('\n');});
                if (!cGroups.isEmpty()) result.append("    CGroups:\n");
                cGroups.forEach(cg->{result.append("      - ").append(cg).append('\n');});
                eventMapping.forEach(em->{result.append("    ").append(em).append('\n');});
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
                if (!cGroups.isEmpty()) {
                    result.append("CGroups: [ ").append(String.join(", ", cGroups)).append(" ], ");
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
}
