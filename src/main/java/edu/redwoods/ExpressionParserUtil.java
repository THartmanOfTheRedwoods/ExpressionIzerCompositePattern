package edu.redwoods;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import edu.redwoods.parser.ExprLexer;
import edu.redwoods.parser.ExprParser;

import java.util.Scanner;

public class ExpressionParserUtil {

    public static Expression parseExpression(String expressionText) {
        // 1. Convert the input string into a character stream for ANTLR
        CharStream input = CharStreams.fromString(expressionText);

        // 2. Create a lexer from the character stream
        ExprLexer lexer = new ExprLexer(input);

        // 3. Create a stream of tokens from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // 4. Create the parser from the token stream
        edu.redwoods.parser.ExprParser parser = new ExprParser(tokens);

        // 5. Start parsing from the 'expr' rule. This returns a ParseTree.
        //ParseTree tree = parser.expr();

        // 6. Use ANTLR's built-in visitor to walk the tree.
        // The `expr()` method from the parser returns a special context object
        // that already contains the `value` field we defined in the grammar.
        // The type of `parser.expr()` is `ExprParser.ExprContext`.
        ExprParser.ExprContext context = parser.expr();
        return context.value; // This is your edu.redwoods.Expression object, built by the grammar!
    }

    public static void main(String[] args) {
        String input;
        Scanner s = new Scanner(System.in);
        String prompt = "Enter a mathematical expression to simplify ('Q' to Quit): ";
        System.out.print(prompt);
        while(!(input = s.nextLine().strip()).equalsIgnoreCase("Q")) {
            Expression result = parseExpression(input);
            System.out.println("Parsed: " + result);
            // Now you can simplify it using your existing rules
            Expression simplified = result.simplify();
            System.out.println("Simplified: " + simplified);
            // Start prompt over
            System.out.print(prompt);
        }
    }
}