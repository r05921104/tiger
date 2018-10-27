package lexer;

import static control.Control.ConLexer.dump;

import java.io.InputStream;
import java.util.HashMap;

import lexer.Token.Kind;
import util.Todo;

public class Lexer
{
  String fname; // the input file name to be compiled
  InputStream fstream; // input stream for the above file
  HashMap<String, Token.Kind> keyword_map;
  int lineNum;
  boolean isComment;

  public Lexer(String fname, InputStream fstream)
  {
    this.fname = fname;
    this.fstream = fstream;
    // Initialize the keyword map
    keyword_map = new HashMap<String, Token.Kind>();
    keyword_map.put("boolean", Token.Kind.TOKEN_BOOLEAN);
    keyword_map.put("class", Token.Kind.TOKEN_CLASS);
    keyword_map.put("else", Token.Kind.TOKEN_ELSE);
    keyword_map.put("extends", Token.Kind.TOKEN_EXTENDS);
    keyword_map.put("false", Token.Kind.TOKEN_FALSE);
    keyword_map.put("if", Token.Kind.TOKEN_IF);
    keyword_map.put("int", Token.Kind.TOKEN_INT);
    keyword_map.put("length", Token.Kind.TOKEN_LENGTH);
    keyword_map.put("main", Token.Kind.TOKEN_MAIN);
    keyword_map.put("new", Token.Kind.TOKEN_NEW);
    keyword_map.put("out", Token.Kind.TOKEN_OUT);
    keyword_map.put("println", Token.Kind.TOKEN_PRINTLN);
    keyword_map.put("public", Token.Kind.TOKEN_PUBLIC);
    keyword_map.put("return", Token.Kind.TOKEN_RETURN);
    keyword_map.put("static", Token.Kind.TOKEN_STATIC);
    keyword_map.put("String", Token.Kind.TOKEN_STRING);
    keyword_map.put("System", Token.Kind.TOKEN_SYSTEM);
    keyword_map.put("this", Token.Kind.TOKEN_THIS);
    keyword_map.put("true", Token.Kind.TOKEN_TRUE);
    keyword_map.put("void", Token.Kind.TOKEN_VOID);
    keyword_map.put("while", Token.Kind.TOKEN_WHILE);

    // Initialize the lineNum to one
    this.lineNum = 1;
    this.isComment = false;
  }

  // When called, return the next token (refer to the code "Token.java")
  // from the input stream.
  // Return TOKEN_EOF when reaching the end of the input stream.
  private Token nextTokenInternal() throws Exception
  {
    int c = this.fstream.read();
    if (-1 == c)
      // The value for "lineNum" is now "null",
      // you should modify this to an appropriate
      // line number for the "EOF" token.
      return new Token(Kind.TOKEN_EOF, null);

    // skip all kinds of "blanks"
    while (' ' == c || '\t' == c || '\n' == c || '\r' == c) {
      if (c == '\n') {
        this.lineNum++;
      }
      c = this.fstream.read();
    }
    
    // comment
    while ('/' == c) {
      this.fstream.mark(1);
      c = this.fstream.read();
      if (c == '/') {
        while (true) {
          c = this.fstream.read();
          if (c == '\n') {
            this.lineNum++;
            break;
          }
        }
        c = this.fstream.read();
        while (' ' == c || '\t' == c || '\n' == c || '\r' == c) {
          if (c == '\n') {
            this.lineNum++;
          }
          c = this.fstream.read();
        }
        continue;
      } else {
        this.fstream.reset();
        return new Token(Kind.TOKEN_UNKNOWN, this.lineNum);
      }
    }

    if (-1 == c)
      return new Token(Kind.TOKEN_EOF, null);

    // See if this is an identifier
    if (isAlphabet(c)) {
      StringBuilder builder = new StringBuilder(Character.toString ((char) c));
      // Mark in order to reset later.
      this.fstream.mark(1);
      c = this.fstream.read();
      while (isAlphabet(c) || isDigit(c)) {
        builder.append(Character.toString ((char) c));
        this.fstream.mark(1);
        c = this.fstream.read();
      }
      String build_string = builder.toString();
      // reset the fstream by one character
      this.fstream.reset();
      if (keyword_map.containsKey(build_string)) {
        // If this is a keyword
        return new Token(keyword_map.get(build_string), this.lineNum, build_string);
      } else {
        return new Token(Kind.TOKEN_ID, this.lineNum, build_string);
      }
    }

    // See if this is an number
    if (isDigit(c)) {
      StringBuilder builder = new StringBuilder(Character.toString ((char) c));
      this.fstream.mark(1);
      c = this.fstream.read();
      while (isDigit(c)) {
        builder.append(Character.toString ((char) c));
        this.fstream.mark(1);
        c = this.fstream.read();
      }
      this.fstream.reset();
      String build_string = builder.toString();
      return new Token(Kind.TOKEN_NUM, this.lineNum, build_string);
    }

    // Other operators
    switch (c) {
    case '+':
      return new Token(Kind.TOKEN_ADD, this.lineNum);
    case '&':
      // Has to deal with '&&'
      this.fstream.mark(1);
      c = this.fstream.read();
      if (c == '&') {
        return new Token(Kind.TOKEN_AND, this.lineNum);
      } else {
        System.out.println("Lexical Error!");
        this.fstream.reset();
        return new Token(Kind.TOKEN_UNKNOWN, this.lineNum);
      }
    case '=':
      return new Token(Kind.TOKEN_ASSIGN, this.lineNum);
    case ',':
      return new Token(Kind.TOKEN_COMMER, this.lineNum);
    case '.':
      return new Token(Kind.TOKEN_DOT, this.lineNum);
    case '{':
      return new Token(Kind.TOKEN_LBRACE, this.lineNum);
    case '[':
      return new Token(Kind.TOKEN_LBRACK, this.lineNum);
    case '(':
      return new Token(Kind.TOKEN_LPAREN, this.lineNum);
    case '<':
      return new Token(Kind.TOKEN_LT, this.lineNum);
    case '!':
      return new Token(Kind.TOKEN_NOT, this.lineNum);
    case '}':
      return new Token(Kind.TOKEN_RBRACE, this.lineNum);
    case ']':
      return new Token(Kind.TOKEN_RBRACK, this.lineNum);
    case ')':
      return new Token(Kind.TOKEN_RPAREN, this.lineNum);
    case ';':
      return new Token(Kind.TOKEN_SEMI, this.lineNum);
    case '-':
      return new Token(Kind.TOKEN_SUB, this.lineNum);
    case '*':
      return new Token(Kind.TOKEN_TIMES, this.lineNum);

    default:
      // Lab 1, exercise 2: supply missing code to
      // lex other kinds of tokens.
      // Hint: think carefully about the basic
      // data structure and algorithms. The code
      // is not that much and may be less than 50 lines. If you
      // find you are writing a lot of code, you
      // are on the wrong way.
      return null;
    }
  }

  public Token nextToken()
  {
    Token t = null;

    try {
      t = this.nextTokenInternal();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    if (dump)
      System.out.println(t.toString());
    return t;
  }

  private boolean isAlphabet(int read_char) {
    boolean result = false;
    if (read_char >= 65 && read_char <= 89) {
      // A-Z
      result = true;
    } else if (read_char >= 97 && read_char <= 122) {
      // a-z
      result = true;
    } else if (read_char == 95) {
      // _
      result = true;
    }
    return result;
  }

  private boolean isDigit(int read_char) {
    boolean result = false;
    if (read_char >= 48 && read_char <= 57) {
      // 0-9
      result = true;
    }
    return result;
  }
}
