package org.kobjects.expressionparser.demo.thin.type;

import org.kobjects.expressionparser.demo.thin.ParsingContext;

public class UnresolvedType implements Type {
  private final String name;

  public UnresolvedType(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return "unresolved<" + name + ">";
  }

  @Override
  public Type resolveType(ParsingContext context) {
    Object resolved = context.resolve(name);
    if (!(resolved instanceof Type)) {
      throw new RuntimeException("Not a type: " + name);
    }
    return (Type) resolved;
  }

  @Override
  public String toString() {
    return name();
  }
}
