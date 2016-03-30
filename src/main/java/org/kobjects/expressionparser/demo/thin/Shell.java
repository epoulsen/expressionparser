package org.kobjects.expressionparser.demo.thin;


import org.kobjects.expressionparser.demo.thin.type.FunctionType;
import org.kobjects.expressionparser.demo.thin.type.Type;
import org.kobjects.expressionparser.demo.thin.ast.Classifier;
import org.kobjects.expressionparser.demo.thin.ast.Processor;
import org.kobjects.expressionparser.demo.thin.ast.Statement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;

public class Shell {

  static Processor processor = new Processor();
  static org.kobjects.expressionparser.demo.thin.EvaluationContext evaluationContext;
  static org.kobjects.expressionparser.demo.thin.ParsingContext parsingContext;

  static void load(String url) {
    try {
      InputStream is;
      if (url.indexOf(':') != -1) {
        URLConnection connection = new URL(url).openConnection();
        connection.setDoInput(true);
        is = connection.getInputStream();
      } else {
        System.out.println("Url: " + url);
        is = Shell.class.getResourceAsStream(url);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

      Statement parsed = processor.process(parsingContext, reader);

      reader.close();
      is.close();

      //adjustLocals();


      System.out.println("Parsed: " + parsed);
//        s.eval(evaluationContext);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /*
  static void adjustLocals() {
    if (parser.parsingContext.locals.size() > evaluationContext.locals.length) {
      Object[] newLocals = new Object[parser.parsingContext.locals.size()];
      System.arraycopy(evaluationContext.locals, 0, newLocals, 0, evaluationContext.locals.length);
      evaluationContext.locals = newLocals;
    }
  }
  */

  public static void main(String[] args) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    Classifier rootClass = new Classifier(Classifier.Kind.CLASS, "Root");
    Classifier consoleClass = new Classifier(Classifier.Kind.CLASS, "Console");
    consoleClass.addMethod("log", new Applicable() {
      @Override
      public FunctionType type() {
        return new FunctionType(Type.VOID, Type.STRING);
      }

      @Override
      public Object apply(org.kobjects.expressionparser.demo.thin.EvaluationContext context) {
        System.out.println(context.getLocal(0));
        return null;
      }
    });
    rootClass.addMethod("load", new Applicable() {
      @Override
      public FunctionType type() {
        return new FunctionType(Type.VOID, Type.STRING);
      }

      @Override
      public Object apply(org.kobjects.expressionparser.demo.thin.EvaluationContext context) {
        load(String.valueOf(context.locals[0]));
        return null;
      }
    });
    rootClass.addField("console", consoleClass);
    Instance console = consoleClass.newInstance(null);
    Instance root = rootClass.newInstance(null);
    root.setField(rootClass.members.get("console").fieldIndex, console);

    parsingContext = new org.kobjects.expressionparser.demo.thin.ParsingContext(rootClass);
    evaluationContext = new org.kobjects.expressionparser.demo.thin.EvaluationContext(root, null);

    while (true) {
      System.out.print("Expression? ");
      String line = reader.readLine();
      if (!line.endsWith(";")) {
        line += ";";
      }
      try {
        Statement statement = processor.process(parsingContext, new StringReader(line));
        System.out.println("Parsed:     " + statement);

        if (statement.kind == Statement.Kind.EXPRESSION) {
          statement.kind = Statement.Kind.RETURN;
        }

        System.out.println("Result:     " + statement.eval(evaluationContext));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
