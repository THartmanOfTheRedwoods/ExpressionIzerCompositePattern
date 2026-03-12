grammar Expr;

@header {
package edu.redwoods.parser;
import edu.redwoods.*;
}

// 1. ADD / SUB
expr returns [Expression value]
    : l=multExpr { $value = $l.value; }
      ( '+' r=multExpr { $value = new BinaryExpression(Operator.ADD, $value, $r.value); }
      | '-' r=multExpr { $value = new BinaryExpression(Operator.SUB, $value, $r.value); }
      )* ;

// 2. MUL / DIV
multExpr returns [Expression value]
    : l=unary { $value = $l.value; }
      ( '*' r=unary { $value = new BinaryExpression(Operator.MUL, $value, $r.value); }
      | '/' r=unary { $value = new BinaryExpression(Operator.DIV, $value, $r.value); }
      )* ;

// 3. UNARY MINUS (Crucial for handling -x)
unary returns [Expression value]
    : '-' u=unary { $value = new BinaryExpression(Operator.SUB, new Constant(0), $u.value); }
    | p=powExpr   { $value = $p.value; }
    ;

// 4. POWER
powExpr returns [Expression value]
    : a=atom { $value = $a.value; }
      ( '^' b=unary { $value = new BinaryExpression(Operator.POW, $value, $b.value); } )?
    ;

// 5. ATOMS
atom returns [Expression value]
    : NUMBER { $value = new Constant(Double.parseDouble($NUMBER.text)); }
    | IDENT  { $value = new Variable($IDENT.text); }
    | '(' e=expr ')' { $value = $e.value; }
    ;

NUMBER : [0-9]+ ('.' [0-9]+)? ;
IDENT  : [a-zA-Z_] [a-zA-Z0-9_]* ;
WS     : [ \t\r\n]+ -> skip ;