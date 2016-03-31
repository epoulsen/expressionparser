package org.kobjects.expressionparser.demo.thinscript.expression;

import org.kobjects.expressionparser.demo.thinscript.CodePrinter;
import org.kobjects.expressionparser.demo.thinscript.EvaluationContext;
import org.kobjects.expressionparser.demo.thinscript.parser.ParsingContext;
import org.kobjects.expressionparser.demo.thinscript.statement.Classifier;

public class This extends Node {
  public This(Classifier type) {
    super(type);
  }

  @Override
  public Object eval(EvaluationContext context) {
    return context.self;
  }

  @Override
  public void print(CodePrinter cp) {
    cp.append("this");
  }

  @Override
  public Node resolve(ParsingContext context) {
    throw new UnsupportedOperationException();
  }
}
