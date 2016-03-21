package net.tidej.expressionparser.demo.derive.tree;

import net.tidej.expressionparser.demo.derive.string2d.String2d;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.add;
import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.c;
import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.cMul;
import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.div;
import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.mul;
import static net.tidej.expressionparser.demo.derive.tree.NodeFactory.powC;

public class Derive extends Node {
  public static Node factorNode(Map.Entry<Node, Double> entry) {
    return powC(entry.getKey(), entry.getValue());
  }

  private final Node expression;
  private final String to;

  Derive(Node expression, String to) {
    this.expression = expression;
    this.to = to;
  }

  @Override
  public Node simplify(Set<String> explanation) {
    Node simplified = this.expression.simplify(explanation);
    return simplified.equals(expression)
        ? derive(expression, explanation) : NodeFactory.derive(simplified, to);
  }

  public Node derive(Node node, Set<String> explanation) {
    if (node instanceof Constant) {
      return c(0);
    }
    if (node instanceof Variable) {
      return node.toString().equals(to) ? c(1) : c(0);
    }
    if (node instanceof Product) {
      return deriveProduct((Product) node, explanation);
    }
    if (node instanceof Sum) {
      return deriveSum((Sum) node, explanation);
    }
    if (node instanceof Power) {
      return derivePower((Power) node, explanation);
    }
    if (node instanceof UnaryFunction) {
      return deriveUnarayFunction((UnaryFunction) node, explanation);
    }
    // Can't do anything.
    return this;
  }

  private Node deriveUnarayFunction(UnaryFunction node, Set<String> explanation) {
    Node derivative = node.definition.derivative;
    return mul(derivative.substitute("x", node.param), NodeFactory.derive(node.param, to));
  }

  private Node deriveSum(Sum sum, Set<String> explanation) {
    QuantifiedSet<Node> summands = sum.components;
    double c = sum.c;
    QuantifiedSet.Mutable derived = new QuantifiedSet.Mutable(false);
    if (summands.size() > 1) {
      explanation.add("Sum rule");
    }
    for (Map.Entry<Node,Double> summand: summands.entries()) {
      if (summand.getValue() != 1) {
        explanation.add("Constant factor rule");
      }
      derived.add(summand.getValue(), NodeFactory.derive(summand.getKey(), to));
    }
    return new Sum(0, derived);
  }

  private Node derivePower(Power power, Set<String> explanation) {
    explanation.add("Generalized power rule");
    Node f = power.base;
    Node g = power.exponent;
    Node f_ = NodeFactory.derive(f, to);
    Node g_ = NodeFactory.derive(g, to);
    return mul(power, add(
        mul(f_, div(g, f)), mul(g_, NodeFactory.f("ln", f))));
  }

  private Node deriveProduct(Product product, Set<String> explanation) {
    QuantifiedSet<Node> factors = product.components;
    double c = product.c;
    if (factors.size() == 0) {
      return c(0);
    }

    Iterator<Map.Entry<Node, Double>> i = factors.entries().iterator();
    if (c != 1) {
      explanation.add("Constant factor rule");
      return cMul(c, NodeFactory.derive(new Product(1, QuantifiedSet.of(i)), to));
    }
    if (factors.size() == 1) {
      Map.Entry<Node, Double> entry = i.next();
      double exponent = entry.getValue();
      Node base = entry.getKey();
      if (exponent == 1) {
        return derive(base, explanation);
      }
      if (exponent == -1) {
        explanation.add("Reciprocal rule");
        return NodeFactory.div(
            cMul(-1, NodeFactory.derive(base, to)),
            powC(base, 2));
      }
      if (base.toString().equals(to)) {
        explanation.add("power rule");
        return cMul(exponent, powC(base, exponent - 1));
      }
      return derivePower(new Power(entry.getKey(), c(entry.getValue())), explanation);
    }

    explanation.add("Product rule");
    Node left = factorNode(i.next());
    Node right = factors.size() == 2 ? factorNode(i.next())
        : new Product(1, QuantifiedSet.of(i));

    return NodeFactory.add(
        NodeFactory.mul(left, NodeFactory.derive(right, to)),
        NodeFactory.mul(NodeFactory.derive(left, to), right));
  }

  @Override
  public int getPrecedence() {
    return PRECEDENCE_PRIMARY;
  }

  public String2d toString2d(Stringify type) {
    return String2d.concat("derive", String2d.embrace(
        '(',
        String2d.concat(expression.toString2d(type), ", ", to),
        ')'));
  }
}
