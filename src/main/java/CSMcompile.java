import java.io.File;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.ArrayList;
//import java.util.Map;
//import java.util.HashMap;

public class CSMcompile {

  //***********************************  CLASS  ***********************************
  private static final String version =
      "3, 2021-05-03 better errors for misplaced ';' w/ parse path";
//    "2, 2021-04-30 use /build & /data";
//  "initial version comment";

  private static String parsePath = "";
  private static String initState = "";
  private static String qcState = "";
  private static int ErrorCount = 0;
  
  public static void Report( String msg ){
    System.out.println( msg + " in " + parsePath  );
    ErrorCount++;
  }
  public static boolean showPredecessors = false;
  public static boolean showSuccessors = false;
  
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
  public static String formatDateTime(FileTime fileTime) {
    LocalDateTime localDateTime = fileTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return localDateTime.format(DATE_FORMATTER);
  }
  public static void writeConfig( jsV def, PrintWriter cF ){
    jsV cfg = def.getField( "config" );
    parsePath = "config";
    if (cfg==null) 
      System.out.println( "No config:{} found." );
    else if ( !cfg.isObj() ) 
      System.out.println( "config: isn't an object" );
    else {
      //System.out.println( "config:{} has " + cfg.fieldCnt() + " entries." );
      cF.println( "TBConfig_t  TB_Config = { " );
      
      // *** FIELD ORDER MUST MATCH struct TBConfig in tbook.h
      cF.println( "  " + cfg.getIntField( "default_volume", 5 ) + ",  // default_volume " );
      cF.println( "  " + cfg.getIntField( "powerCheckMS", 60000 ) + ",  // powerCheckMS " );
      cF.println( "  " + cfg.getIntField( "shortIdleMS", 2000 ) + ",  // shortIdleMS " );
      cF.println( "  " + cfg.getIntField( "longIdleMS", 30000 ) + ",  // longIdleMS " );
      cF.println( "  " + cfg.getIntField( "minShortPressMS", 50 ) + ",  // minShortPressMS " );
      cF.println( "  " + cfg.getIntField( "minLongPressMS", 600 ) + ",  // minLongPressMS " );
      qcState = cfg.getStrField( "qcTestState", "qcTest" );
      cF.println( "  " + CsmState.asIdx( qcState ) + ", // qcTestState " );
      initState = cfg.getStrField( "initState", "init" );
      cF.println( "  " + CsmState.asIdx( initState ) + ", // initState " );
      
      cF.println( "  \"" + cfg.getStrField( "systemAudio", "system/audio" ) + "\",  // systemAudio " );
      cF.println( "  \"" + cfg.getStrField( "bgPulse",    "_49G" ) + "\",  // bgPulse " );
      cF.println( "  \"" + cfg.getStrField( "fgPlaying",   "G!" ) + "\",  // fgPlaying " );
      cF.println( "  \"" + cfg.getStrField( "fgPlayPaused", "G2_3!" ) + "\",  // fgPlayPaused " );
      cF.println( "  \"" + cfg.getStrField( "fgRecording",  "R!" ) + "\",  // fgRecording " );
      cF.println( "  \"" + cfg.getStrField( "fgRecordPaused", "R2_3!" ) + "\",  // fgRecordPaused " );
      cF.println( "  \"" + cfg.getStrField( "fgSavingRec",  "O!" ) + "\",  // fgSavingRec " );
      cF.println( "  \"" + cfg.getStrField( "fgSaveRec",   "G3_3G3" ) + "\",  // fgSaveRec " );
      cF.println( "  \"" + cfg.getStrField( "fgCancelRec",  "R3_3R3" ) + "\",  // fgCancelRec " );
      cF.println( "  \"" + cfg.getStrField( "fgUSB_MSC",   "O5o5!" ) + "\",  // fgUSB_MSC " );
      cF.println( "  \"" + cfg.getStrField( "fgTB_Error",  "R8_2R8_2R8_20!" ) + "\",  // fgTB_Error " );
      cF.println( "  \"" + cfg.getStrField( "fgNoUSBcable", "_3R3_3R3_3R3_5!" ) + "\",  // fgNoUSBcable " );
      cF.println( "  \"" + cfg.getStrField( "fgUSBconnect", "G5g5!" ) + "\",  // fgUSBconnect " );
      cF.println( "  \"" + cfg.getStrField( "fgPowerDown",  "G_3G_3G_9G_3G_9G_3" ) + "\",  // fgPowerDown " );
      
      cF.println( "}; " );
    }
  }
  private static final List<CsmState> CsmStates = new ArrayList<CsmState>();
  private static int csmStCnt = 0;
  
  public static void buildCSM( jsV def ){
    jsV cgrps = def.getField( "CGroups" );
    if (cgrps==null) 
      System.out.println( "No CGroups:{} found." );
    else if ( !cgrps.isObj() ) 
      System.out.println( "CGroups: isn't an object" );
    else {
    //  System.out.println( "CGroups:{} has " + cgrps.fieldCnt() + " fields." );
      for( String cgnm: cgrps.fields() ){
        parsePath = "CGroups." + cgnm;
        new CsmCGroup( cgnm, cgrps.getField( cgnm ));
      }
    }
    
    jsV csts = def.getField( "CStates" );
    if (csts==null) 
      System.out.println( "No CStates:{} found." );
    else if ( !csts.isObj() ) 
      System.out.println( "CStates: isn't an object" );
    else {
      //System.out.println( "CStates:{} has " + csts.fieldCnt() + " fields." );
      for( String stnm: csts.fields() ){
        parsePath = "CStates." + stnm;
        new CsmState( stnm, csts.getField( stnm ));
      }
      parsePath = "control.def";
      CsmState.verifyDefined();   // report any undefined states
    // if ( CsmState.nCSMstates != csts.fieldCnt() )
    //    CSMcompile.Report( "CStates: has " + CsmState.nCSMstates  + " fields, but " + csts.fieldCnt() + " were defined" );
      CsmState.calcReachability();
      for (int i=0; i< CsmState.nCSMstates; i++ ){
        CsmState st = CsmState.stByIdx( i );
        int nPred = st.numPredecessors();
        int nSucc = st.numSuccessors();
        if (nPred==0 && st.idx != CsmState.asIdx(initState)) 
          CSMcompile.Report( "CStates '" + st.nm  + "' is unreachable" );
        if ( showPredecessors || showSuccessors )
         System.out.println( ( showPredecessors? st.PredecessorList() + " => " : "") + i + ": " + st.nm +
                   ( showSuccessors? " => " + st.SuccessorList() : "" ));
      }
    }
  }
      
  public static void writeSysAudio( PrintWriter cF ){
    cF.println( "int   nPlaySys = " + CsmAction.SysAudio.size() + ";  // # PlaySys prompts used by CSM " );
    cF.println( "SysAudio_t SysAudio[] = { " );
    for( String s: CsmAction.SysAudio )
      cF.println( "{ \"" + s + "\",  \"M0:/system/audio/" + s + ".wav\" }, " );           
    cF.println( "};  // SysAudio " );
  }
  public static void writeStates( PrintWriter cF ){
    cF.println( "int   nCSMstates = " + CsmState.nCSMstates + ";  // TBook state machine definition " );
    for (int i=0; i< CsmState.nCSMstates; i++ ){
      CsmState st = CsmState.stByIdx( i );
      if ( st==null ) 
        CSMcompile.Report( "CState " + i + " is not defined." );
      else {
        cF.println( "csmState " + st.nm + " =  // TBookCMS[" + i + "] " );
        cF.println( "  {  " + st.idx + ", \"" + st.nm + "\", " );
        cF.println( "//     N   H   C   P   M   T   L   R   p   S   t   H_  C_  P_  M_  T_  L_  R_  p_  S_  t_  sH  sC  sP  sM  sT  sL  sR  sp  sS  st  Ad  As  sI  lI  lB  cB  CB  FU  T   cF  lH  mH" );
        String nxtIdxStr = "";
        for (int iEvt=0; iEvt < CsmToken.nEvents(); iEvt++ ){
          String enm = CsmToken.eventName( iEvt );
          nxtIdxStr += String.format( "%2d", st.nxtStIdx( enm ))+ ", ";
        }
        cF.println( "     { " + nxtIdxStr + "}," );
        
        int nA = st.actions.size();
        if (nA==0 ) 
          cF.println( "    0" );
        else {
          cF.println( "     " + nA + ", {  " );
          for (int iA=0; iA<nA; iA++){
            CsmAction a = st.actions.get(iA);
            cF.println( "          " + a );
          }
          cF.println( "        }" );
        }
        cF.println( "  }; " );
      }
    }
  }
  public static void writeTBookCSM( PrintWriter cF ){
    cF.println( "csmState * TBookCSM[] = { " );
    for (int i=0; i< CsmState.nCSMstates; i++ ){
      CsmState st = CsmState.stByIdx( i );
      if ( st==null ) 
        CSMcompile.Report( "CState " + i + " is not defined." );
      else
        cF.println( "  &" + st.nm + ",  // TBookCMS[" + i + "] " );
    }
    cF.println( "};  // end of tbook_csm_def.h" );
  }
  public static void main(String[] args)  throws Exception {
    System.out.println( "CSMcompile version: " + version );
    String in_path = "control.def";
    String out_path = "tbook_csm_def.h";
    int pos = 0;
    for ( String s: args ) {
      if (s.startsWith("-")) {  // command line switches
        switch (s) {
          case "-showPred":
            showPredecessors = true;
            break;
          case "-showSucc":
            showSuccessors = true;
            break;
        }
      } else { // positional
        if (pos == 0) in_path = s;  // 1st positional
        if (pos == 1) out_path = s;
        pos++;
      }
    }

    File cFile = new File( out_path );
    PrintWriter cF = new PrintWriter( cFile );
    
    Path file = Paths.get( in_path );
    BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
    System.out.println( "processing " + file.toAbsolutePath() + " of: " + formatDateTime( attr.lastModifiedTime() ));

    cF.println( "// TBook Control State Machine   tbook_csm_def.h" );
    cF.println( "// generated by CSMcompile version: " + version );
    cF.println( "// run at " + LocalDateTime.now().format(DATE_FORMATTER) );
    cF.println( "// from " + file.toAbsolutePath() + " of: " + formatDateTime( attr.lastModifiedTime()) );
    
    JSONish jsh = new JSONish( file.toAbsolutePath().toString() );
    System.out.println( "  version: " + jsh.FirstLine );
    jsV def = jsh.result;

    System.out.println( "to " + out_path + (showPredecessors? " (showing predecessor states) ":" (no -showPred)" ) +
            (showSuccessors? " (showing successor states) ":" (no -showSucc)" ) );

    cF.println( "#include \"controlmanager.h\" " );
    cF.println( "char CSM_Version[] = \"PRE: " + jsh.FirstLine + "\"; " );
    
    writeConfig( def, cF );  // initState will be state 0

    buildCSM( def );
    
    writeSysAudio( cF );
    writeStates( cF );
    writeTBookCSM( cF );

    cF.close();

    if ( ErrorCount > 0 ){
      System.out.println( "CSMcompile reported " + ErrorCount + " problems, output may be incorrect." );
    //  System.exit( ErrorCount );
    }
    System.out.println( "CSMcompile wrote tbook_csm_def.h" );
  }
}
