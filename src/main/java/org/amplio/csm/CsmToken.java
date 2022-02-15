package org.amplio.csm;// org.amplio.CsmToken

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsmToken {
  //***********************************  CLASS  ***********************************

  public enum tknGroup {  // tknGroup -- types of TBook tokens 
    gNull, gGroup, gPunct, gEvent, gAction, gToken, gList, gObject 
  };

  private static final String  punctChars = "_,;:{}[]()\"";
  public enum tknPunct {  // tknPunct -- org.amplio.JSONish punctuation tokens  -- tknGroup==gPunct
    pNull, Comma, Semi, Colon, LBrace, RBrace, LBracket, RBracket, LParen, RParen, DQuote 
  };
  // MUST MATCH:  ENms[] defined in tknTable.c
  public enum tknEvent {  // tknEvent -- TBook event types  -- tknGroup==gEvent
    eNull, 
    Home,     Circle,     Plus,      Minus,      Tree,      Lhand,      Rhand,      Pot,     Star,     Table,     //=10
    Home__,   Circle__,   Plus__,    Minus__,    Tree__,    Lhand__,    Rhand__,    Pot__,   Star__,   Table__,   //=20
    starHome, starCircle, starPlus,  starMinus,  starTree,  starLhand,  starRhand,  starPot, starStar, starTable, //=30
    AudioStart,           AudioDone,	         ShortIdle,	            LongIdle,	         LowBattery,          //=35
    BattCharging,         BattCharged,	         FirmwareUpdate,        Timer,               ChargeFault,         //=40
    LithiumHot,           MpuHot,                FilesSuccess,          FilesFail,           anyKey,              //=45
    eUNDEF //=46
    };
  // MUST MATCH:  ANms[] defined in tknTable.c
  public enum tknAction {  // tknAction -- TBook actions  -- tknGroup==gAction
    aNull,
    LED,        bgLED,     playSys,
    playSubj,   pausePlay, resumePlay,
    stopPlay,   volAdj,    spdAdj,
    posAdj,     startRec,  pauseRec,
    resumeRec,  finishRec, playRec,
    saveRec,    writeMsg,  goPrevSt,
    saveSt,     goSavedSt, subjAdj,
    msgAdj,     setTimer,  resetTimer,
    showCharge, startUSB,  endUSB,
    powerDown,  sysBoot,   sysTest,
    playNxtPkg, changePkg, playTune,
    filesTest
  };

  public static EnumSet<tknEvent> tknKeys = EnumSet.range( tknEvent.Home, tknEvent.starTable );

  private static Map< String, tknGroup > predefTokens = new HashMap< String, tknGroup >();    // map known token strings to enum tknGroup
  
  private static Map< String, tknPunct > punctTokens = new HashMap< String, tknPunct >();     // map punct 1 char strings to enum tknPunct
  private static Map< String, tknEvent > eventTokens = new HashMap< String, tknEvent >();     // map event strings to enum tknEvent
  private static Map< String, tknAction > actionTokens = new HashMap< String, tknAction >();   // map action strings to enum tknAction

  private static List<String> eventNames = new ArrayList< String >();     // event strings in tknEvent order

  static {
    // build map of all the predefined tokens => tknGroup
    for ( tknGroup g: tknGroup.values() )
      predefTokens.put( g.name(), tknGroup.gGroup );
    for ( char c: punctChars.toCharArray() )
      predefTokens.put( Character.toString(c), tknGroup.gPunct );
    
    punctTokens.put( ",", tknPunct.Comma );
    punctTokens.put( ";", tknPunct.Semi );
    punctTokens.put( ":", tknPunct.Colon );
    punctTokens.put( "{", tknPunct.LBrace );
    punctTokens.put( "}", tknPunct.RBrace );
    punctTokens.put( "[", tknPunct.LBracket );
    punctTokens.put( "]", tknPunct.RBracket );
    punctTokens.put( "(", tknPunct.LParen );
    punctTokens.put( ")", tknPunct.RParen );
    punctTokens.put( "\"", tknPunct.DQuote );
    
    int i = 0;
    for ( tknEvent e: tknEvent.values() ){
      predefTokens.put( e.name(), tknGroup.gEvent );
      eventTokens.put( e.name(), e );
      eventNames.add( e.name() );
    }
    
    for ( tknAction a: tknAction.values() ){
      predefTokens.put( a.name(), tknGroup.gAction );
      actionTokens.put( a.name(), a );
    }
  }
  public static tknPunct asPunct( CsmToken tkn ){    // => tknPunct or pNull if group!=gPunct
    if ( tkn.group!=tknGroup.gPunct ) 
      return tknPunct.pNull;
    return punctTokens.get( tkn.text );
  }
  public static boolean invalidName( CsmToken tkn ){    // => tknPunct or pNull if group!=gPunct
    switch ( tkn.group ){
      case gEvent:
      case gAction:
      case gToken:   return false;

      default:      // tokens that can't be field names
      case gList:
      case gObject:
      case gNull:
      case gGroup:
      case gPunct:   return true;
    }
  }

  public static tknEvent asEvent( CsmToken tkn ){    // => tknEvent or eNull if group!=gEvent
    if ( tkn.group!=tknGroup.gEvent ) return tknEvent.eNull;
    return eventTokens.get( tkn.text );
  }
  public static String eventName( int idx ){
    return eventNames.get( idx );
  }
  public static int nEvents(){
    return eventNames.size()-2;  // anyKey & eUNDEF aren't tokens
  }
  public static tknEvent toEvent( String ename ){
    return eventTokens.containsKey( ename )? eventTokens.get( ename ) : tknEvent.eNull;
  }


  public static tknAction asAction( CsmToken tkn ){    // => tknAction or aNull if group!=gAction
    if ( tkn.group!=tknGroup.gAction ) return tknAction.aNull;
    return actionTokens.get( tkn.text );
  }
  public static boolean isAction( String tkn ){    // => tknAction or aNull if group!=gAction
    return actionTokens.containsKey( tkn );
  }
  public static tknAction toAction( String aname ){
    return actionTokens.containsKey( aname )? actionTokens.get( aname ) : tknAction.aNull;
  }
  
  //***********************************  INSTANCE  ***********************************
  // instance data & methods
  
  private String text;
  private tknGroup group;
  
  public CsmToken( String s ) { 
    text = s;  
    group = predefTokens.get( text );
    if (group==null) group = tknGroup.gToken;

   // System.out.print( "'" + text + "' => " + group );
  }
  public CsmToken( String s, tknGroup grp ){
    text = s;
    group = grp;
  }
  public String text(){ 
    return text;
  }
  public String toString() {
    switch ( group ){
      case gNull:    return "<gNull>";
      case gGroup:   return " g[" + text + "]";
      case gPunct:   return " " + text;
      case gEvent:   return " e[" + text + "]";
      case gAction:  return " a[" + text + "]";
      case gToken:   return " <" + text + ">";
      case gList:    return " L[" + text + "]";
      case gObject:  return " O[" + text + "]";
      default:       return " ?[" + text + "]";
    }
  }
}
