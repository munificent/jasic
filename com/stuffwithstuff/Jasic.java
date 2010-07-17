package com.stuffwithstuff;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * This defines a single class that contains an entire interpreter for a
 * language very similar to the original BASIC. Everything is here (albeit in
 * very simplified form): tokenizing, parsing, and interpretation.
 * 
 * Comments start with ' and proceed to the end of the line:
 * 
 *     print "hi there" ' this is a comment
 * 
 * Numbers and strings are supported. Strings should be in "double quotes", and
 * only positive integers can be parsed (though numbers are double internally).
 * 
 * Lines can contain statements or labels. A label starts with : followed by a
 * name, like:
 * 
 *     :foo
 * 
 * 
 * The following statements are supported:
 * 
 * <name> = <expression>
 *     Evaluates the expression and assigns the result to the given named 
 *     variable. All variables are globally scoped.
 *     
 *     pi = (314159 / 10000)
 *     
 * print <expression>
 *     Evaluates the expression and prints the result.
 * 
 *     print "hello, " + "world"
 *     
 * goto <label>
 *     Jumps to the statement after the label with the given name.
 * 
 *     goto loop
 * 
 * if <expression> then <label>
 *     Evaluates the expression. If it evaluates to a non-zero number, then
 *     jumps to the statement after the given label.
 * 
 *     if a < b then dosomething
 * 
 * 
 * The following expressions are supported:
 * 
 * <expression> = <expression>
 *     Evaluates to 1 if the two expressions are equal, 0 otherwise.
 * 
 * <expression> + <expression>
 *     If the left-hand expression is a number, then adds the two expressions,
 *     otherwise concatenates the two strings.
 * 
 * <expression> - <expression>
 * <expression> * <expression>
 * <expression> / <expression>
 * <expression> < <expression>
 * <expression> > <expression>
 *     You can figure it out.
 * 
 * All binary operators have the same precedence. Sorry, I had to cut corners
 * somewhere.
 * 
 * @author Bob Nystrom
 */
public class Jasic {

    /**
     * Runs the interpreter as a command-line app. Takes one argument: a path
     * to a script file to load and run. The script should contain one
     * statement per line, and *must* end with a trailing newline.
     * 
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: jasic <script>");
            System.out.println("Where <script> is a relative path to a .jas script to run.");
            return;
        }
        
        String contents = readFile(args[0]);
        
        Jasic jasic = new Jasic();
        jasic.interpret(contents);
    }
    
    public Jasic() {
        variables = new HashMap<String, Value>();
        labels = new HashMap<String, Integer>();
    }
    
    // Tokenizing (lexing) -----------------------------------------------------
    //
    // This phase takes a script as a string of characters and chunks it into a
    // sequence of tokens. Each token is a meaningful unit of program, like a
    // variable name, a number, a string, or an operator.
    
    private enum TokenType {
        WORD, NUMBER, STRING, LABEL, LINE,
        EQUALS, OPERATOR, LEFT_PAREN, RIGHT_PAREN, EOF
    }
    private static class Token {
        public Token(String text, TokenType type) {
            this.text = text;
            this.type = type;
        }
        
        public final String text;
        public final TokenType type;
    }
    
    private enum TokenizeState {
        DEFAULT, WORD, NUMBER, STRING, LABEL, COMMENT
    }
    
    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<Token>();
        
        String token = "";
        TokenizeState state = TokenizeState.DEFAULT;
        
        String charTokens = "\n=+-*/<>()";
        TokenType[] tokenTypes = { TokenType.LINE, TokenType.EQUALS,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN
        };
        
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (state) {
            case DEFAULT:
                if (charTokens.indexOf(c) != -1) {
                    tokens.add(new Token(Character.toString(c),
                        tokenTypes[charTokens.indexOf(c)]));
                } else if (Character.isLetter(c)) {
                    token += c;
                    state = TokenizeState.WORD;
                } else if (Character.isDigit(c)) {
                    token += c;
                    state = TokenizeState.NUMBER;
                } else if (c == '"') {
                    state = TokenizeState.STRING;
                } else if (c == ':') {
                    state = TokenizeState.LABEL;
                } else if (c == '\'') {
                    state = TokenizeState.COMMENT;
                }
                break;
                
            case WORD:
                if (Character.isLetterOrDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.WORD));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--; // rollback to reprocess this character
                }
                break;
                
            case NUMBER:
                if (Character.isDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.NUMBER));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--; // rollback to reprocess this character
                }
                break;
                
            case STRING:
                if (c == '"') {
                    tokens.add(new Token(token, TokenType.STRING));
                    token = "";
                    state = TokenizeState.DEFAULT;
                } else {
                    token += c;
                }
                break;
                
            case LABEL:
                if (c == '\n') {
                    tokens.add(new Token(token.trim(), TokenType.LABEL));
                    token = "";
                    state = TokenizeState.DEFAULT;
                } else {
                    token += c;
                }
                break;
                
            case COMMENT:
                if (c == '\n') {
                    state = TokenizeState.DEFAULT;
                }
                break;
            }
        }
        
        tokens.add(new Token("", TokenType.EOF));
        return tokens;
    }
    
    // Parsing -----------------------------------------------------------------
    //
    // This phase takes in a sequence of tokens and generates an abstract
    // syntax tree. This is the nested data structure that represents the
    // series of statements, and the expressions (which can nest arbitrarily
    // deeply) that they evaluate.
    //
    // As a side-effect, this phase also stores off the line numbers for each
    // label in the program. It's a bit gross, but it works.
    
    private class Parser {
        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            position = 0;
        }
        
        // Grammar:
        
        private List<Statement> parse(Map<String, Integer> labels) {
            List<Statement> statements = new ArrayList<Statement>();
            
            while (true) {
                // ignore empty lines
                while (match(TokenType.LINE));
                
                if (lookAhead(TokenType.LABEL)) {
                    // mark the index of the statement after the label
                    labels.put(consume().text, statements.size());
                } else if (lookAhead(TokenType.WORD, TokenType.EQUALS)) {
                    String name = consume().text;
                    consume(); // =
                    Expression value = expression();
                    statements.add(new AssignStatement(name, value));
                } else if (match("print")) {
                    statements.add(new PrintStatement(expression()));
                } else if (match("goto")) {
                    statements.add(new GotoStatement(consume().text));
                } else if (match("if")) {
                    Expression condition = expression();
                    consume("then");
                    String label = consume(TokenType.WORD).text;
                    statements.add(new IfThenStatement(condition, label));
                } else break; // unexpected token, just bail
            }
            
            return statements;
        }
        
        private Expression expression() {
            return operator();
        }
        
        private Expression operator() {
            Expression expression = atomic();
            
            // all binary operators have the same precedence and are
            // left-associative
            while (lookAhead(TokenType.OPERATOR) ||
                   lookAhead(TokenType.EQUALS)) {
                char operator = consume().text.charAt(0);
                Expression right = atomic();
                expression = new OperatorExpression(expression, operator, right);
            }
            
            return expression;
        }
        
        private Expression atomic() {
            if (lookAhead(TokenType.WORD)) {
                return new VariableExpression(consume().text);
            } else if (lookAhead(TokenType.NUMBER)) {
                return new NumberValue(Double.parseDouble(consume().text));
            } else if (lookAhead(TokenType.STRING)) {
                return new StringValue(consume().text);
            } else if (match(TokenType.LEFT_PAREN)) {
                Expression expression = expression();
                consume(TokenType.RIGHT_PAREN);
                return expression;
            }
            throw new Error("Couldn't parse :(");
        }
        
        // Basic parser functionality:
        
        private boolean match(String word) {
            if (!lookAhead(word)) return false;
            consume();
            return true;
        }
        
        private boolean match(TokenType type) {
            if (!lookAhead(type)) return false;
            consume();
            return true;
        }
        
        private Token consume(String word) {
            if (!lookAhead(word)) throw new Error("Expected " + word + ".");
            return consume();
        }
        
        private Token consume(TokenType type) {
            if (!lookAhead(type)) throw new Error("Expected " + type + ".");
            return consume();
        }
        
        private Token consume() {
            return tokens.get(position++);
        }
        
        private Token get(int offset) {
            if (position + offset >= tokens.size()) {
                return new Token("", TokenType.EOF);
            }
            return tokens.get(position + offset);
        }
        
        private boolean lookAhead(String word) {
            return (get(0).type == TokenType.WORD) &&
                   (get(0).text.equals(word));
        }
        
        private boolean lookAhead(TokenType type) {
            return get(0).type == type;
        }
        
        private boolean lookAhead(TokenType type1, TokenType type2) {
            return (get(0).type == type1) && (get(1).type == type2);
        }
        
        private final List<Token> tokens;
        private int position;
    }
    
    // AST ---------------------------------------------------------------------
    //
    // These classes define the syntax tree data structures. This is the
    // internal representation for a chunk of code. Unlike most real compilers
    // or interpreters, the logic to evaluate the code is baked directly into
    // these classes. For expressions, it's in the evaluate() method, and for
    // statements, it's in execute().

    public interface Statement {
        void execute();
    }
    
    public class PrintStatement implements Statement {
        public PrintStatement(Expression expression) {
            this.expression = expression;
        }
        
        @Override public void execute() {
            System.out.println(expression.evaluate().toString());
        }

        private final Expression expression;
    }

    public class AssignStatement implements Statement {
        public AssignStatement(String name, Expression value) {
            this.name = name;
            this.value = value;
        }
        
        @Override public void execute() {
            variables.put(name, value.evaluate());
        }

        private final String name;
        private final Expression value;
    }
    
    public class GotoStatement implements Statement {
        public GotoStatement(String label) {
            this.label = label;
        }
        
        @Override public void execute() {
            if (labels.containsKey(label)) {
                currentStatement = labels.get(label).intValue();
            }
        }

        private final String label;
    }
    
    public class IfThenStatement implements Statement {
        public IfThenStatement(Expression condition, String label) {
            this.condition = condition;
            this.label = label;
        }
        
        @Override public void execute() {
            if (labels.containsKey(label)) {
                double value = condition.evaluate().toNumber();
                if (value != 0) {
                    currentStatement = labels.get(label).intValue();
                }
            }
        }

        private final Expression condition;
        private final String label;
    }
    
    public interface Expression {
        Value evaluate();
    }
    
    public class VariableExpression implements Expression {
        public VariableExpression(String name) {
            this.name = name;
        }
        
        public Value evaluate() {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            return new NumberValue(0);
        }
        
        private final String name;
    }
    
    public class OperatorExpression implements Expression {
        public OperatorExpression(Expression left, char operator,
                                  Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
        
        public Value evaluate() {
            Value leftVal = left.evaluate();
            Value rightVal = right.evaluate();
            
            switch (operator) {
            case '=':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() ==
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue(leftVal.toString().equals(
                                           rightVal.toString()) ? 1 : 0);
                }
            case '+':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue(leftVal.toNumber() +
                                           rightVal.toNumber());
                } else {
                    return new StringValue(leftVal.toString() +
                            rightVal.toString());
                }
            case '-':
                return new NumberValue(leftVal.toNumber() -
                        rightVal.toNumber());
            case '*':
                return new NumberValue(leftVal.toNumber() *
                        rightVal.toNumber());
            case '/':
                return new NumberValue(leftVal.toNumber() /
                        rightVal.toNumber());
            case '<':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() <
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue((leftVal.toString().compareTo(
                                           rightVal.toString()) < 0) ? 1 : 0);
                }
            case '>':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() >
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue((leftVal.toString().compareTo(
                            rightVal.toString()) > 0) ? 1 : 0);
                }
            }
            throw new Error("Unknown operator.");
        }
        
        private final Expression left;
        private final char operator;
        private final Expression right;
    }
    
    // Value Types -------------------------------------------------------------
    //
    // These classes define the basic kinds of values the interpreter can
    // manipulate. They are what it stores in variables, and what expressions
    // evaluate to.
    //
    // To save a little space, the value types here also do double-duty as
    // expression literals in the AST.
    
    public interface Value {
        String toString();
        double toNumber();
    }
    
    public class NumberValue implements Value, Expression {
        public NumberValue(double value) {
            this.value = value;
        }
        
        @Override public String toString() { return Double.toString(value); }
        @Override public double toNumber() { return value; }
        @Override public Value evaluate() { return this; }

        private final double value;
    }
    
    public class StringValue implements Value, Expression {
        public StringValue(String value) {
            this.value = value;
        }
        
        @Override public String toString() { return value; }
        @Override public double toNumber() { return Double.parseDouble(value); }
        @Override public Value evaluate() { return this; }

        private final String value;
    }

    // Interpreter -------------------------------------------------------------
    //
    // This is where the magic happens. This runs the code through the parsing
    // pipeline to generate the AST. Then it executes each statement. It keeps
    // track of the current line in a member variable that the statement objects
    // have access to. This lets "goto" and "if then" do flow control by simply
    // setting the index of the current statement.
    //
    // In an interpreter that didn't mix the interpretation logic in with the
    // AST node classes, this would be doing a lot more work.
    
    public void interpret(String source) {
        // tokenize
        List<Token> tokens = tokenize(source);
        
        // parse
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parse(labels);
        
        // interpret
        currentStatement = 0;
        while (currentStatement < statements.size()) {
            int thisStatement = currentStatement;
            currentStatement++;
            statements.get(thisStatement).execute();
        }
    }
    
    private final Map<String, Value> variables;
    private final Map<String, Integer> labels;
    
    private int currentStatement;
    
    // Utility stuff -----------------------------------------------------------
    
    /**
     * Reads the file from the given path and returns its contents as a single
     * string.
     * 
     * @param path Path to the text file to read.
     * @return The contents of the file or null if the load failed.
     * @throws IOException
     */
    private static String readFile(String path) {
        try {
            FileInputStream stream = new FileInputStream(path);
            
            try {
                InputStreamReader input = new InputStreamReader(stream,
                    Charset.defaultCharset());
                Reader reader = new BufferedReader(input);
                
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                
                while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                    builder.append(buffer, 0, read);
                }
                
                return builder.toString();
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            return null;
        }
    }
}
