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
 * very simplified form): tokenizing, parsing, and interpretation. The file is
 * organized in phases, with each appearing roughly in the order that they
 * occur when a program is run. You should be able to read this top-down to walk
 * through the entire process of loading and running a program.
 * 
 * Jasic language syntax
 * ---------------------
 * 
 * Comments start with ' and proceed to the end of the line:
 * 
 *     print "hi there" ' this is a comment
 * 
 * Numbers and strings are supported. Strings should be in "double quotes", and
 * only positive integers can be parsed (though numbers are double internally).
 * 
 * Variables are identified by name which must start with a letter and can
 * contain letters or numbers. Case is significant for names and keywords.
 * 
 * Each statement is on its own line. Optionally, a line may have a label before
 * the statement. A label is a name that ends with a colon:
 * 
 *     foo:
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
 * <name>
 *     A name in an expression simply returns the value of the variable with
 *     that name. If the variable was never set, it defaults to 0.
 * 
 * All binary operators have the same precedence. Sorry, I had to cut corners
 * somewhere.
 * 
 * To keep things simple, I've omitted some stuff or hacked things a bit. When
 * possible, I'll leave a "HACK" note there explaining what and why. If you
 * make your own interpreter, you'll want to address those.
 * 
 * @author Bob Nystrom
 */
public class Jasic {

    /**
     * Runs the interpreter as a command-line app. Takes one argument: a path
     * to a script file to load and run. The script should contain one
     * statement per line.
     * 
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        // Just show the usage and quit if a script wasn't provided.
        if (args.length != 1) {
            System.out.println("Usage: jasic <script>");
            System.out.println("Where <script> is a relative path to a .jas script to run.");
            return;
        }
        
        // Read the file.
        String contents = readFile(args[0]);
        
        // Run it.
        Jasic jasic = new Jasic();
        jasic.interpret(contents);
    }
    
    // Tokenizing (lexing) -----------------------------------------------------
    
    /**
     * This function takes a script as a string of characters and chunks it into
     * a sequence of tokens. Each token is a meaningful unit of program, like a
     * variable name, a number, a string, or an operator.
     */
    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<Token>();
        
        String token = "";
        TokenizeState state = TokenizeState.DEFAULT;
        
        // Many tokens are a single character, like operators and ().
        String charTokens = "\n=+-*/<>()";
        TokenType[] tokenTypes = { TokenType.LINE, TokenType.EQUALS,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN
        };
        
        // Scan through the code one character at a time, building up the list
        // of tokens.
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
                } else if (c == '\'') {
                    state = TokenizeState.COMMENT;
                }
                break;
                
            case WORD:
                if (Character.isLetterOrDigit(c)) {
                    token += c;
                } else if (c == ':') {
                    tokens.add(new Token(token, TokenType.LABEL));
                    token = "";
                    state = TokenizeState.DEFAULT;
                } else {
                    tokens.add(new Token(token, TokenType.WORD));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--; // Reprocess this character in the default state.
                }
                break;
                
            case NUMBER:
                // HACK: Negative numbers and floating points aren't supported.
                // To get a negative number, just do 0 - <your number>.
                // To get a floating point, divide.
                if (Character.isDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.NUMBER));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--; // Reprocess this character in the default state.
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
                
            case COMMENT:
                if (c == '\n') {
                    state = TokenizeState.DEFAULT;
                }
                break;
            }
        }
        
        // HACK: Silently ignore any in-progress token when we run out of
        // characters. This means that, for example, if a script has a string
        // that's missing the closing ", it will just ditch it.
        
        return tokens;
    }

    // Token data --------------------------------------------------------------

    /**
     * This defines the different kinds of tokens or meaningful chunks of code
     * that the parser knows how to consume. These let us distinguish, for
     * example, between a string "foo" and a variable named "foo".
     * 
     * HACK: A typical tokenizer would actually have unique token types for
     * each keyword (print, goto, etc.) so that the parser doesn't have to look
     * at the names, but Jasic is a little more crude.
     */
    private enum TokenType {
        WORD, NUMBER, STRING, LABEL, LINE,
        EQUALS, OPERATOR, LEFT_PAREN, RIGHT_PAREN, EOF
    }
    
    /**
     * This is a single meaningful chunk of code. It is created by the tokenizer
     * and consumed by the parser.
     */
    private static class Token {
        public Token(String text, TokenType type) {
            this.text = text;
            this.type = type;
        }
        
        public final String text;
        public final TokenType type;
    }
    
    /**
     * This defines the different states the tokenizer can be in while it's
     * scanning through the source code. Tokenizers are state machines, which
     * means the only data they need to store is where they are in the source
     * code and this one "state" or mode value.
     * 
     * One of the main differences between tokenizing and parsing is this
     * regularity. Because the tokenizer stores only this one state value, it
     * can't handle nesting (which would require also storing a number to
     * identify how deeply nested you are). The parser is able to handle that.
     */
    private enum TokenizeState {
        DEFAULT, WORD, NUMBER, STRING, COMMENT
    }

    // Parsing -----------------------------------------------------------------

    /**
     * This defines the Jasic parser. The parser takes in a sequence of tokens
     * and generates an abstract syntax tree. This is the nested data structure
     * that represents the series of statements, and the expressions (which can
     * nest arbitrarily deeply) that they evaluate. In technical terms, what we
     * have is a recursive descent parser, the simplest kind to hand-write.
     *
     * As a side-effect, this phase also stores off the line numbers for each
     * label in the program. It's a bit gross, but it works.
     */
    private class Parser {
        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            position = 0;
        }
        
        // Grammar:
        
        /**
         * The top-level function to start parsing. This will keep consuming
         * tokens and routing to the other parse functions for the different
         * grammar syntax until we run out of code to parse.
         * 
         * @param  labels   A map of label names to statement indexes.
         *                  The parser will fill this in as it scans through the
         *                  code.
         * @return          The list of parsed statements.
         */
        public List<Statement> parse(Map<String, Integer> labels) {
            List<Statement> statements = new ArrayList<Statement>();
            
            while (true) {
                // Ignore empty lines.
                while (match(TokenType.LINE));
                
                if (match(TokenType.LABEL)) {
                    // Mark the index of the statement after the label.
                    labels.put(last(1).text, statements.size());
                } else if (match(TokenType.WORD, TokenType.EQUALS)) {
                    String name = last(2).text;
                    Expression value = expression();
                    statements.add(new AssignStatement(name, value));
                } else if (match("print")) {
                    statements.add(new PrintStatement(expression()));
                } else if (match("goto")) {
                    statements.add(new GotoStatement(
                        consume(TokenType.WORD).text));
                } else if (match("if")) {
                    Expression condition = expression();
                    consume("then");
                    String label = consume(TokenType.WORD).text;
                    statements.add(new IfThenStatement(condition, label));
                } else break; // Unexpected token (likely EOF), so end.
            }
            
            return statements;
        }
        
        // The following functions each represent one grammatical part of the
        // language. If this parsed English, these functions would be named like
        // noun() and verb().
        
        /**
         * Parses a single expression. Recursive descent parsers start with the
         * lowest-precedent term and moves towards higher precedence. For Jasic,
         * binary operators (+, -, etc.) are the lowest.
         * 
         * @return The parsed expression.
         */
        private Expression expression() {
            return operator();
        }
        
        /**
         * Parses a series of binary operator expressions into a single
         * expression. In Jasic, all operators have the same predecence and
         * associate left-to-right. That means it will interpret:
         *    1 + 2 * 3 - 4 / 5
         * like:
         *    ((((1 + 2) * 3) - 4) / 5)
         * 
         * It works by building the expression tree one at a time. So, given
         * this code: 1 + 2 * 3, this will:
         * 
         * 1. Parse (1) as an atomic expression.
         * 2. See the (+) and start a new operator expression.
         * 3. Parse (2) as an atomic expression.
         * 4. Build a (1 + 2) expression and replace (1) with it.
         * 5. See the (*) and start a new operator expression.
         * 6. Parse (3) as an atomic expression.
         * 7. Build a ((1 + 2) * 3) expression and replace (1 + 2) with it.
         * 8. Return the last expression built.
         * 
         * @return The parsed expression.
         */
        private Expression operator() {
            Expression expression = atomic();
            
            // Keep building operator expressions as long as we have operators.
            while (match(TokenType.OPERATOR) ||
                   match(TokenType.EQUALS)) {
                char operator = last(1).text.charAt(0);
                Expression right = atomic();
                expression = new OperatorExpression(expression, operator, right);
            }
            
            return expression;
        }
        
        /**
         * Parses an "atomic" expression. This is the highest level of
         * precedence and contains single literal tokens like 123 and "foo", as
         * well as parenthesized expressions.
         * 
         * @return The parsed expression.
         */
        private Expression atomic() {
            if (match(TokenType.WORD)) {
                // A word is a reference to a variable.
                return new VariableExpression(last(1).text);
            } else if (match(TokenType.NUMBER)) {
                return new NumberValue(Double.parseDouble(last(1).text));
            } else if (match(TokenType.STRING)) {
                return new StringValue(last(1).text);
            } else if (match(TokenType.LEFT_PAREN)) {
                // The contents of a parenthesized expression can be any
                // expression. This lets us "restart" the precedence cascade
                // so that you can have a lower precedence expression inside
                // the parentheses.
                Expression expression = expression();
                consume(TokenType.RIGHT_PAREN);
                return expression;
            }
            throw new Error("Couldn't parse :(");
        }
        
        // The following functions are the core low-level operations that the
        // grammar parser is built in terms of. They basically match and consume
        // tokens in the token stream.
        
        /**
         * Consumes the next two tokens if they are the given type (in order).
         * Consumes no tokens if either check fais.
         * 
         * @param  type1 Expected type of the next token.
         * @param  type2 Expected type of the subsequent token.
         * @return       True if tokens were consumed.
         */
        private boolean match(TokenType type1, TokenType type2) {
            if (get(0).type != type1) return false;
            if (get(1).type != type2) return false;
            position += 2;
            return true;
        }
        
        /**
         * Consumes the next token if it's the given type.
         * 
         * @param  type  Expected type of the next token.
         * @return       True if the token was consumed.
         */
        private boolean match(TokenType type) {
            if (get(0).type != type) return false;
            position++;
            return true;
        }
        
        /**
         * Consumes the next token if it's a word token with the given name.
         * 
         * @param  name  Expected name of the next word token.
         * @return       True if the token was consumed.
         */
        private boolean match(String name) {
            if (get(0).type != TokenType.WORD) return false;
            if (!get(0).text.equals(name)) return false;
            position++;
            return true;
        }
        
        /**
         * Consumes the next token if it's the given type. If not, throws an
         * exception. This is for cases where the parser demands a token of a
         * certain type in a certain position, for example a matching ) after
         * an opening (.
         * 
         * @param  type  Expected type of the next token.
         * @return       The consumed token.
         */
        private Token consume(TokenType type) {
            if (get(0).type != type) throw new Error("Expected " + type + ".");
            return tokens.get(position++);
        }
        
        /**
         * Consumes the next token if it's a word with the given name. If not,
         * throws an exception.
         * 
         * @param  name  Expected name of the next word token.
         * @return       The consumed token.
         */
        private Token consume(String name) {
            if (!match(name)) throw new Error("Expected " + name + ".");
            return last(1);
        }

        /**
         * Gets a previously consumed token, indexing backwards. last(1) will
         * be the token just consumed, last(2) the one before that, etc.
         * 
         * @param  offset How far back in the token stream to look.
         * @return        The consumed token.
         */
        private Token last(int offset) {
            return tokens.get(position - offset);
        }
        
        /**
         * Gets an unconsumed token, indexing forward. get(0) will be the next
         * token to be consumed, get(1) the one after that, etc.
         * 
         * @param  offset How far forward in the token stream to look.
         * @return        The yet-to-be-consumed token.
         */
        private Token get(int offset) {
            if (position + offset >= tokens.size()) {
                return new Token("", TokenType.EOF);
            }
            return tokens.get(position + offset);
        }
        
        private final List<Token> tokens;
        private int position;
    }
    
    // Abstract syntax tree (AST) ----------------------------------------------
    //
    // These classes define the syntax tree data structures. This is the
    // internal representation for a chunk of code. Unlike most real compilers
    // or interpreters, the logic to evaluate the code is baked directly into
    // these classes. For expressions, it's in the evaluate() method, and for
    // statements, it's in execute().

    public interface Statement {
        void execute();
    }

    public interface Expression {
        Value evaluate();
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
    
    public Jasic() {
        variables = new HashMap<String, Value>();
        labels = new HashMap<String, Integer>();
    }

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
                
                // HACK: The parser expects every statement to end in a newline,
                // even the very last one, so we'll just tack one on here in
                // case the file doesn't have one.
                builder.append("\n");
                
                return builder.toString();
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            return null;
        }
    }
}
