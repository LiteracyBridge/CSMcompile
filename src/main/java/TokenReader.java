/**
 * TokenReader -- returns tokens from control.def 
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.ArrayList;

public class TokenReader {
  
  //***********************************  INSTANCE  ***********************************
  private BufferedReader cdef;
  private String cline = "";
  private int     clineNum = 0;
  private int     iChr = 0;     //  index of curr char in cline
  
  private final String  delimChars = ",;{}[]()\":";
  private final String  whiteChars = " \t\r\n";
  public  final static CsmToken  nullTkn = new CsmToken("NUL");
  private String  txtTkn = "";   // string for current token being collected
  
  private List< CsmToken > tkns = new ArrayList<CsmToken>();   // tokens for current line
  private CsmToken cTkn = null;    // current token being processed
  
  public String FirstLine = "";
  
  public TokenReader( String filename ) { 
      try {
         cdef = new BufferedReader( new FileReader( filename ));
         nxtLine();
         FirstLine = cline;
         
      } catch(Exception e) {
         e.printStackTrace();
      }
  }
  public void report( String msg ){
    System.out.println( msg + " at line " + clineNum + ":" + iChr );
  }
  private void nxtLine(){
    try {
      cline = cdef.readLine();
      clineNum++;
      if ( cline==null )
        cdef.close();
   //   else
   //     System.out.println( clineNum + ": " +  cline );
    } catch(Exception e) {
       e.printStackTrace();
    }
  }
  public CsmToken currToken(){   // return current token in file (fetch if necessary)
    while ( cTkn==null ){  // make next token current
      if ( tkns.isEmpty() ){  // out of tokens on line - read a line
        nxtLine();
        if (cline==null){
          cTkn = nullTkn;
          break;
        }
        int cmt = cline.indexOf("//");
        if ( cmt >= 0 ) // strip comment from end of line
          cline = cline.substring( 0, cmt );
        
        if ( cline.length()>0 )
          tokenize();
      } else {
        cTkn = tkns.remove( 0 );
      }
   }
   return cTkn;
  }
  public CsmToken  nextToken(){  // INTERNAL:  accept currTkn, then => new currTkn
    if ( cTkn == nullTkn ) 
      return cTkn;   // at EOF
    cTkn = null;  // move to next token
    return currToken();
  }
  public String toString(){
    return " line " + clineNum + " tkn:" + currToken().toString(); // + " of " + tkns.toString();
  }
  public void tokenize(){ // tokenize cline ==> tkns list
    /* tokenizing rules:
     // tokenize:  returns sequence of tknids from a string
     //  white space is ignored, except that it terminates a token
     //  delimeters  are added as single character tokens
     //  enum types GrpID, Punct, Event, Action are predefined, others get allocated as gTkns
     // e.g. 'this,is;a test -2oth_er0 "tkn spc" doesn't work' => 
     //   [ gTkns.0='this' gPunct.Comma gTkns.1=is' gPunct.Semi gTkns:2='a' gTkns:3='test'
     //  gTkns.4="-2oth_er0" gPunct.DQuote gTkns.5='tkn' gTkns.6='spc' gPunct.DQuote gTkns.7="doesn't" gTkns.8='work' ]
     //  note that " doesn't affect whitespace as a delimiter
     */
    int iChr = 0;
    int lpcnt=0;
    while ( true ){
      boolean isNul = (iChr==cline.length());
      char ch = isNul? 0 : cline.charAt( iChr );
      String chS = Character.toString( ch );
      boolean isWht = whiteChars.contains( chS );
      boolean isDlm = delimChars.contains( chS );
      if (( isWht || isDlm || isNul ) && txtTkn.length() > 0 ){  // terminator-- save token in progress
        tkns.add( new CsmToken( txtTkn ));
        txtTkn = "";
      }
      if ( isNul ){  // end of line-- token saved, so return
        //System.out.println( tkns.toString() );
        return;
      }

      if ( ch=='"' ){ // quoted token
        int iQ = cline.indexOf( '"', iChr+1 ); // find matching "
        if (iQ < 0)
          report( "unmatched \" character" );
        txtTkn = cline.substring( iChr, iQ-iChr+1 );   // including "s
        tkns.add( new CsmToken( txtTkn ));
        iChr = iQ+1;
      } else {
        if ( isDlm ) // also add delimiter as token
          tkns.add( new CsmToken( chS ));
        else if ( !isWht ) // token character, append it
          txtTkn = txtTkn + ch; 
        iChr++;
      }
      lpcnt++;
    }
  }
  public void printFile(  ){
    while ( currToken() != nullTkn ){
      // System.out.println( currToken().toString() );
       nextToken();
    }       
  }
}
 
  
