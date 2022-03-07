package org.amplio.csm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CsmCGroup {
  //***********************************  CLASS  ***********************************
  private static Map<String, CsmCGroup> CGrpMap = new HashMap<String, CsmCGroup>();
  
  //***********************************  INSTANCE  ***********************************
  public  String  nm;
  private Map<String,String> nxtState = new HashMap<String,String>();
  
  public CsmCGroup( String name, jsV entries ) {  // new CGroup definition from control.def
    nm = name;
    CGrpMap.put( nm, this );     // not a state's nxtMap, so add to global list of CGroups
    if ( !entries.isObj() ) {
      CSMcompile.Report("value for CGroups." + name + " is not an object!");
    } else {
      for (String evt : entries.fields())
        addTransition(evt, entries.getField(evt).asStr());
    }
  }
  public CsmCGroup( String name ) {  // nxtState rep for a org.amplio.CsmState
    nm = name;
  }
  public Set<String> Events(){
    return nxtState.keySet();
  }
  public String getNxtSt( String evt ){
    return nxtState.get( evt );
  }
  public void addTransition( String evt, String stnm ){
    if ( CsmToken.toEvent( evt )==CsmToken.tknEvent.eNull )
      CSMcompile.Report( "field name '" + evt + "' is not an Event name" );
    int idx = CsmState.asIdx( stnm );  // assign state index
    if ( evt.equals("anyKey") ) {
      for (  CsmToken.tknEvent e: CsmToken.tknKeys )  // add Home..starTable unless already defined
        if ( !nxtState.containsKey(e.name()) )
          nxtState.put( e.name(), stnm );
    } else
      nxtState.put( evt, stnm );
  }
  public void addGrpTransitions( String cgname ){
    CsmCGroup grp = CGrpMap.get( cgname );
    if ( grp!=null ){
      for( String evt: grp.Events() )
        addTransition( evt, grp.getNxtSt( evt ));
      
    } else
      CSMcompile.Report( "'" + cgname + "' isn't defined as a CGroup");
  }
}
