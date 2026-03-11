// Expr.g4
grammar Expr;

@header {
package edu.redwoods.parser;
}

// Parser Rules: This is where we build the expression tree using your Java classes.
// The return type for the 'expr' rule is your custom 'edu.redwoods.Expression' class.
expr returns [edu.redwoods.Expression value]:
    l=multExpr {
        // Start with the value of the first multExpr
        $value = $l.value;
    } ( '+' r=multExpr {
        // When we see a '+', combine the left and right values using your edu.redwoods.BinaryExpression
        $value = new edu.redwoods.BinaryExpression(edu.redwoods.Operator.ADD, $value, $r.value);
    } | '-' r=multExpr {
        $value = new edu.redwoods.BinaryExpression(edu.redwoods.Operator.SUB, $value, $r.value);
    } )*
    ;

multExpr returns [edu.redwoods.Expression value]:
    l=atom {
        $value = $l.value;
    } ( '*' r=atom {
        $value = new edu.redwoods.BinaryExpression(edu.redwoods.Operator.MUL, $value, $r.value);
    } | '/' r=atom {
        $value = new edu.redwoods.BinaryExpression(edu.redwoods.Operator.DIV, $value, $r.value);
    } )*
    ;

atom returns [edu.redwoods.Expression value]:
    NUMBER {
        // Create a edu.redwoods.Constant from the matched number text
        $value = new edu.redwoods.Constant(Double.parseDouble($NUMBER.text));
    }
    | IDENT {
        // Create a edu.redwoods.Variable from the matched identifier text
        $value = new edu.redwoods.Variable($IDENT.text);
    }
    | '(' expr ')' {
        // Parentheses just pass through the inner expression's value
        $value = $expr.value;
    }
    ;

// Lexer Rules: This defines the basic tokens of your language.
NUMBER: [0-9]+ ('.' [0-9]+)? ; // Matches integers and decimals
IDENT: [a-zA-Z_][a-zA-Z0-9_]* ; // Matches variable names
WS: [ \t\r\n]+ -> skip ; // Ignore whitespace