// CsmAction
import java.util.List;
import java.util.ArrayList;

public class CsmAction {
  //***********************************  CLASS  ***********************************
  public static List<String> SysAudio = new ArrayList<String>();
  
  //***********************************  INSTANCE  ***********************************
  public String act;
  public String arg = "";
  
  public CsmAction( String actn, String argument ) { 
    act = actn;
    if ( argument!=null) arg = argument;

    if ( !CsmToken.isAction( act ) )
      CSMcompile.Report( "unrecognized Action: '" + act + "'" );
    if ( act.equals("playSys") )
      SysAudio.add( arg );
  }
  public String toString(){
    return "{ " + act + ", \"" + arg + "\" },";
  }
}
