package codegen.C;

import codegen.C.Ast.Class.ClassSingle;
import codegen.C.Ast.Dec;
import codegen.C.Ast.Dec.DecSingle;
import codegen.C.Ast.Exp;
import codegen.C.Ast.Exp.Add;
import codegen.C.Ast.Exp.And;
import codegen.C.Ast.Exp.ArraySelect;
import codegen.C.Ast.Exp.Call;
import codegen.C.Ast.Exp.Id;
import codegen.C.Ast.Exp.Length;
import codegen.C.Ast.Exp.Lt;
import codegen.C.Ast.Exp.NewIntArray;
import codegen.C.Ast.Exp.NewObject;
import codegen.C.Ast.Exp.Not;
import codegen.C.Ast.Exp.Num;
import codegen.C.Ast.Exp.Sub;
import codegen.C.Ast.Exp.This;
import codegen.C.Ast.Exp.Times;
import codegen.C.Ast.MainMethod.MainMethodSingle;
import codegen.C.Ast.Method;
import codegen.C.Ast.Method.MethodSingle;
import codegen.C.Ast.Program.ProgramSingle;
import codegen.C.Ast.Stm;
import codegen.C.Ast.Stm.Assign;
import codegen.C.Ast.Stm.AssignArray;
import codegen.C.Ast.Stm.Block;
import codegen.C.Ast.Stm.If;
import codegen.C.Ast.Stm.Print;
import codegen.C.Ast.Stm.While;
import codegen.C.Ast.Type.ClassType;
import codegen.C.Ast.Type.Int;
import codegen.C.Ast.Type.IntArray;
import codegen.C.Ast.Vtable;
import codegen.C.Ast.Vtable.VtableSingle;
import control.Control;
import java.util.ArrayList;

public class PrettyPrintVisitor implements Visitor
{
  private int indentLevel;
  private java.io.BufferedWriter writer;
  private ArrayList<String> locals_list;
  public PrettyPrintVisitor()
  {
    this.indentLevel = 2;
    locals_list = new ArrayList<String>();
  }

  private void indent()
  {
    this.indentLevel += 2;
  }

  private void unIndent()
  {
    this.indentLevel -= 2;
  }

  private void printSpaces()
  {
    int i = this.indentLevel;
    while (i-- != 0)
      this.say(" ");
  }

  private void sayln(String s)
  {
    say(s);
    try {
      this.writer.write("\n");
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private void say(String s)
  {
    try {
      this.writer.write(s);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  // /////////////////////////////////////////////////////
  // expressions
  @Override
  public void visit(Add e)
  {
    this.say("(");
    e.left.accept(this);
    this.say(" + ");
    e.right.accept(this);
    this.say(")");
    return;
  }

  @Override
  public void visit(And e)
  {
    this.say("(");
    e.left.accept(this);
    this.say(" && ");
    e.right.accept(this);
    this.say(")");
    return;
  }

  @Override
  public void visit(ArraySelect e)
  {
    e.array.accept(this);
    this.say("[");
    e.index.accept(this);
    // The first element of the int array is the length of the array.
    // See the ./runtime/gc.c for more details.
    this.say("+1]");
    return;
  }

  @Override
  public void visit(Call e)
  {
    String _assign = e.assign;
    if (this.locals_list.contains(e.assign)) {
      _assign = "frame." +  _assign;
    }
    this.say("(" + _assign + "=");
    e.exp.accept(this);
    this.say(", ");
    this.say(_assign + "->vptr->" + e.id + "(" + _assign);
    int size = e.args.size();
    if (size == 0) {
      this.say("))");
      return;
    }
    for (Exp.T x : e.args) {
      this.say(", ");
      x.accept(this);
    }
    this.say("))");
    return;
  }

  @Override
  public void visit(Id e)
  {
    if (e.isField) {
      this.say("this -> " + e.id);
    } else if (this.locals_list.contains(e.id)) {
      this.say("frame." + e.id);
    } else {
      this.say(e.id);
    }
  }

  @Override
  public void visit(Length e)
  {
    // TODO
    e.array.accept(this);
    this.say("[0]");
    return;
  }

  @Override
  public void visit(Lt e)
  {
    this.say("(");
    e.left.accept(this);
    this.say(" < ");
    e.right.accept(this);
    this.say(")");
    return;
  }

  @Override
  public void visit(NewIntArray e)
  {
    // TODO
    this.say("(int*) Tiger_new_array(");
    e.exp.accept(this);
    this.say(")");
    return;

  }

  @Override
  public void visit(NewObject e)
  {
    this.say("((struct " + e.id + "*)(Tiger_new (&" + e.id
        + "_vtable_, sizeof(struct " + e.id + "))))");
    return;
  }

  @Override
  public void visit(Not e)
  {
    this.say("!(");
    e.exp.accept(this);
    this.say(")");
    return;
  }

  @Override
  public void visit(Num e)
  {
    this.say(Integer.toString(e.num));
    return;
  }

  @Override
  public void visit(Sub e)
  {
    this.say("(");
    e.left.accept(this);
    this.say(" - ");
    e.right.accept(this);
    this.say(")");
    return;
  }

  @Override
  public void visit(This e)
  {
    this.say("this");
  }

  @Override
  public void visit(Times e)
  {
    e.left.accept(this);
    this.say(" * ");
    e.right.accept(this);
    return;
  }

  // statements
  @Override
  public void visit(Assign s)
  {
    this.printSpaces();
    if (s.isField) {
      this.say("this -> " + s.id + " = ");
    } else if (this.locals_list.contains(s.id)) {
      this.say("frame." + s.id + " = ");
    } else {
      this.say(s.id + " = ");
    }
    s.exp.accept(this);
    this.sayln(";");
    return;
  }

  @Override
  public void visit(AssignArray s)
  {
    this.printSpaces();
    if (s.isField) {
      this.say("this -> " + s.id + "[");
    } else if (this.locals_list.contains(s.id)) {
      this.say("frame." + s.id + "[");
    } else {
      this.say(s.id + "[");
    }
    s.index.accept(this);
    this.say("] = ");
    s.exp.accept(this);
    this.sayln(";");
    return;
  }

  @Override
  public void visit(Block s)
  {
    this.printSpaces();
    this.sayln("{");
    this.indent();
    for (Stm.T stm : s.stms) {
      stm.accept(this);
    }
    this.unIndent();
    this.printSpaces();
    this.sayln("}");
    return;
  }

  @Override
  public void visit(If s)
  {
    this.printSpaces();
    this.say("if (");
    s.condition.accept(this);
    this.sayln(")");
    this.indent();
    s.thenn.accept(this);
    this.unIndent();
    this.sayln("");
    this.printSpaces();
    this.sayln("else");
    this.indent();
    s.elsee.accept(this);
    this.sayln("");
    this.unIndent();
    return;
  }

  @Override
  public void visit(Print s)
  {
    this.printSpaces();
    // The function System_out_println is provided by the runtime in lib.c!
    this.say("System_out_println (");
    s.exp.accept(this);
    this.sayln(");");
    return;
  }

  @Override
  public void visit(While s)
  {
    this.printSpaces();
    this.say("while(");
    s.condition.accept(this);
    this.sayln(")");
    
    if (s.body instanceof Stm.Block) {
      s.body.accept(this);
    } else {
      this.indent();
      s.body.accept(this);
      this.unIndent();
    }
    return;
  }

  // type
  @Override
  public void visit(ClassType t)
  {
    this.say("struct " + t.id + " *");
  }

  @Override
  public void visit(Int t)
  {
    this.say("int");
  }

  @Override
  public void visit(IntArray t)
  {
    // TODO: Need to be discussed!
    this.say("int *");
  }

  // dec
  @Override
  public void visit(DecSingle d)
  {
  }

  // method
  @Override
  public void visit(MethodSingle m)
  {
    String arguments_gc_map = "";
    String locals_gc_map = "";
    this.sayln("struct " + m.classId + "_" + m.id + "_gc_frame{");
    this.sayln("  void *prev;");
    this.sayln("  char *arguments_gc_map;");
    this.sayln("  int *arguments_base_address;");
    this.sayln("  char *locals_gc_map;");
    /* 
    * The original declaration of locals is directly in the call stack.
    * Now, the declaration of local variables is moved to the gc frame.
    */
    for (Dec.T d : m.locals) {
      DecSingle dec = (DecSingle) d;
      this.say("  ");
      dec.type.accept(this);
      this.say(" " + dec.id + ";\n");
      // Add the local id to the list and this would be used when generating the code of statements.
      this.locals_list.add(dec.id);
      // Modify the bit vector of locals_gc_map
      if (dec.type instanceof ClassType || dec.type instanceof IntArray) {
        locals_gc_map = locals_gc_map + "1";
      } else {
        locals_gc_map = locals_gc_map + "0";
      }
    }
    this.sayln("};");
    m.retType.accept(this);
    this.say(" " + m.classId + "_" + m.id + "(");
    int size = m.formals.size();
    for (Dec.T d : m.formals) {
      DecSingle dec = (DecSingle) d;
      size--;
      dec.type.accept(this);
      this.say(" " + dec.id);
      if (size > 0)
        this.say(", ");
      // Modify the bit vector of arguments_gc_map
      if (dec.type instanceof ClassType || dec.type instanceof IntArray) {
        arguments_gc_map = arguments_gc_map + "1";
      } else {
        arguments_gc_map = arguments_gc_map + "0";
      }
    }
    this.sayln(")");
    this.sayln("{");
    this.sayln("  struct " + m.classId + "_" + m.id + "_gc_frame frame;");
    this.sayln("  memset(&frame, 0, sizeof(struct " + m.classId + "_" + m.id + "_gc_frame));");
    this.sayln("  frame.prev = prev;");
    this.sayln("  prev = &frame;");
    this.sayln("  frame.arguments_gc_map = \"" + arguments_gc_map +"\";");
    this.sayln("  frame.arguments_base_address = &this;");
    this.sayln("  frame.locals_gc_map = \"" + locals_gc_map +"\";");
    for (Stm.T s : m.stms)
      s.accept(this);

    // pop the gc frame of this method from the global linked list "prev".
    this.sayln("  prev = frame.prev;");

    this.say("  return ");
    m.retExp.accept(this);
    this.sayln(";");
    this.sayln("}");
    this.locals_list.clear();
    return;
  }

  @Override
  public void visit(MainMethodSingle m)
  {
    String locals_gc_map = "";
    this.sayln("struct " + "Tiger" + "_" + "main" + "_gc_frame{");
    this.sayln("  void *prev;");
    this.sayln("  char *arguments_gc_map;");
    this.sayln("  int *arguments_base_address;");
    this.sayln("  char *locals_gc_map;");
    for (Dec.T dec : m.locals) {
      this.say("  ");
      DecSingle d = (DecSingle) dec;
      d.type.accept(this);
      this.say(" ");
      this.sayln(d.id + ";");
      this.locals_list.add(d.id);
      if (d.type instanceof ClassType || d.type instanceof IntArray) {
        locals_gc_map = locals_gc_map + "1";
      } else {
        locals_gc_map = locals_gc_map + "0";
      }
    }
    this.sayln("};\n");
    this.sayln("int Tiger_main ()");
    this.sayln("{");
    this.sayln("  struct " + "Tiger" + "_" + "main" + "_gc_frame frame;");
    this.sayln("  memset(&frame, 0, sizeof(struct Tiger_main_gc_frame));");
    this.sayln("  frame.prev = prev;");
    this.sayln("  prev = &frame;");
    this.sayln("  frame.arguments_gc_map = \"\";");
    this.sayln("  frame.arguments_base_address = 0;");
    this.sayln("  frame.locals_gc_map = \"" + locals_gc_map +"\";");
    m.stm.accept(this);
    this.sayln("  prev = frame.prev;");
    this.sayln("}\n");
    this.locals_list.clear();
    return;
  }

  // vtables
  @Override
  public void visit(VtableSingle v)
  {
    this.sayln("struct " + v.id + "_vtable");
    this.sayln("{");
    // this field stores the gc_map of the corresponding class
    this.sayln("  char* " + v.id + "_gc_map;");
    for (codegen.C.Ftuple t : v.ms) {
      this.say("  ");
      t.ret.accept(this);
      this.sayln(" (*" + t.id + ")();");
    }
    this.sayln("};\n");
    return;
  }

  private void outputVtable(VtableSingle v)
  {
    this.sayln("struct " + v.id + "_vtable " + v.id + "_vtable_ = ");
    this.sayln("{");
    // initialize the gc_map of the corresponding class
    String class_gc_map = "";
    for (codegen.C.Tuple t : v.class_fields) {
      if (t.type instanceof ClassType || t.type instanceof IntArray) {
        class_gc_map = class_gc_map + "1";
      } else {
        class_gc_map = class_gc_map + "0";
      }
    }
    this.sayln("  \"" + class_gc_map + "\",");
    for (codegen.C.Ftuple t : v.ms) {
      this.say("  ");
      this.sayln(t.classs + "_" + t.id + ",");
    }
    this.sayln("};\n");
    return;
  }

  // class
  @Override
  public void visit(ClassSingle c)
  {
    // Must print out the struct obeying the object model in gc.c
    this.sayln("struct " + c.id);
    this.sayln("{");
    this.sayln("  struct " + c.id + "_vtable *vptr;");
    this.sayln("  int isObjOrArray;");
    this.sayln("  int length;");
    this.sayln("  void* forwarding;");
    // Add the attribute of the number of fields.
    // In the forwarding process in garbage collector, the number of fields would be used.
    int fields_num = c.decs.size();
    for (codegen.C.Tuple t : c.decs) {
      this.say("  ");
      t.type.accept(this);
      this.say(" ");
      this.sayln(t.id + ";");
    }
    this.sayln("};");
    return;
  }

  // program
  @Override
  public void visit(ProgramSingle p)
  {
    // we'd like to output to a file, rather than the "stdout".
    try {
      String outputName = null;
      if (Control.ConCodeGen.outputName != null)
        outputName = Control.ConCodeGen.outputName;
      else if (Control.ConCodeGen.fileName != null)
        outputName = Control.ConCodeGen.fileName + ".c";
      else
        outputName = "a.c";

      this.writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
          new java.io.FileOutputStream(outputName)));
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

    this.sayln("// This is automatically generated by the Tiger compiler.");
    this.sayln("// Do NOT modify!\n");
    
    // initiate the prev.
    this.sayln("void* prev = 0;");

    this.sayln("// structures");
    for (codegen.C.Ast.Class.T c : p.classes) {
      c.accept(this);
    }

    this.sayln("// vtables structures");
    for (Vtable.T v : p.vtables) {
      v.accept(this);
    }
    this.sayln("");

    this.sayln("// vtables declared");
    for (Vtable.T v : p.vtables) {
      VtableSingle _v = (VtableSingle) v;
      this.sayln("struct " + _v.id + "_vtable " + _v.id + "_vtable_;");
    }
    
    this.sayln("// methods");
    for (Method.T m : p.methods) {
      m.accept(this);
    }
    this.sayln("");

    this.sayln("// vtables");
    for (Vtable.T v : p.vtables) {
      outputVtable((VtableSingle) v);
    }
    this.sayln("");

    this.sayln("// main method");
    p.mainMethod.accept(this);
    this.sayln("");

    this.say("\n\n");

    try {
      this.writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

  }

}
