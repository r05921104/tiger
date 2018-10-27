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

```
