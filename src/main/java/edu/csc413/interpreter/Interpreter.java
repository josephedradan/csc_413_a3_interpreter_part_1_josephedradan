package edu.csc413.interpreter;

import edu.csc413.interpreter.statement.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Interpreter {
    // Assorted regular expression patterns for statements we'll be parsing.
    private static final Pattern PRINT_PATTERN = Pattern.compile("^print\\((.+)\\)$");
    private static final Pattern ASSIGN_PATTERN = Pattern.compile("^(.+) = (.+)$");

    private static final Pattern IF_PATTERN = Pattern.compile("^if (.+):$");

    // The list of Statements created from parsing the Strings comprising the program.
    private final List<Statement> statements = new ArrayList<>();

    /**
     * Creates the Interpreter object from an array of Strings representing a program to be run. The constructor will
     * parse the lines into executable Statements, which can be invoked with the runProgram method.
     */
    public Interpreter(List<String> program) {

        // Get lines that are code
        Queue<String> lines =
                program.stream()
                        .filter(line -> !line.trim().isEmpty())
                        .filter(line -> !line.startsWith("#"))
                        .collect(Collectors.toCollection(LinkedList::new));

        // Make parser
        Parser parser = new Parser();

        while (!lines.isEmpty()) {

            // Statements are made from the parser
            Statement statement = parseStatement(lines, parser, 0);
            statements.add(statement);
        }
    }

    // Parse a single Statement from the front of the provided deque. Note that with block statements (if statements,
    // while loops), a single Statement may involve multiple lines with multiple contained statements.
    // Basically a code line handler
    private Statement parseStatement(Queue<String> lines, Parser parser, int indentationLevel) {
        // Pop head string
        String line = lines.remove();

        // Check if indentation level matches that of what is given
        if (getIndentationLevel(line) != indentationLevel) {
            throw new RuntimeException("Line with unexpected indentation: " + line.trim());
        }

        // Strip the spaces from the line
        line = line.trim();

        // ------ Multi-line statements ------

        // Check if line is if statement
        Matcher ifMatcher = IF_PATTERN.matcher(line);
        if (ifMatcher.matches()) {
            String conditionAsString = ifMatcher.group(1);

            // *** Get the body statements
            List<Statement> bodyStatements = parseBodyStatements(lines, parser, indentationLevel);

            // Create if statement object
            return parser.createIfStatement(conditionAsString, bodyStatements);
        }

        // ----- Single line statements -----
        // Check if line is print statement
        Matcher printMatcher = PRINT_PATTERN.matcher(line);
        if (printMatcher.matches()) {
            String expressionAsString = printMatcher.group(1).trim();
            return parser.createPrintStatement(expressionAsString);
        }

        // Check if line is assignment statement
        Matcher assignMatcher = ASSIGN_PATTERN.matcher(line);
        if (assignMatcher.matches()) {
            String variableName = assignMatcher.group(1).trim();
            String expressionAsString = assignMatcher.group(2).trim();
            return parser.createAssignStatement(variableName, expressionAsString);
        }

        throw new RuntimeException("Unrecognized statement: " + line);
    }

    // parseBodyStatements is called when parsing any statement type with multiple lines. It will keep converting lines
    // into Statements and collecting them in a List until it encounters a line with an indentation level to the left of
    // indentationLevel (i.e. unindented from what was expected).
    private List<Statement> parseBodyStatements(Queue<String> lines, Parser parser, int indentationLevel) {

        // No body
        if (lines.isEmpty()) {
            throw new RuntimeException("Block statement (if, for, while, etc.) found with an empty body.");
        }

        // Get indent level of head
        int blockIndentationLevel = getIndentationLevel(lines.element());

        // block indentation level must be bigger than the given indentation level
        if (blockIndentationLevel <= indentationLevel) {
            throw new RuntimeException(
                    "Expected body of block statement to be further indented, but was not: " + lines.element().trim());
        }

        // Make List of statements
        List<Statement> blockStatements = new ArrayList<>();

        // Loop and add statements...
        while (!lines.isEmpty()) {

            // We peek at the next line before attempting to parse it, in case it signals the end of this block.
            String nextLine = lines.element();
            int nextIndentationLevel = getIndentationLevel(nextLine);

            // If the next line is unindented, this block is done.
            if (nextIndentationLevel <= indentationLevel) {
                return blockStatements;
            }
            // Otherwise, the next line must match the block indentation level.
            if (nextIndentationLevel != blockIndentationLevel) {
                throw new RuntimeException("Line with unexpected indentation: " + nextLine.trim());
            }

            // If the indentation is valid, we parse the next line as a normal Statement. Note that the next line can
            // itself be a multi-line statement, like an if statement or a while loop.
            blockStatements.add(parseStatement(lines, parser, blockIndentationLevel));
        }

        // If we ran out of lines, the block being parsed finishes at the end of the entire program.
        return blockStatements;
    }

    // Returns the index of the first character in the line that isn't a space character. This will determine how far
    // the line is indented.
    private int getIndentationLevel(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ' ') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Run the parsed program Statements.
     */
    public void runProgram() {
        ProgramState programState = new ProgramState();
        for (Statement statement : statements) {
            statement.run(programState);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Must provide the program file name to run.");
        }
        if (args.length > 1) {
            throw new RuntimeException("Only one argument expected (program file name).");
        }

        ArrayList<String> programLines = new ArrayList<>();
        try {
            String programFileName = args[0];
            BufferedReader bufferedReader = new BufferedReader(new FileReader(programFileName));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                programLines.add(line);
            }
            bufferedReader.close();
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        Interpreter interpreter = new Interpreter(programLines);
        interpreter.runProgram();
    }
}
