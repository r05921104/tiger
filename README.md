# tiger
A simple teaching compiler for MiniJava.

## How to use this ?

``` bash
# Compile the compiler
cd tiger  
mkdir bin
javac -sourcepath src -d bin src/Tiger.java

# Test the lexer
java -cp bin Tiger ./test/Factorial.java -testlexer

# Print the AST
java -cp bin Tiger <inputFile> -dump ast

# Compile and generate the C code
java -cp bin Tiger <inputFile> -codegen C

```
