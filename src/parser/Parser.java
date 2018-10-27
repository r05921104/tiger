package parser;

import lexer.Lexer;
import lexer.Token;
import lexer.Token.Kind;
import ast.Ast.*;
import java.util.LinkedList;

public class Parser
{
  Lexer lexer;
  Token current;
  boolean lookahead;
  Token ahead;

  public Parser(String fname, java.io.InputStream fstream)
  {
    lexer = new Lexer(fname, fstream);
    current = lexer.nextToken();
    this.lookahead = false;
    this.ahead = null;
  }

  // /////////////////////////////////////////////
  // utility methods to connect the lexer
  // and the parser.

  private void advance()
  {
    if (this.lookahead == true) {
      current = this.ahead;
      this.lookahead = false;
    } else {
      current = lexer.nextToken();
    }
  }

  private void lookAhead() {
    this.ahead = lexer.nextToken();
    this.lookahead = true;
  }

  private void eatToken(Kind kind)
  {
    if (kind == current.kind)
      advance();
    else {
      System.out.println("Expects: " + kind.toString());
      System.out.println("But got: " + current.kind.toString() + " at line: " + current.lineNum);
      System.exit(1);
    }
  }

  private void error()
  {
    System.out.println("Syntax error: compilation aborting...\n");
    System.out.println("line: " + current.lineNum);
    System.exit(1);
    return;
  }

  // ////////////////////////////////////////////////////////////
  // below are method for parsing.

  // A bunch of parsing methods to parse expressions. The messy
  // parts are to deal with precedence and associativity.

  // ExpList -> Exp ExpRest*
  // ->
  // ExpRest -> , Exp
  private LinkedList<Exp.T> parseExpList()
  {
    LinkedList<Exp.T> exp_list = new LinkedList<Exp.T>();
    if (current.kind == Kind.TOKEN_RPAREN)
      return exp_list;
    exp_list.add(parseExp());
    while (current.kind == Kind.TOKEN_COMMER) {
      advance();
      exp_list.add(parseExp());
    }
    return exp_list;
  }

  // AtomExp -> (exp)
  // -> INTEGER_LITERAL
  // -> true
  // -> false
  // -> this
  // -> id
  // -> new int [exp]
  // -> new id ()
  private Exp.T parseAtomExp()
  {
    int lineNum;
    switch (current.kind) {
    case TOKEN_LPAREN:
      advance();
      Exp.T brace_exp =parseExp();
      eatToken(Kind.TOKEN_RPAREN);
      return brace_exp;
    case TOKEN_NUM:
      int number = Integer.parseInt(current.lexeme);
      lineNum = current.lineNum;
      advance();
      return new Exp.Num(number, lineNum);
    case TOKEN_TRUE:
      lineNum = current.lineNum;
      advance();
      return new Exp.True(lineNum);
    case TOKEN_FALSE:
      lineNum = current.lineNum;
      advance();
      return new Exp.False(lineNum);
    case TOKEN_THIS:
      lineNum = current.lineNum;
      advance();
      return new Exp.This(lineNum);
    case TOKEN_ID:
      lineNum = current.lineNum;
      String id = current.lexeme;
      advance();
      return new Exp.Id(id, lineNum);
    case TOKEN_NEW: {
      lineNum = current.lineNum;
      advance();
      switch (current.kind) {
      case TOKEN_INT:
        advance();
        eatToken(Kind.TOKEN_LBRACK);
        Exp.T exp = parseExp();
        eatToken(Kind.TOKEN_RBRACK);
        return new Exp.NewIntArray(exp, lineNum);
      case TOKEN_ID:
        String object_id = current.lexeme;
        advance();
        eatToken(Kind.TOKEN_LPAREN);
        eatToken(Kind.TOKEN_RPAREN);
        return new Exp.NewObject(object_id, lineNum);
      default:
        System.out.println(current.kind);
        error();
        return null;
      }
    }
    default:
      System.out.println(current.kind);
      error();
      return null;
    }
  }

  // NotExp -> AtomExp
  // -> NotExp .id (expList)
  // -> NotExp [exp]
  // -> NotExp .length
  // 
  private Exp.T parseNotExp()
  {
    int lineNum = current.lineNum;
    Exp.T leader = parseAtomExp();
    while (current.kind == Kind.TOKEN_DOT || current.kind == Kind.TOKEN_LBRACK) {
      if (current.kind == Kind.TOKEN_DOT) {
        advance();
        if (current.kind == Kind.TOKEN_LENGTH) {
          advance();
          leader = new Exp.Length(leader, lineNum);
          return leader;
        }
        String id = current.lexeme;
        eatToken(Kind.TOKEN_ID);
        eatToken(Kind.TOKEN_LPAREN);
        LinkedList<Exp.T> exp_list = parseExpList();
        eatToken(Kind.TOKEN_RPAREN);
        leader = new Exp.Call(leader, id, exp_list, lineNum);
      } else {
        advance();
        Exp.T index = parseExp();
        eatToken(Kind.TOKEN_RBRACK);
        leader = new Exp.ArraySelect(leader, index, lineNum);
      }
    }
    return leader;
  }

  // TimesExp -> ! TimesExp
  // -> NotExp
  private Exp.T parseTimesExp()
  {
    int not_num = 0;
    int lineNum = current.lineNum;
    while (current.kind == Kind.TOKEN_NOT) {
      advance();
      not_num++;
    }
    Exp.T leader = parseNotExp();
    for (int i = 0; i < not_num; i++) {
      leader = new Exp.Not(leader, lineNum);
    }
    return leader;
  }

  // AddSubExp -> TimesExp * TimesExp
  // -> TimesExp
  private Exp.T parseAddSubExp()
  {
    int lineNum = current.lineNum;
    Exp.T leader = parseTimesExp();
    while (current.kind == Kind.TOKEN_TIMES) {
      advance();
      Exp.T follower = parseTimesExp();
      leader = new Exp.Times(leader, follower, lineNum);
    }
    return leader;
  }

  // LtExp -> AddSubExp + AddSubExp
  // -> AddSubExp - AddSubExp
  // -> AddSubExp
  private Exp.T parseLtExp()
  {
    int lineNum = current.lineNum;
    Exp.T leader = parseAddSubExp();
    while (current.kind == Kind.TOKEN_ADD || current.kind == Kind.TOKEN_SUB) {
      Exp.T follower;
      if (current.kind == Kind.TOKEN_ADD) {
        // add
        advance();
        follower = parseAddSubExp();
        leader = new Exp.Add(leader, follower, lineNum);
      } else {
        // sub
        advance();
        follower = parseAddSubExp();
        leader = new Exp.Sub(leader, follower, lineNum);
      }
      
    }
    return leader;
  }

  // AndExp -> LtExp < LtExp
  // -> LtExp
  private Exp.T parseAndExp()
  {
    int lineNum = current.lineNum;
    Exp.T leader = parseLtExp();
    while (current.kind == Kind.TOKEN_LT) {
      advance();
      Exp.T follower = parseLtExp();
      leader = new Exp.Lt(leader, follower, lineNum);
    }
    return leader;
  }

  // Exp -> AndExp && AndExp
  // -> AndExp
  private Exp.T parseExp()
  {
    int lineNum = current.lineNum;
    Exp.T leader = parseAndExp();
    while (current.kind == Kind.TOKEN_AND) {
      advance();
      Exp.T follower = parseAndExp();
      leader = new Exp.And(leader, follower, lineNum);
    }
    return leader;
  }

  // Statement -> { Statement* }
  // -> if ( Exp ) Statement else Statement
  // -> while ( Exp ) Statement
  // -> System.out.println ( Exp ) ;
  // -> id = Exp ;
  // -> id [ Exp ]= Exp ;
  private Stm.T parseStatement()
  {
    Stm.T stm_t = null;
    // Lab1. Exercise 4: Fill in the missing code
    // to parse a statement.
    if (current.kind == Kind.TOKEN_IF) {
      // -> if ( Exp ) Statement else Statement

      Stm.T if_stm = null;
      Stm.T else_stm = null;
      Exp.T if_exp = null;
      eatToken(Kind.TOKEN_IF);
      eatToken(Kind.TOKEN_LPAREN);
      if_exp = parseExp();
      eatToken(Kind.TOKEN_RPAREN);
      if_stm = parseStatement();
      eatToken(Kind.TOKEN_ELSE);
      else_stm = parseStatement();
      stm_t = new Stm.If(if_exp, if_stm, else_stm);

    } else if (current.kind == Kind.TOKEN_WHILE) {
      // -> while ( Exp ) Statement

      Exp.T condition = null;
      Stm.T body = null;
      eatToken(Kind.TOKEN_WHILE);
      eatToken(Kind.TOKEN_LPAREN);
      condition = parseExp();
      eatToken(Kind.TOKEN_RPAREN);
      body = parseStatement();
      stm_t = new Stm.While(condition, body);

    } else if (current.kind == Kind.TOKEN_SYSTEM) {
      // -> System.out.println ( Exp ) ;
      Exp.T system_exp = null;
      eatToken(Kind.TOKEN_SYSTEM);
      eatToken(Kind.TOKEN_DOT);
      eatToken(Kind.TOKEN_OUT);
      eatToken(Kind.TOKEN_DOT);
      eatToken(Kind.TOKEN_PRINTLN);
      eatToken(Kind.TOKEN_LPAREN);
      system_exp = parseExp();
      eatToken(Kind.TOKEN_RPAREN);
      eatToken(Kind.TOKEN_SEMI);
      stm_t = new Stm.Print(system_exp);

    } else if (current.kind == Kind.TOKEN_ID) {
      String id = current.lexeme;
      eatToken(Kind.TOKEN_ID);
      if (current.kind == Kind.TOKEN_ASSIGN) {
        // -> id = Exp ;
        Exp.T assign_exp = null;
        eatToken(Kind.TOKEN_ASSIGN);
        assign_exp = parseExp();
        eatToken(Kind.TOKEN_SEMI);
        stm_t = new Stm.Assign(id, assign_exp);
      } else {
        // -> id [ Exp ]= Exp ;
        Exp.T index = null;
        Exp.T assign_exp = null;
        eatToken(Kind.TOKEN_LBRACK);
        index = parseExp();
        eatToken(Kind.TOKEN_RBRACK);
        eatToken(Kind.TOKEN_ASSIGN);
        assign_exp = parseExp();
        eatToken(Kind.TOKEN_SEMI);
        stm_t = new Stm.AssignArray(id, index, assign_exp);
      }
    } else if (current.kind == Kind.TOKEN_LBRACE) {
      // -> { Statement* }
      LinkedList<Stm.T> statements = null;
      eatToken(Kind.TOKEN_LBRACE);
      statements = parseStatements();
      eatToken(Kind.TOKEN_RBRACE);
      stm_t = new Stm.Block(statements);
    }
    return stm_t;
  }

  // Statements -> Statement Statements
  // ->
  private LinkedList<Stm.T> parseStatements()
  {
    LinkedList<Stm.T> statements = new LinkedList<Stm.T>();
    while (current.kind == Kind.TOKEN_LBRACE || current.kind == Kind.TOKEN_IF
        || current.kind == Kind.TOKEN_WHILE
        || current.kind == Kind.TOKEN_SYSTEM || current.kind == Kind.TOKEN_ID) {
      statements.add(parseStatement());
    }
    return statements;
  }

  // Type -> int []
  // -> boolean
  // -> int
  // -> id
  private Type.T parseType()
  {
    // Lab1. Exercise 4: Fill in the missing code
    // to parse a type.
    if (current.kind == Kind.TOKEN_INT) {
      eatToken(Kind.TOKEN_INT);
      if (current.kind == Kind.TOKEN_LBRACK) {
        // int []
        eatToken(Kind.TOKEN_LBRACK);
        eatToken(Kind.TOKEN_RBRACK);
        return new Type.IntArray();
      } else {
        // int
        // nop
        return new Type.Int();
      }
    } else if (current.kind == Kind.TOKEN_BOOLEAN) {
      // -> boolean
      eatToken(Kind.TOKEN_BOOLEAN);
      return new Type.Boolean();
    } else {
      // -> id
      String class_name = current.lexeme;
      eatToken(Kind.TOKEN_ID);
      return new Type.ClassType(class_name);
    }
  }

  // VarDecl -> Type id ;
  private Dec.T parseVarDecl()
  {
    // to parse the "Type" nonterminal in this method, instead of writing
    // a fresh one.
    Type.T type = parseType();
    String id = current.lexeme;
    eatToken(Kind.TOKEN_ID);
    eatToken(Kind.TOKEN_SEMI);
    return new Dec.DecSingle(type, id);
  }

  // VarDecls -> VarDecl VarDecls
  // ->
  private LinkedList<Dec.T> parseVarDecls()
  {
    LinkedList<Dec.T> decs_list = new LinkedList<Dec.T>();
    while (current.kind == Kind.TOKEN_INT || current.kind == Kind.TOKEN_BOOLEAN
        || current.kind == Kind.TOKEN_ID) {
      decs_list.add(parseVarDecl());
    }
    return decs_list;
  }

  // FormalList -> Type id FormalRest*
  // ->
  // FormalRest -> , Type id
  private LinkedList<Dec.T> parseFormalList()
  {
    LinkedList<Dec.T> formal_list = new LinkedList<Dec.T>();
    if (current.kind == Kind.TOKEN_INT || current.kind == Kind.TOKEN_BOOLEAN
        || current.kind == Kind.TOKEN_ID) {
      Type.T type;
      String id;
      Dec.T dec;
      type = parseType();
      id = current.lexeme;
      eatToken(Kind.TOKEN_ID);
      dec = new Dec.DecSingle(type, id);
      formal_list.add(dec);
      while (current.kind == Kind.TOKEN_COMMER) {
        advance();
        type = parseType();
        id = current.lexeme;
        eatToken(Kind.TOKEN_ID);
        dec = new Dec.DecSingle(type, id);
        formal_list.add(dec);
      }
    }
    return formal_list;
  }

  // Method -> public Type id ( FormalList )
  // { VarDecl* Statement* return Exp ;}
  private ast.Ast.Method.T parseMethod()
  {
    // Lab1. Exercise 4: Fill in the missing code
    // to parse a method.

    eatToken(Kind.TOKEN_PUBLIC);
    Type.T return_type = parseType();
    String method_name = current.lexeme;
    eatToken(Kind.TOKEN_ID);
    eatToken(Kind.TOKEN_LPAREN);
    LinkedList<Dec.T> formal_list = parseFormalList();
    eatToken(Kind.TOKEN_RPAREN);
    eatToken(Kind.TOKEN_LBRACE);
    LinkedList<Dec.T> locals_list = new LinkedList<Dec.T>();
    LinkedList<Stm.T> statements_list = new LinkedList<Stm.T>();
    while (current.kind != Kind.TOKEN_RETURN) {
      // BUG: Need to be checked again!
      lookAhead();
      if ((this.ahead.kind == Kind.TOKEN_ID) || 
        ((this.ahead.kind == Kind.TOKEN_LBRACK) && (this.current.kind == Kind.TOKEN_INT))) {
        locals_list.add(parseVarDecl());
      } else {
        statements_list.add(parseStatement());
      }
    }
    eatToken(Kind.TOKEN_RETURN);
    Exp.T return_exp = parseExp();
    eatToken(Kind.TOKEN_SEMI);
    eatToken(Kind.TOKEN_RBRACE);
    return new Method.MethodSingle(return_type, method_name, formal_list, locals_list, statements_list, return_exp);
  }

  // MethodDecls -> MethodDecl MethodDecls
  // ->
  private LinkedList<ast.Ast.Method.T> parseMethodDecls()
  {
    LinkedList<ast.Ast.Method.T> methods_list = new LinkedList<ast.Ast.Method.T>();
    while (current.kind == Kind.TOKEN_PUBLIC) {
      methods_list.add(parseMethod());
    }
    return methods_list;
  }

  // ClassDecl -> class id { VarDecl* MethodDecl* }
  // -> class id extends id { VarDecl* MethodDecl* }
  private ast.Ast.Class.T parseClassDecl()
  {
    eatToken(Kind.TOKEN_CLASS);
    String class_name = current.lexeme;
    eatToken(Kind.TOKEN_ID);
    String parent_name = null;
    if (current.kind == Kind.TOKEN_EXTENDS) {
      eatToken(Kind.TOKEN_EXTENDS);
      parent_name = current.lexeme;
      eatToken(Kind.TOKEN_ID);
    }
    eatToken(Kind.TOKEN_LBRACE);
    LinkedList<Dec.T> decs_lst = parseVarDecls();
    LinkedList<ast.Ast.Method.T> methods_list = parseMethodDecls();
    eatToken(Kind.TOKEN_RBRACE);
    return new ast.Ast.Class.ClassSingle(class_name, parent_name, decs_lst, methods_list);
  }

  // ClassDecls -> ClassDecl ClassDecls
  // ->
  private LinkedList<ast.Ast.Class.T> parseClassDecls()
  {
    LinkedList<ast.Ast.Class.T> classes = new LinkedList<ast.Ast.Class.T>();
    while (current.kind == Kind.TOKEN_CLASS) {
      classes.add(parseClassDecl());
    }
    return classes;
  }

  // MainClass -> class id
  // {
  // public static void main ( String [] id )
  // {
  // Statement
  // }
  // }
  private MainClass.T parseMainClass()
  {
    // Lab1. Exercise 4: Fill in the missing code
    // to parse a main class as described by the
    // grammar above.
    String id = null;
    String arg= null;
    Stm.T stm_t = null;
    eatToken(Kind.TOKEN_CLASS);
    id = current.lexeme;
    eatToken(Kind.TOKEN_ID);
    eatToken(Kind.TOKEN_LBRACE);
    eatToken(Kind.TOKEN_PUBLIC);
    eatToken(Kind.TOKEN_STATIC);
    eatToken(Kind.TOKEN_VOID);
    eatToken(Kind.TOKEN_MAIN);
    eatToken(Kind.TOKEN_LPAREN);
    eatToken(Kind.TOKEN_STRING);
    eatToken(Kind.TOKEN_LBRACK);
    eatToken(Kind.TOKEN_RBRACK);
    arg = current.lexeme;
    eatToken(Kind.TOKEN_ID);
    eatToken(Kind.TOKEN_RPAREN);
    eatToken(Kind.TOKEN_LBRACE);
    stm_t = parseStatement();
    eatToken(Kind.TOKEN_RBRACE);
    eatToken(Kind.TOKEN_RBRACE);
    MainClass.MainClassSingle mainclass = new MainClass.MainClassSingle(id, arg, stm_t);
    return mainclass;
  }

  // Program -> MainClass ClassDecl*
  private Program.T parseProgram()
  {
    MainClass.T mainclass = parseMainClass();
    LinkedList<ast.Ast.Class.T> classes = parseClassDecls();
    eatToken(Kind.TOKEN_EOF);
    Program.ProgramSingle program_single = new Program.ProgramSingle(mainclass, classes);
    return program_single;
  }

  public Program.T parse()
  {
    return parseProgram();
  }
}
