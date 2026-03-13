grammar Expr;

@header {
package edu.redwoods.parser;
import edu.redwoods.*;
}

// 1. ADD / SUB
expr returns [Expression value]
    : l=multExpr { $value = $l.value; }
      ( PLUS r=multExpr { $value = new NaryExpression(Operator.ADD, $value, $r.value); }
      | MINUS r=multExpr { $value = new BinaryExpression(Operator.SUB, $value, $r.value); }
      )* ;

// 2. MUL / DIV (with implicit multiplication)
multExpr returns [Expression value]
    : l=unary { $value = $l.value; }
      ( MUL r=unary { $value = new NaryExpression(Operator.MUL, $value, $r.value); }
      | DIV r=unary { $value = new BinaryExpression(Operator.DIV, $value, $r.value); }
      | { _input.LA(1) != PLUS && _input.LA(1) != MINUS }? r=unary { $value = new NaryExpression(Operator.MUL, $value, $r.value); } // implicit multiplication
      )* ;

// 3. UNARY MINUS
unary returns [Expression value]
    : MINUS u=unary { $value = new BinaryExpression(Operator.SUB, new Constant(0), $u.value); }
    | p=powExpr   { $value = $p.value; }
    ;

// 4. POWER
powExpr returns [Expression value]
    : a=atom { $value = $a.value; }
      ( POW b=unary { $value = new BinaryExpression(Operator.POW, $value, $b.value); } )?
    ;

// 5. ATOMS
atom returns [Expression value]
    : NUMBER { $value = new Constant(Double.parseDouble($NUMBER.text)); }
    | IDENT  { $value = new Variable($IDENT.text); }
    | LPAREN e=expr RPAREN { $value = $e.value; }
    ;

// Tokens
PLUS : '+' ;
MINUS : '-' ;
MUL : '*' ;
DIV : '/' ;
POW : '^' ;
LPAREN : '(' ;
RPAREN : ')' ;
NUMBER : [0-9]+ ('.' [0-9]+)? ;
IDENT  : [a-zA-Z_] [a-zA-Z0-9_]* ;
WS     : [ \t\r\n]+ -> skip ;