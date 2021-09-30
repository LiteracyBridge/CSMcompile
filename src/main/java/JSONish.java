// JSONish parser
public class JSONish {
  
  //***********************************  INSTANCE  ***********************************
  private TokenReader cdef;
  public jsV result = null;
  
  public String FirstLine = "";
  
  public JSONish( String fname ) { 
    
    cdef = new TokenReader( fname );
    CsmToken tk = cdef.currToken();
    FirstLine = cdef.FirstLine;
    
    result = parseValue( 0, "" );
  }
  public String toString(){
    return result.toString();
  }

  private void err( String msg, String path ){
    CSMcompile.Report( "JSONish parse err: " + msg + "  at "+path );
   // System.out.println( msg + cdef.toString() );
  }
  private jsV parseListObj( CsmToken.tknPunct close, int depth, String path ){
    // called with cTkn==LBrace or LBracket
    // parse list contents & save in nms[] vals[], then allocate listNds at end & return list tknid
    // calls parseValue to parse vals that are {} or [] lists
    jsV lst = new jsV( close==CsmToken.tknPunct.RBrace );
    CsmToken nm = null;
    String elPos = "";
    
    CsmToken tkn = cdef.nextToken();
    CsmToken.tknPunct pTkn = CsmToken.asPunct( tkn );  // move to first token
    while ( pTkn != close ){
      if ( close==CsmToken.tknPunct.RBrace ){  // parsing Obj, so get <Nm> <:> 
        nm = parseName( depth, path + elPos );
        pTkn = CsmToken.asPunct( cdef.currToken() );
        if ( pTkn==CsmToken.tknPunct.Colon )   // check if Colon
          pTkn = CsmToken.asPunct( cdef.nextToken()); // and skip it
        elPos = nm.toString() + ":";
      } else
        elPos = "[" + lst.listCnt() + "]";

      // at start of Value 
      jsV val = parseValue( depth, path + elPos );

      if (nm==null)
        lst.lstAdd( val );
      else
        lst.objAdd( nm, val );
      pTkn = CsmToken.asPunct( cdef.currToken() );
      if ( pTkn==CsmToken.tknPunct.Comma ) // check if nxt tkn is a comma
        pTkn = CsmToken.asPunct( cdef.nextToken()); // and skip it, if it is
      // pTkn should now be closeBr, or nxt Nm or Val
      if ( cdef.currToken()==TokenReader.nullTkn ) 
        err( "!! EOF without closeBr", path );
    }
    cdef.nextToken();   // accept close
   // if ( close==CsmToken.tknPunct.RBracket ) System.out.println( lst );
    return lst;
  }
  
  private CsmToken parseName( int depth, String path ){
    CsmToken.tknPunct pTkn = CsmToken.asPunct( cdef.currToken() );
    CsmToken nm = null;
    if ( pTkn==CsmToken.tknPunct.DQuote ){
      nm = cdef.nextToken();
      pTkn = CsmToken.asPunct( cdef.nextToken() );
      if ( pTkn!=CsmToken.tknPunct.DQuote )
        err( "Expected matching DQuote after name", path );
    } else
      nm = cdef.currToken();
    cdef.nextToken();
    if ( CsmToken.invalidName( nm ) )
      err( "invalid name '"+nm+"'", path );
    return nm;
  }
  
  private jsV parseValue( int depth, String path ){
    // parse a value-- tkn | { n:v... } | [ v... ] | tkn( arg )
    // cTkn at initial tkn, or LBrace or LBracket
    // calls parseLstObj() for {} or [] lists
    // returns with cTkn after }, ], ), or tkn
    
    CsmToken.tknPunct pTkn = CsmToken.asPunct( cdef.currToken() );
    jsV val, arg;
    if ( pTkn==CsmToken.tknPunct.LBrace ){
      val = parseListObj( CsmToken.tknPunct.RBrace, depth+1, path + " { " );
    } else if ( pTkn==CsmToken.tknPunct.LBracket ){
      val = parseListObj( CsmToken.tknPunct.RBracket, depth+1, path + " [ " );
    } else {
      val = new jsV( parseName(depth, path) );
    }
    pTkn = CsmToken.asPunct( cdef.currToken() );
    if ( pTkn==CsmToken.tknPunct.LParen ){
      pTkn = CsmToken.asPunct( cdef.nextToken() );
      if ( pTkn!=CsmToken.tknPunct.RParen ){
        arg = new jsV( cdef.currToken() );
        val.argAdd( arg.toString() );
        pTkn = CsmToken.asPunct( cdef.nextToken() ); 
        if ( pTkn!=CsmToken.tknPunct.RParen ){
          err( "expected )", path );
        }
      }
      cdef.nextToken();
    }
    //if ( !val.isStr())    System.out.println( cdef.toString() + " => " + val.toString() );
    return val;
  }
  
}
