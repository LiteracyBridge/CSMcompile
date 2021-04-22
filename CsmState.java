// CsmState
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class CsmState {
  //***********************************  CLASS  ***********************************
  private static Map<String,Integer> stNmToIndex = new HashMap<String,Integer>();   // stateName => state #
  private static Map<Integer,CsmState> statesByIndex = new HashMap<Integer,CsmState>();
  
  public static int nCSMstates = 0;
  public static void verifyDefined(){
    // report any names added to stNmToIndex that weren't later defined
    for( String nm: stNmToIndex.keySet() ){
      Integer idx = stNmToIndex.get( nm );
      if ( !statesByIndex.containsKey( idx )){
        CSMcompile.Report("state '" + nm + "' was referenced, but never defined" );
        new CsmState( nm, new jsV(true) );
      }
    }
  }
  public static void calcReachability(){
    for (int i=0; i<nCSMstates; i++){   // find possible nextStates for all states
      CsmState st = statesByIndex.get( i );
      st.calcSuccessors();
    }
    for (int i=0; i<nCSMstates; i++){   // then use that to find possible predecessors for all states
      CsmState st = statesByIndex.get( i );
      st.calcPredecessors();
    }
  }
  
  public static int asIdx( String nm ){
    Integer idx = stNmToIndex.get( nm );
    if (idx != null) return idx;
    
    int stnum = nCSMstates;
    stNmToIndex.put( nm, stnum );
    nCSMstates++;
    return stnum;
  }
  public static CsmState stByIdx( int idx ){
    if ( statesByIndex.containsKey( idx ))
      return statesByIndex.get( idx );
    else {
      CSMcompile.Report( "no CsmState[" + idx + "]" ); 
      return null;
    }
  }
  
  //***********************************  INSTANCE  ***********************************
  
  public String nm;
  public int idx;
  private CsmCGroup nxtState = null; 
  public List<CsmAction> actions = new ArrayList<CsmAction>();
  
  private Set<CsmState> comesFrom = new HashSet<CsmState>();    // states with transitions to this one
  private Set<CsmState> goesTo = new HashSet<CsmState>();
  
  
  public CsmState( String name, jsV stdef ) { 
    nm = name;
    idx = asIdx( nm );
    statesByIndex.put( idx, this );   // add to possibly sparse list
    
    nxtState = new CsmCGroup( nm );
    
    jsV acts = stdef.getField( "Actions" );
    if ( acts!=null ){ 
      for( int i=0; i< acts.listCnt(); i++ ){
        jsV op = acts.listEl( i );
        actions.add( new CsmAction( op.asStr(), op.asArg() ));
      }
    }
    
    jsV grps = stdef.getField( "CGroups" );
    if ( grps != null ){
      for( int i=0; i< grps.listCnt(); i++ ) // add all the transitions for each CGroup
        nxtState.addGrpTransitions( grps.listEl(i).asStr() );
      
      for( String fnm: stdef.fields() ){
        if ( !fnm.equals("Actions") && !fnm.equals("CGroups") ){  // evt: nxtSt
          nxtState.addTransition( fnm, stdef.getField( fnm ).asStr() );
        }
      }
    }
  }

  private void calcSuccessors(){
    for (int iEvt=0; iEvt < CsmToken.nEvents(); iEvt++ ){
       int nxtStIdx = nxtStIdx( CsmToken.eventName( iEvt ));
       CsmState dst = statesByIndex.get( nxtStIdx );
       goesTo.add( dst );   // add to set of destinations
    }
  }
  public boolean canGoTo( CsmState st ){
    return goesTo.contains( st );
  }
  public List<Integer> SuccessorList(){
    List<Integer> suc = new ArrayList<Integer>();
    for ( CsmState s: goesTo )
      suc.add( s.idx );
    return suc;
  }
  public int numSuccessors( ){
    return goesTo.size();
  }
  
  private void calcPredecessors(){
    for (int i=0; i<nCSMstates; i++){
      CsmState st = statesByIndex.get( i );
      if ( st.canGoTo( this ))
        comesFrom.add( st );
    }
  }
  public List<Integer> PredecessorList(){
    List<Integer> pred = new ArrayList<Integer>();
    for ( CsmState s: comesFrom )
      pred.add( s.idx );
    return pred;
  }
  public int numPredecessors( ){
    return comesFrom.size();
  }
  
  public int nxtStIdx( String evt ){
    String nxt = nxtState.getNxtSt( evt );
    if ( nxt==null ){  // next state not explicitly defined
      nxt = nxtState.getNxtSt( "anyKey" );  // use anyKey if defined, otherwise self
      return nxt==null? idx : asIdx( nxt );
    }
    return asIdx( nxt );
  }
  public String toString(){
    return nm + idx;
  }
}
