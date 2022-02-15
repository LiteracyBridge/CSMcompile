package org.amplio.csm;// jsV -- jsonish value element

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class jsV {
  
  //***********************************  INSTANCE  ***********************************
  private enum vType{ vStr, vObj, vList, vOpArg };
  private vType typ;
  private CsmToken tknVal = null;    // value if typ==vStr
  private String arg = null;
  private Map<String, jsV> nmValMap;   // value if typ==vObj
  private List<jsV> valList;        // value if typ==vList
  
  
  public jsV( boolean asObject ) {  // new empty vObj or vList jsV 
    if ( asObject ){
      typ = vType.vObj;
      nmValMap = new LinkedHashMap<String,jsV>();
      
    } else {
      typ = vType.vList;
      valList = new ArrayList<jsV>();
    }
  }
  public jsV( CsmToken tkn ) {    // new jsV is vStr
    typ = vType.vStr;
    tknVal = tkn;
  }
  public void objAdd( CsmToken nm, jsV val ){  // add nm:val to vObj jsV
    if ( CsmToken.invalidName( nm ))
      CSMcompile.Report( "invalid field name '"+nm+"'" );
    if (typ!=vType.vObj) System.out.println( "objAdd not vObj" );
    nmValMap.put( nm.text(), val );
  }
  public void lstAdd( jsV val ){  // append val to vList jsV 
    if (typ!=vType.vList) System.out.println( "lstAdd not vList" );
    valList.add( val );
  }
  public void argAdd( String a ){
    if (typ!=vType.vStr) System.out.println( "argAdd not vStr" );
    typ = vType.vOpArg;
    arg = a;
  }
  public boolean isStr(){
    return typ==vType.vStr;
  }
  public String asArg(){
    if (typ==vType.vOpArg)
      return arg;
    else
      return "";
  }
  public boolean isObj(){
    return typ==vType.vObj;
  }
  public boolean isList(){
    return typ==vType.vList;
  }
  public int listCnt(){
    if ( isList()) 
      return valList.size();
    else
      return 0;
  }
  public int fieldCnt(){
    if ( isObj()) 
      return nmValMap.keySet().size();
    else
      return 0;
  }
  public Set<String> fields(){
    if ( isObj()) 
      return nmValMap.keySet();
    else
      return null;
  }
  
  public String asStr(){
    if ( typ==vType.vStr || typ==vType.vOpArg )
      return tknVal.text();
    else
      return null;
  }
  public jsV listEl( int idx ){
    if ( typ==vType.vList && valList.size()>=idx )
      return valList.get( idx );
    else
      return null;
  }
  public jsV getField( String nm ){
    if ( typ==vType.vObj && nmValMap.containsKey( nm ) )
      return nmValMap.get( nm );
    else
      return null;    
  }
  public String getStrField( String nm, String defval ){
    jsV v = getField( nm );
    if ( v==null ) return defval;
    return v.asStr();
  }
  public int getIntField( String nm, int defval ){
    jsV v = getField( nm );
    if ( v==null ) return defval;
    return Integer.parseInt( v.asStr() );
  }
  public String toString(){
    if (typ==vType.vStr) 
      return tknVal.text();
    if (typ==vType.vOpArg) 
      return tknVal.text() + "( " + arg + " )";
    if (typ==vType.vObj){
      String s = " { \n";
      for( String nm: nmValMap.keySet() )
        s += "  " + nm + ": " + nmValMap.get(nm) + ", ";
      //nmValMap.forEach((n,v) -> s = s + (n + ": " + v) ); 
      return s + " }";
    }
    if (typ==vType.vList){
      String s = " [ \n";
      for ( jsV v: valList )
        s += v + ", ";
      return s + " ]"; 
    }
    return "js_badTyp";
  }
}
