package org.mapleir.ir.cfg.builder.ssaopt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.FieldLoadExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.locals.Local;

public class LatestValue {
	
	public static final int PARAM = 0, PHI = 1, CONST = 2, VAR = 3;
	private static final String[] TO_STRING = new String[]{"param", "phi", "phi", "other"};
	
	private final ControlFlowGraph cfg;
	private final int type;
	private final Object rvalue;
	private final Object svalue;
	private final List<Constraint> constraints;
	
	public LatestValue(ControlFlowGraph cfg, int type, Object val) {
		this(cfg, type, val, val);
	}
	
	public LatestValue(ControlFlowGraph cfg, int type, Object rvalue, Object svalue) {
		this.cfg = cfg;
		this.type = type;
		this.rvalue = rvalue;
		this.svalue = svalue;
		constraints = new ArrayList<>();
	}
	
	public boolean hasConstraints() {
		return !constraints.isEmpty();
	}
	
	public int getType() {
		return type;
	}
	
	public Object getRealValue() {
		return rvalue;
	}
	
	public Object getSuggestedValue() {
		return svalue;
	}
	
	public void importConstraints(LatestValue v) {
		constraints.addAll(v.constraints);
	}
	
	public void makeConstraints(Expression e) {
		for(Statement s : e.enumerate()) {
			int op = s.getOpcode();
			if(op == Opcode.FIELD_LOAD) {
				FieldConstraint c = new FieldConstraint((FieldLoadExpression) s);
				constraints.add(c);
			} else if(ConstraintUtil.isInvoke(s)) {
				constraints.add(new InvokeConstraint());
			} else if(op == Opcode.ARRAY_LOAD) {
				constraints.add(new ArrayConstraint());
			}
		}
	}
	
	private Set<Statement> findReachable(Statement from, Statement to) {
		Set<Statement> res = new HashSet<>();
		BasicBlock f = from.getBlock();
		BasicBlock t = to.getBlock();
		
		int end = f == t ? f.indexOf(to) : f.size();
		for(int i=f.indexOf(from); i < end; i++) {
			res.add(f.get(i));
		}
		
		if(f != t) {
			for(BasicBlock r : cfg.wanderAllTrails(f, t)) {
				res.addAll(r);
			}
		}
		
		return res;
	}

	public boolean canPropagate(AbstractCopyStatement def, Statement use, Statement tail, boolean debug) {
		Local local = def.getVariable().getLocal();
		
		Set<Statement> path = findReachable(def, use);
		path.remove(def);
		path.add(use);
		
		for(Statement stmt : path) {
			if(stmt != use) {
				for(Statement s : stmt.enumerate()) {
					for(Constraint c : constraints) {
						if(c.fails(s)) {
							if(debug) {
								System.out.println("  Fail: " + c);
								System.out.println("  stmt: " + stmt);
								System.out.println("     c: " + s);
							}
							return false;
						}
					}
				}
			} else {
				if(constraints.size() > 0) {
					for(Statement s : stmt.execEnumerate()) {
						if(s == tail && (s.getOpcode() == Opcode.LOCAL_LOAD && ((VarExpression) s).getLocal() == local)) {
							break;
						} else {
							for(Constraint c : constraints) {
								if(c.fails(s)) {
									if(debug) {
										System.out.println("  Fail: " + c);
										System.out.println("  stmt: " + stmt);
										System.out.println("     c: " + s);
									}
									return false;
								}
							}
						}
					}
				}
			}
		}
		
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("LatestValue: {type=%s, rval=%s, sval=%s, cons=%d}", TO_STRING[type], rvalue, svalue, constraints.size());
	}
}