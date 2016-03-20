package net.tidej.expressionparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * A simple configurable expression parser.
 */
public class ExpressionParser<T> {

  /**
   * Called by the expression parser, needs to be implemented by the user. May process
   * the expressions directly or build a tree. Abstract class instead of an interface
   * to avoid the need to implement methods that never trigger for a given syntax.
   */
  public static class Processor<T> {
    /** Called when an infix operator with the given name is parsed. */
    public T infixOperator(String name, T left, T right) {
      throw new UnsupportedOperationException("infixOperator(" + name + ", " + left + ", " + right + ')');
    }

    /** Called when an implicit operator is parsed. */
    public T implicitOperator(boolean strong, T left, T right) {
      throw new UnsupportedOperationException("implicitOperator(" + left + ", " + right + ')');
    }

    /** Called when a prefix operator with the given name is parsed. */
    public T prefixOperator(String name, T argument) {
      throw new UnsupportedOperationException("prefixOperator(" + name + ", " + argument + ')');
    }

    /**
     * Called when a symbol that is neither an operator nor an identifier is parsed
     * (e.g. the empty set symbol).
     */
    public T primarySymbol(String name) {
      throw new UnsupportedOperationException("primarySymbol(" + name + ")");
    }

    /** Called when a suffix operator with the given name is parsed. */
    public T suffixOperator(String name, T argument) {
      throw new UnsupportedOperationException("suffixOperator(" + name + ", " + argument + ')');
    }

    /** Called when the given number literal is parsed. */
    public T numberLiteral(String value) {
      throw new UnsupportedOperationException("numberLiteral(" + value + ")");
    }

    /** 
     * Called when the given (quoted) string literal is parsed.
     * The string is handed in in its original quoted form; use ExpressionParser.unquote()
     * to unquote and unescape the string.
     */
    public T stringLiteral(String value) {
      throw new UnsupportedOperationException("stringLiteral(" + value + ')');
    }

    /** Called when the given identifier is parsed. */
    public T identifier(String name) {
      throw new UnsupportedOperationException("identifier(" + name + ')');
    }

    /** Called when a group with the given opening bracket and elements is parsed. */
    public T group(String paren, List<T> elements) {
      throw new UnsupportedOperationException("group(" + paren + ", " + elements + ')');
    };

    /**
     * Called when a bracked registered for calls following an identifier is parsed.
     * Useful to avoid apply(indentifier(identifier), bracket, arguments) in simple cases,
     * see calculator example.
     */
    public T call(String identifier, String bracket, List<T> arguments) {
      throw new UnsupportedOperationException(
          "call(" + identifier + ", " + bracket + ", " + arguments + ")");
    }

    /** Called when an argument list with the given base, opening bracket and elements is parsed. */
    public T apply(T base, String bracket, List<T> arguments) {
      throw new UnsupportedOperationException(
          "apply(" + base+ ", " + bracket + ", " + arguments + ")");
    }
  }

  public enum OperatorType {
    INFIX, INFIX_RTL, PREFIX, SUFFIX
  }

  private final HashSet<String> primarySymbols = new HashSet<>();
  private final HashMap<String, String[]> calls = new HashMap<>();
  private final HashMap<String, Boolean> allSymbols = new HashMap<>();

  private final ArrayList<Operators> precedenceList = new ArrayList<>();
  private final Processor<T> processor;
  private int strongImplicitOperatorPrecedence = -1;
  private int weakImplicitOperatorPrecedence = -1;

  public static String unquote(String s) {
    StringBuilder sb = new StringBuilder();
    int len = s.length() - 1;
    for (int i = 1; i < len; i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        c = s.charAt(++i);
        switch(c) {
          case 'b': sb.append('\b'); break;
          case 'f': sb.append('\f'); break;
          case 'n': sb.append('\n'); break;
          case 't': sb.append('\t'); break;
          case 'r': sb.append('\r'); break;
          default:
            sb.append(c);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public ExpressionParser(Processor<T> processor) {
    this.processor = processor;
  }

  /**
   * Adds "apply" brackets with the given precedence. Used for function calls or array element access.
   */
  public void addApplyBrackets(int precedence, String open, String separator, String close) {
    operators(precedence).apply.put(addSymbol(open, false),
        new String[] {addSymbol(separator, false), addSymbol(close, false)});
  }

  /**
   * Adds "call" brackets, parsed eagerly after identifiers.
   */
  public void addCallBrackets(String open, String separator, String close) {
    calls.put(addSymbol(open, true),  // Doesn't need protection b/c parsed eagerly
        new String[]{addSymbol(separator, false), addSymbol(close, false)});
  }

  /**
   * Adds grouping with the given precedence. If the separator is null, only a single element
   * will be permitted. If the separator is empty, whitespace will be sufficient for element
   * separation. Used for parsing lists or overriding the operator precedence (typically with
   * parens and a null separator).
   */
  public ExpressionParser<T> addGroupBrackets(
      int precedence, String open, String separator, String close) {
    operators(precedence).group.put(addSymbol(open, true),
        new String[] {addSymbol(separator, false), addSymbol(close, false)});
    return this;
  }

  public void addPrimarySymbols(String... names) {
    for (String name : names) {
      primarySymbols.add(addSymbol(name, false));
    }
  }

    /**
   * Add prefixOperator, infixOperator or postfix operators with the given precedence.
   */
  public void addOperators(OperatorType type, int precedence, String... names) {
    HashSet<String> set = operators(precedence).get(type);
    for (String name : names) {
      set.add(addSymbol(name, type == OperatorType.PREFIX));
    }
  }

  /**
   * Used to keep track of all registered operators / symbols and whether they may occur in
   * a non-prefixOperator position.
   */
  private String addSymbol(String symbol, boolean prefix) {
    if (symbol != null && (!allSymbols.containsKey(symbol) || !prefix)) {
      allSymbols.put(symbol, prefix);
    }
    return symbol;
  }

  private Operators operators(int precedence) {
    while (precedenceList.size() <= precedence) {
      precedenceList.add(new Operators());
    }
    return precedenceList.get(precedence);
  }

  public void setImplicitOperatorPrecedence(boolean strong, int precedence) {
    if (strong) {
      strongImplicitOperatorPrecedence = precedence;
    } else {
      weakImplicitOperatorPrecedence = precedence;
    }
    if (precedence > 0) {
      operators(precedence);
    }
  }

  /**
   * Returns all symbols registered via add...Operator and add...Bracket calls.
   * Useful for tokenizer construction.
   */
  public Iterable<String> getSymbols() {
    return allSymbols.keySet();
  }

  /**
   * Parse the given expression using a simple StreamTokenizer-based parser.
   * Leftover tokens will cause an exception.
   */
  public T parse(String expr) {
    Tokenizer tokenizer = new Tokenizer(new Scanner(expr), getSymbols());
    tokenizer.nextToken();
    T result = parse(tokenizer);
    if (tokenizer.currentType != Tokenizer.TokenType.EOF) {
      throw tokenizer.exception("Leftover input.", null);
    }
    return result;
  }

  /**
   * Parse an expression from the given tokenizer. Leftover tokens will be ignored and
   * may be handled by the caller.
   */
  public T parse(Tokenizer tokenizer) {
    return parseOperator(tokenizer, 0);
  }

  private T parseOperator(Tokenizer tokenizer, int precedence) {
    if (precedence >= precedenceList.size()) {
      return parsePrimary(tokenizer);
    }
    Operators operators = operators(precedence);

    // Prefix

    String candidate = tokenizer.currentValue;
    if (operators.get(OperatorType.PREFIX).contains(candidate)) {
      tokenizer.nextToken();
      return processor.prefixOperator(candidate, parseOperator(tokenizer, precedence));
    }
    if (operators.group.containsKey(candidate)) {
      tokenizer.nextToken();
      String[] grouping = operators.group.get(candidate);
      return processor.group(candidate, parseList(tokenizer, grouping[0], grouping[1]));
    }

    // Recursion
    T result = parseOperator(tokenizer, precedence + 1);

    // Infix (including implicit)

    candidate = tokenizer.currentValue;

    if (operators.get(OperatorType.INFIX_RTL).contains(candidate)) {
      tokenizer.nextToken();
      return processor.infixOperator(candidate, result, parseOperator(tokenizer, precedence));
    }

    // The complex implicit operator check is intended to prevent it from consuming
    // - EOF,
    // - unhandled, infix or suffix symbols (e.g. ';')
    // - identifiers registered as non-prefix symbols
    while (operators.get(OperatorType.INFIX).contains(candidate)
        || (precedence == (tokenizer.leadingWhitespace
            ? weakImplicitOperatorPrecedence : strongImplicitOperatorPrecedence)
          && !candidate.isEmpty()
          && (tokenizer.currentType != Tokenizer.TokenType.SYMBOL ||
            allSymbols.get(candidate) == Boolean.TRUE)
          && (tokenizer.currentType != Tokenizer.TokenType.IDENTIFIER ||
            allSymbols.get(candidate) != Boolean.FALSE))) {
      if (operators.get(OperatorType.INFIX).contains(candidate)) {
        tokenizer.nextToken();
        T right = parseOperator(tokenizer, precedence + 1);
        result = processor.infixOperator(candidate, result, right);
      } else {
        boolean strong = !tokenizer.leadingWhitespace;
        T right = parseOperator(tokenizer, precedence + 1);
        result = processor.implicitOperator(strong, result, right);
      }
      candidate = tokenizer.currentValue;
    }

    // Suffix

    while (operators.apply.containsKey(candidate)
        || operators.get(OperatorType.SUFFIX).contains(candidate)) {
      tokenizer.nextToken();
      if (operators.apply.containsKey(candidate)) {
        String[] apply = operators.apply.get(candidate);
        result = processor.apply(result, candidate, parseList(tokenizer, apply[0], apply[1]));
      } else {
        result = processor.suffixOperator(candidate, result);
      }
      candidate = tokenizer.currentValue;
    }
    return result;
  }

  // Precondition: Opening paren consumed
  // Postcondition: Closing paren consumed
  List<T> parseList(Tokenizer tokenizer, String separator, String close) {
    ArrayList<T> elements = new ArrayList<>();
    if (!tokenizer.currentValue.equals(close)) {
      while (true) {
        elements.add(parse(tokenizer));
        String op = tokenizer.currentValue;
        if (op.equals(close)) {
          break;
        }
        if (separator == null) {
          throw tokenizer.exception("Closing bracket expected: '" + close + "'.", null);
        }
        if (!separator.isEmpty()) {
          if (!op.equals(separator)) {
            throw tokenizer.exception("List separator '" + separator + "' or closing paren '"
                + close + " expected.", null);
          }
          tokenizer.nextToken();  // separator
        }
      }
    }
    tokenizer.nextToken();  // closing paren
    return elements;
  }

  T parsePrimary(Tokenizer tokenizer) {
    T result;
    switch (tokenizer.currentType) {
      case NUMBER:
        result = processor.numberLiteral(tokenizer.currentValue);
        tokenizer.nextToken();
        break;
      case IDENTIFIER:
        String identifier = tokenizer.currentValue;
        tokenizer.nextToken();
        if (calls.containsKey(tokenizer.currentValue)) {
          String openingBracket = tokenizer.currentValue;
          String[] call = calls.get(openingBracket);
          tokenizer.nextToken();
          result = processor.call(identifier, openingBracket, parseList(tokenizer, call[0], call[1]));
        } else {
          result = processor.identifier(identifier);
        }
        break;
      case STRING:
        result = processor.stringLiteral(tokenizer.currentValue);
        tokenizer.nextToken();
        break;
      case SYMBOL:
        if (primarySymbols.contains(tokenizer.currentValue)) {
          result = processor.primarySymbol(tokenizer.currentValue);
          tokenizer.nextToken();
          break;
        }  // Fall-through intended.
      default:
        throw tokenizer.exception("Unexpected token type.", null);
    }
    return result;
  }

  private static class Operators {
    HashSet<String>[] operators = new HashSet[]{
        new HashSet<>(), new HashSet<>(),
        new HashSet<>(), new HashSet<>()};
    HashMap<String, String[]> group = new HashMap<>();
    HashMap<String, String[]> apply = new HashMap<>();

    private HashSet<String> get(OperatorType type) {
      return operators[type.ordinal()];
    }
  }

  public static class ParsingException extends RuntimeException {
    final public int position;
    public ParsingException(String text, int position, Exception base) {
      super(text, base);
      this.position = position;
    }
  }

  /** 
   * A simple tokenizer utilizing java.util.Scanner.
   */
  public static class Tokenizer {
    public static final Pattern DEFAULT_NUMBER_PATTERN = Pattern.compile(
        "\\G\\s*\\d+(\\.\\d*)?([eE][+-]?\\d+)?");

    public static final Pattern DEFAULT_IDENTIFIER_PATTERN = Pattern.compile(
        "\\G\\s*\\p{IsAlphabetic}[\\p{IsAlphabetic}\\d]*");

    public static final Pattern DEFAULT_STRING_PATTERN = Pattern.compile(
        // "([^"\\]*(\\.[^"\\]*)*)"|\'([^\'\\]*(\\.[^\'\\]*)*)\'
        "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"|\\'([^\\'\\\\]*(\\\\.[^\\'\\\\]*)*)\\'");
    public static final Pattern DEFAULT_END_PATTERN = Pattern.compile("\\G\\s*\\Z");

    public enum TokenType {
      UNRECOGNIZED, BOF, IDENTIFIER, SYMBOL, NUMBER, STRING, EOF
    }

    public Pattern numberPattern = DEFAULT_NUMBER_PATTERN;
    public Pattern identifierPattern = DEFAULT_IDENTIFIER_PATTERN;
    public Pattern stringPattern = DEFAULT_STRING_PATTERN;
    public Pattern endPattern = DEFAULT_END_PATTERN;
    public Pattern symbolPattern;

    public int currentPosition = 0;
    public String currentValue = "";
    public TokenType currentType = TokenType.BOF;
    public boolean leadingWhitespace;

    protected final Scanner scanner;

    public Tokenizer(Scanner scanner, Iterable<String> symbols) {
      this.scanner = scanner;
      StringBuilder sb = new StringBuilder("\\G\\s*(");
      for (String symbol: symbols) {
        sb.append(Pattern.quote(symbol)).append('|');
      }
      sb.setCharAt(sb.length() - 1, ')');
      symbolPattern = Pattern.compile(sb.toString());
    }

    public void nextToken() {
      String value;
      if ((value = scanner.findWithinHorizon(identifierPattern, 0)) != null) {
        currentType = TokenType.IDENTIFIER;
      } else if ((value = scanner.findWithinHorizon(numberPattern, 0)) != null) {
        currentType = TokenType.NUMBER;
      } else if ((value = scanner.findWithinHorizon(stringPattern, 0)) != null) {
        currentType = TokenType.STRING;
      } else if ((value = scanner.findWithinHorizon(symbolPattern, 0)) != null) {
        currentType = TokenType.SYMBOL;
      } else if ((value = scanner.findWithinHorizon(endPattern, 0)) != null) {
        currentType = TokenType.EOF;
      } else if (scanner.ioException() != null) {
        throw exception("IO Exception: " + scanner.ioException().getMessage(),
            scanner.ioException());
      } else {
        currentType = TokenType.UNRECOGNIZED;
        currentValue = scanner.findWithinHorizon("\\s*.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?.?", 0);
        throw exception("Unrecognized Token.", null);
      }
      currentPosition += value.length();
      leadingWhitespace = (value.length() > 0 && value.charAt(0) <= ' ');
      currentValue = leadingWhitespace ? value.trim() : value;
    }

    @Override
    public String toString() {
      return currentType + " " + currentValue + " position: " + currentPosition;
    }

    public ParsingException exception(String message, Exception cause) {
      return new ParsingException(message
          + " Position: " + currentPosition + " Token: '" + currentValue + "' Type: " + currentType,
          currentPosition, cause);
    }
  }
}
