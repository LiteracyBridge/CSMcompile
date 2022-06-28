package org.amplio.csm;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CSMcompile {

  //***********************************  CLASS  ***********************************
  private static final String version =
      "9(2022-5-16)"; // quoted string tokens-- subString fix, nxtTkn fix
//    "8(2021-10-15)"; // write preloadCSM.c instead of csm_def.h
//    "7(2021-10-7)";  // also output csm_data.txt
//    "6, 2021-05-20 add filesTest action & events";
//    "5, 2021-05-19 exit immediately on premature EOF";
//    "4, 2021-05-07 proper handling of anyKey";
//    "3, 2021-05-03 better errors for misplaced ';' w/ parse path";
//    "2, 2021-04-30 use /build & /data";
//  "initial version comment";

  private static String parsePath = "";
  private static String initState = "";
  private static String qcState = "";
  private static int ErrorCount = 0;
  
  public static void Report( String msg ){
    System.out.println( msg + " in " + parsePath  );
    ErrorCount++;
    if ( msg.contains("!!")){
      System.out.println(" unrecoverable error, exiting...");
      System.exit(0);
    }
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
  public static void writeConfigToPreloadC(jsV def, PrintWriter cF ){
    jsV cfg = def.getField( "config" );
    parsePath = "config";
    if (cfg==null) 
      System.out.println( "No config:{} found." );
    else if ( !cfg.isObj() ) 
      System.out.println( "config: isn't an object" );
    else {
      //System.out.println( "config:{} has " + cfg.fieldCnt() + " entries." );
      cF.println( "static TBConfig_t  preTB_Config = { " );
      
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
      
      cF.println( "};  // preTB_Config" );
      cF.println(" ");
    }
  }
  public static void writeConfigToCsmData(jsV def, PrintWriter cF ) {
    jsV cfg = def.getField( "config" );
    parsePath = "dataConfig";
    if (cfg==null)
      System.out.println( "No config:{} found." );
    else if ( !cfg.isObj() )
      System.out.println( "config: isn't an object" );
    else {
      // *** FIELD ORDER MUST MATCH struct TBConfig in tbook.h
      cF.println( cfg.getIntField("default_volume", 5));
      cF.println( cfg.getIntField("powerCheckMS", 60000));
      cF.println( cfg.getIntField("shortIdleMS", 2000));
      cF.println( cfg.getIntField("longIdleMS", 30000));
      cF.println( cfg.getIntField("minShortPressMS", 50));
      cF.println( cfg.getIntField("minLongPressMS", 600));

      qcState = cfg.getStrField( "qcTestState", "qcTest" );
      cF.println( CsmState.asIdx( qcState )  );
      initState = cfg.getStrField( "initState", "init" );
      cF.println( CsmState.asIdx( initState ) );

      cF.println( cfg.getStrField( "systemAudio", "system/audio" ) );
      cF.println( cfg.getStrField( "bgPulse",    "_49G" )  );
      cF.println( cfg.getStrField( "fgPlaying",   "G!" ) );
      cF.println( cfg.getStrField( "fgPlayPaused", "G2_3!" ));
      cF.println( cfg.getStrField( "fgRecording",  "R!" )  );
      cF.println( cfg.getStrField( "fgRecordPaused", "R2_3!" ) );
      cF.println( cfg.getStrField( "fgSavingRec",  "O!" )  );
      cF.println( cfg.getStrField( "fgSaveRec",   "G3_3G3" )  );
      cF.println( cfg.getStrField( "fgCancelRec",  "R3_3R3" )  );
      cF.println( cfg.getStrField( "fgUSB_MSC",   "O5o5!" )  );
      cF.println( cfg.getStrField( "fgTB_Error",  "R8_2R8_2R8_20!" ) );
      cF.println( cfg.getStrField( "fgNoUSBcable", "_3R3_3R3_3R3_5!" ) );
      cF.println( cfg.getStrField( "fgUSBconnect", "G5g5!" ));
      cF.println( cfg.getStrField( "fgPowerDown",  "G_3G_3G_9G_3G_9G_3" ));
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
    // if ( org.amplio.CsmState.nCSMstates != csts.fieldCnt() )
    //    org.amplio.CSMcompile.Report( "CStates: has " + org.amplio.CsmState.nCSMstates  + " fields, but " + csts.fieldCnt() + " were defined" );
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
      
  public static void writeSysAudioToPreloadC(PrintWriter cF ){
    cF.println( "static AudioList_t preSysAudio[] = { " );
    cF.println(  CsmAction.SysAudio.size() + ",  // # PlaySys prompts used by CSM " );
    cF.println( "{ " );
    for( String s: CsmAction.SysAudio )
      cF.println( "\"" + s + "\"," );
    if ( CsmAction.SysAudio.size()==0 )
      cF.println( "\"welcome\"" );  // so it will compile
    cF.println( "} ");

    cF.println( "};  // preSysAudio " );
    cF.println(" ");
  }
  public static void writeSysAudioToCsmData(PrintWriter cF ){
    cF.println( CsmAction.SysAudio.size());
    for( String s: CsmAction.SysAudio )
      cF.println( s );
  }

  public static String pad( int n ){
    String s = "";
    for ( int i=0; i<n; i++ )
      s += " ";
    return s;
  }
  public static void writeStatesToPreloadC(PrintWriter cF ){
  //  cF.println( "int   nCSMstates = " + org.amplio.CsmState.nCSMstates + ";  // TBook state machine definition " );
    for (int i=0; i< CsmState.nCSMstates; i++ ){
      CsmState st = CsmState.stByIdx( i );
      if ( st==null ) 
        CSMcompile.Report( "CState " + i + " is not defined." );
      else {
        String aPtrs = "";
        int np = 18 - st.nm.length();
        int nA = st.actions.size();
        if (nA==0 )
          cF.println( "static ActionList_t a_" + st.nm + pad(np-2) + " = {  0,  { 0 } };" );
        else {
          for (int iA = 0; iA < nA; iA++) {
            CsmAction a = st.actions.get(iA);
            cF.println("static csmAction_t  a_" + st.nm + "_" + iA + pad(np-4) + " = " + a + ";");
            aPtrs += (iA == 0 ? "" : ", ") + "&a_" + st.nm + "_" + iA;
          }
          cF.println("static ActionList_t a_" + st.nm + pad(np-2) + " = { " + nA + ", { " + aPtrs + " } };");
        }
        String nxtIdxStr = "";
        int nE = CsmToken.nEvents();
        for (int iEvt=0; iEvt < nE; iEvt++ ){
          String enm = CsmToken.eventName( iEvt );
          nxtIdxStr += ( iEvt==0? "" : "," ) + String.format( "%2d", st.nxtStIdx( enm ));
        }
        cF.println( "static short        n_" + st.nm + "[]" + pad(np-4)+ " = { " + nxtIdxStr + "};" );
        cF.println( "static CState_t     " + st.nm + pad(np) + " = { " + i + ", \"" + st.nm + "\", " + nE + ", n_" + st.nm + ", &a_" + st.nm + " };  // [" + i + "] " );
        cF.println(" ");
      }
    }
  }
  public static void writeStatesToCsmData(PrintWriter cF ){
    cF.println( CsmState.nCSMstates );
    for (int i=0; i< CsmState.nCSMstates; i++ ) {
      CsmState st = CsmState.stByIdx(i);
      if (st == null)
        CSMcompile.Report("CState " + i + " is not defined.");
      else {
        cF.println(st.idx);
        cF.println(st.nm);
        cF.println(CsmToken.nEvents());  // # nxtStates
        String nxtIdxStr = "";
        for (int iEvt = 0; iEvt < CsmToken.nEvents(); iEvt++) {
          String enm = CsmToken.eventName(iEvt);
          nxtIdxStr += String.format("%2d", st.nxtStIdx(enm)) + ", ";
        }
        cF.println(nxtIdxStr);

        int nA = st.actions.size();
        cF.println(nA);  // # Actions
        for (int iA = 0; iA < nA; iA++) {
          CsmAction a = st.actions.get(iA);
          cF.println( a.act );
          if (a.arg=="") cF.println( "_" ); else cF.println( a.arg );
        }
      }
    }
  }
  public static void finishPreloadC(PrintWriter cF, String ver ){
    int nS = CsmState.nCSMstates;
    cF.println( "static CSList_t  preCSlist = {  " + nS + ", " );
    cF.println( "  {");
    String stNms = "";
    for (int i=0; i< nS; i++ ){
      CsmState st = CsmState.stByIdx( i );
      int np = 18 - st.nm.length();
      if ( st==null )
        CSMcompile.Report( "CState " + i + " is not defined." );
      else
        stNms += pad(np) + "&" + st.nm + ",";

        if ( i%5==4 ){ cF.println( stNms ); stNms = ""; }
    }
    cF.println( stNms );
    cF.println( "  }");
    cF.println( "};  ");

    cF.println( "static CSM_t preCSM = { " );
    cF.println( "    \"" + ver + "\", " );
    cF.println( "    &preTB_Config, preSysAudio, &preCSlist" );
    cF.println( "};  // preCSM");

    cF.println( " " );
    cF.println( "void     preloadCSM( void ){" );
    cF.println( "    CSM = &preCSM;" );
    cF.println( "    TB_Config = &preTB_Config;" );
    cF.println( "}  // preloadCSM()");
    cF.println( " ");
    cF.println( "// preloadCSM.c ");
  }

    public static void main(String[] args) throws Exception {
        Namespace parsedArgs = parseArgs(args);
        showPredecessors = parsedArgs.getBoolean("predecessors");
        showSuccessors = parsedArgs.getBoolean("successors");
        String source_path = parsedArgs.getString("source");
        String out_data_path = parsedArgs.getString("csm_data");
        String preparsed_path = parsedArgs.getString("preparsed");
        System.out.println("CSMcompile version: " + version);

        compile(source_path, out_data_path, preparsed_path);

        if (ErrorCount>0) {
            System.exit(1);
        }
    }

    private static void compile(String source_path, String csm_data_path, String preparsed_path)
        throws IOException
    {

        File preloadCFile = new File(preparsed_path);
        File csmDataFile = new File(csm_data_path);
        PrintWriter preloadCWriter = new PrintWriter(preloadCFile);
        PrintWriter csmDataWriter = new PrintWriter(csmDataFile);

        Path file = Paths.get(source_path);
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        System.out.println("processing " + file.toAbsolutePath() + " of: "
            + formatDateTime(attr.lastModifiedTime()));

        preloadCWriter.println("// preloaded TBook Control State Machine   preloadCSM.c");
        preloadCWriter.println("// generated by org.amplio.CSMcompile version: " + version);
        preloadCWriter.println("// run at " + LocalDateTime.now().format(DATE_FORMATTER));
        preloadCWriter.println(
            "// from " + file.toAbsolutePath() + " of: " + formatDateTime(attr.lastModifiedTime()));
        JSONish jsh = new JSONish(file.toAbsolutePath().toString());
        System.out.println("  version: " + jsh.FirstLine);
        jsV def = jsh.result;

        csmDataWriter.println(jsh.FirstLine);
        csmDataWriter.println("org.amplio.CSMcompile " + version + " from " + file.toAbsolutePath() + " of "
            + formatDateTime(attr.lastModifiedTime()));

        System.out.println("to " + preparsed_path + (showPredecessors ?
                                                     " (showing predecessor states) " :
                                                     " (no -showPred)") + (showSuccessors ?
                                                                           " (showing successor states) " :
                                                                           " (no -showSucc)"));

        preloadCWriter.println("#include \"packageData.h\" ");
        preloadCWriter.println("//Ver:   " + jsh.FirstLine);

        writeConfigToPreloadC(def, preloadCWriter);  // initState will be state 0
        writeConfigToCsmData(def, csmDataWriter); // writeConfig to csm_data.txt

        buildCSM(def);

        writeSysAudioToPreloadC(preloadCWriter);
        writeSysAudioToCsmData(csmDataWriter);

        writeStatesToPreloadC(preloadCWriter);
        writeStatesToCsmData(csmDataWriter);
        csmDataWriter.close();
        System.out.println("org.amplio.CSMcompile wrote " + csm_data_path);

        finishPreloadC(preloadCWriter, jsh.FirstLine);

        preloadCWriter.close();

        if (ErrorCount>0) {
            System.out.println("org.amplio.CSMcompile reported " + ErrorCount
                + " problems, output may be incorrect.");
            //  System.exit( ErrorCount );
        }
        System.out.println("org.amplio.CSMcompile wrote " + preparsed_path);
    }

  /**
   * Define and parse arguments for the main program.
   *
   * @param args from the command line.
   * @return a "Namespace" object with values for all of the arguments.
   */
  private static Namespace parseArgs(String[] args) {
      ArgumentParser parser = ArgumentParsers.newFor("Main")
                                             .build()
                                             .defaultHelp(true)
                                             .description("Compile control_dev.txt file.");
      parser.addArgument("--predecessors", "-showPred", "-p")
            .action(Arguments.storeTrue())
            .help("Show predecessor states.");
      parser.addArgument("--successors", "-showSucc", "-s")
            .action(Arguments.storeTrue())
            .help("Show succecessor states.");
      parser.addArgument("source").setDefault("control_def.txt").help("Input control_def.txt file");
      parser.addArgument("preparsed")
            .setDefault("preloadCSM.c")
            .help("Output pre-parsed preloadCSM.c file");
      parser.addArgument("csm_data")
            .setDefault("csm_data.txt")
            .help("Output compiled csm_data.txt");
      Namespace namespace = null;
      try {
          namespace = parser.parseArgs(args);
      } catch (ArgumentParserException e) {
          parser.handleError(e);
          System.exit(1);
      }
      return namespace;
  }


}
