package net.tidej.expressionparser.demo.calculator;

import net.tidej.expressionparser.ExpressionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

public class Calculator {

  static HashMap<String, Double> variables = new HashMap<>();

  /**
   * Processes the calls from the parser directly to a Double value.
   */
  static class DoubleProcessor implements ExpressionParser.Processor<Double> {
    @Override
    public Double infixOperator(String name, Double left, Double right) {
      switch (name.charAt(0)) {
        case '+': return left + right;
        case '-': return left - right;
        case '*': return left * right;
        case '/': return left / right;
        case '^': return Math.pow(left, right);
        default:
          throw new IllegalArgumentException();
      }
    }

    @Override
    public Double prefixOperator(String name, Double argument) {
      return name.equals("-") ? -argument : argument;
    }

    @Override
    public Double suffixOperator(String name, Double argument) {
      throw new UnsupportedOperationException(name);
    }

    @Override
    public Double numberLiteral(String value) {
      return Double.parseDouble(value);
    }

    @Override
    public Double stringLiteral(char quote, String value) {
      throw new UnsupportedOperationException("Strings not supported");
    }

    @Override
    public Double primarySymbol(String value) {
      throw new UnsupportedOperationException("symbol " + value);
    }

    @Override
    public Double identifier(String name) {
      Double value = variables.get(name);
      if (value == null) {
        throw new IllegalArgumentException("Undeclared variable: " + name);
      }
      return value;
    }

    @Override
    public Double group(String paren, List<Double> elements) {
      return elements.get(0);
    }

    /** 
     * Delegates function calls to Math via reflection.
     */
    @Override
    public Double call(String identifier, String bracket, List<Double> arguments) {
      if (identifier.equals("ln")) {
        identifier = "log";
      }
      Class<?>[] argTypes = new Class<?>[arguments.size()];
      Object[] args = new Object[arguments.size()];
      for (int i = 0; i < argTypes.length; i++) {
        argTypes[i] = Double.TYPE;
        args[i] = arguments.get(i);
      }
      try {
        return (Double) Math.class.getMethod(identifier, argTypes).invoke(null, args);
      } catch (Exception e) {
        throw new UnsupportedOperationException(identifier, e);
      }
    }

    @Override
    public Double apply(Double base, String bracket, List<Double> arguments) {
      throw new UnsupportedOperationException("apply");
    }
  }

  /**
   * Registers operations and precedences.
   */
  public static void main(String[] args) throws IOException {
    variables.put("tau", 2 * Math.PI);
    variables.put("pi", Math.PI);
    variables.put("e", Math.E);
    ExpressionParser<Double> parser = new ExpressionParser<Double>(new DoubleProcessor());
    parser.addCallBrackets("(", ",", ")");
    parser.addGroupBrackets(4, "(", null, ")");
      parser.addOperators(ExpressionParser.OperatorType.INFIX_RTL, 3, "^");
      parser.addOperators(ExpressionParser.OperatorType.PREFIX, 2, "+", "-");
      parser.addOperators(ExpressionParser.OperatorType.INFIX, 1, "*", "/");
      parser.addOperators(ExpressionParser.OperatorType.INFIX, 0, "+", "-");
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      System.out.print("Expression? ");
      String input = reader.readLine();
      if (input == null || input.isEmpty()) {
        break;
      }
      try {
        System.out.println("Result:     " + parser.parse(input));
      } catch (ExpressionParser.ParsingException e) {
        System.out.print("Error -----");
        for (int i = 0; i < e.position; i++) {
          System.out.print("-");
        }
        System.out.println("^: " + e.getMessage());
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }
  }
}
