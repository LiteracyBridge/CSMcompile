package org.amplio.csm;

import org.amplio.csm.CsmData.CGroup;
import org.amplio.csm.CsmData.CState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.amplio.csm.CsmData.eventNames;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class Compiler {
    // Match "name" or "name(arg)"
    static final Pattern ACTION_ARG = Pattern.compile("(\\w*)(?:\\((.*)\\))?");
    private final CsmData csmData;

    private boolean ok = true;
 
    Compiler(CsmData csmData) {
        this.csmData = csmData;
    }

    boolean go() {
        ok = validateCGroups() && ok;
        ok = validateCStates() && ok;
        csmData.ok = ok;
        return ok;
    }

    List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (!csmData.isValid())
            csmData.fillErrors(errors);
        return errors.isEmpty() ? null : errors;
    }

    boolean validateCStates() {
        boolean stateOk = true;
        for (Map.Entry<String, Map<String, Object>> stateEntry : csmData.CStatesIn.entrySet()) {
            String stateName = stateEntry.getKey();
            Map<String, Object> state = stateEntry.getValue();
            CState newState = validateCState(stateName, state);
            stateOk = stateOk && newState != null;
            if (stateOk) {
                csmData.CStates.put(stateName, newState);
            }
        }
        return stateOk;
    }

    CState validateCState(String stateName, Map<String, Object> state) {
        CState result = csmData.new CState(stateName);
        List<String> errors = new ArrayList<>();
        List<String> actions = (List<String>) state.getOrDefault("Actions", new ArrayList());
        List<String> cGroups = (List<String>) state.getOrDefault("CGroups", new ArrayList());
        List<String> events = state.keySet()
            .stream()
            .filter(n -> !CsmData.stateKeys.contains(n))
            .collect(Collectors.toList());

        // Validate and accumulate actions.
        for (String action : actions) {
            Matcher m = ACTION_ARG.matcher(action);
            if (m.matches()) {
                String actionName = m.group(1);
                String actionArg = m.groupCount() > 1 ? m.group(2) : "";
                if (CsmData.actionNames.contains(actionName)) {
                    if (actionName.equals("enterState")) {
                        if (!csmData.CStatesIn.containsKey(actionArg)) {
                            errors.add("Not a valid state for 'enterState: " + actionArg);
                            continue;
                        }
                    }
                    result.actions.add(csmData.new CAction(actionName, actionArg));
                } else {
                    errors.add("Unknown action '" + action + "'");
                }
            } else {
                errors.add("Misformed action '" + action + "'");
            }
        }
        // Validate and accumulate CGroup (names).
        for (String groupName : cGroups) {
            if (csmData.CGroupsIn.containsKey(groupName)) {
                result.cGroups.add(groupName);
            } else {
                errors.add("Unknown group '" + groupName + "'");
            }
        }
        // Validate and accumulate event:state pairs.
        for (String event : events) {
            String newStateName = (String) state.get(event);
            if (event == null || newStateName == null | !eventNames.contains(event)) {
                errors.add("Unrecognizable event or state: '" + event + ":" + newStateName + "'");
            } else {
                if (!events.contains(event)) errors.add("Unknown event '" + event + "'");
                if (!csmData.stateNames.contains(newStateName)) errors.add("Unknown state '" + newStateName + "'");
                result.eventMapping.add(csmData.new CEvent(event, newStateName));
            }
        }
        if (!errors.isEmpty()) {
            System.out.printf("Error(s) parsing CState %s: \n    %s\n", stateName, String.join("\n    ", errors));
            return null;
        }
        return result;
    }

    boolean validateCGroups() {
        boolean groupOk = true;
        for (Map.Entry<String, Map<String, String>> groupEntry : csmData.CGroupsIn.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, String> group = groupEntry.getValue();
            CGroup newGroup = validateCGroup(groupName, group);
            groupOk = groupOk && newGroup != null;
            if (groupOk) {
                csmData.CGroups.put(groupName, newGroup);
            }
        }
        return groupOk;
    }

    CGroup validateCGroup(String groupName, Map<String, String> group) {
        CGroup result = csmData.new CGroup(groupName);
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> eventMapping : group.entrySet()) {
            String eventName = eventMapping.getKey();
            String newStateName = eventMapping.getValue();
            if (eventName == null || newStateName == null) {
                errors.add("Unrecognizable event or state: '" + eventName + ":" + newStateName + "'");
            } else {
                if (!eventNames.contains(eventName)) errors.add("Unknown event '" + eventName + "'");
                if (!csmData.stateNames.contains(newStateName)) errors.add("Unknown state '" + newStateName + "'");
            }
            if (errors.isEmpty()) {
                result.eventMapping.add(csmData.new CEvent(eventName, newStateName));
            }
        }
        if (!errors.isEmpty()) {
            System.out.printf("Error(s) parsing CGroup %s: \n    %s\n", groupName, String.join("\n    ", errors));
            return null;
        }
        return result;
    }


}
