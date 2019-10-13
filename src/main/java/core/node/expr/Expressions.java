package core.node.expr;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.node.FrameBuilder;
import core.node.FrameBuilderNodeGen;
import core.values.BigNumber;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  static Arg arg(int i) { return new Arg(i); }
  static Var var(FrameSlot slot) { return VarNodeGen.create(slot); }

  static Lambda lam(RootCallTarget callTarget) { return Lambda.create(callTarget); }
  static Lambda lam(int arity, RootCallTarget callTarget) { return Lambda.create(arity, callTarget); }
  static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, callTarget); }
  static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, int arity, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, arity, callTarget); }

  static App app(Expression rator, Expression... rands) {
    return new App(rator, rands);
  }
  static FrameBuilder put(FrameSlot slot, Expression value) { return FrameBuilderNodeGen.create(slot,value); }

  static Add add(Expression x, Expression y) { return AddNodeGen.create(x,y); }
  static Expression booleanLiteral(boolean b) {
    return new Expression() {
      @Override public Object execute(VirtualFrame frame) { return b; }
      @Override public boolean executeBoolean(VirtualFrame frame) { return b; }
    };
  }
  static Expression intLiteral(int i) {
    return new Expression() {
      @Override public Object execute(VirtualFrame frame) { return i; }
      @Override public int executeInteger(VirtualFrame frame) { return i; }
    };
  }
  static Expression bigLiteral(BigNumber i) {
    return new Expression() {
      @Override public Object execute(VirtualFrame frame) { return i; }
      @Override public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
        try {
          if (i.fitsInInt()) return i.asInt();
        } catch (UnsupportedMessageException e) {}
        throw new UnexpectedResultException(i);
      }
    };
  }
}