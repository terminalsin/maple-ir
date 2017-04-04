package org.mapleir.ir.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.DummyEdge;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraphDumper {
	public static void dump(ControlFlowGraph cfg, MethodNode m) {
		// TODO: Use tarjan scc to linearize CFG and rebuild ranges
		
		m.instructions.removeAll(true);
		m.tryCatchBlocks.clear();

		m.visitCode();
		
		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		List<BasicBlock> blocks = new ArrayList<>(cfg.vertices());
		
		for (BasicBlock b : blocks) {
			m.visitLabel(b.getLabel());
			for (Stmt stmt : b) {
				stmt.toCode(m, null);
			}
		}
		
		ListIterator<BasicBlock> it = blocks.listIterator();
		while(it.hasNext()) {
			BasicBlock b = it.next();
			
			for(FlowEdge<BasicBlock> e: cfg.getEdges(b)) {
				if(e.getType() == FlowEdges.IMMEDIATE) {
					if(it.hasNext()) {
						BasicBlock n = it.next();
						it.previous();
						
						if(n != e.dst) {
							throw new IllegalStateException("Illegal flow " + e + " > " + n);
						}
					} else {
						throw new IllegalStateException("Trailing " + e);
					}
				}
			}
		}

		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
//			System.out.println("RANGE: " + er);
			Type type = null;
			Set<Type> typeSet = er.getTypes();
			if (typeSet.size() == 0 || typeSet.size() > 1) {
				// TODO: fix base exception
				type = ExceptionAnalysis.THROWABLE;
			} else {
				// size == 1
				type = typeSet.iterator().next();
			}
			List<BasicBlock> range = er.get();
			Label start = range.get(0).getLabel();
//			if(range.get(0).getId().equals("C")) {
//				start = range.get(1).getLabel();
//			}
			Label end = null;
			BasicBlock endBlock = range.get(range.size() - 1);
			BasicBlock im = endBlock.getImmediate();
			if (im == null) {
				int endIndex = blocks.indexOf(endBlock);
				if (endIndex + 1 < blocks.size()) {
					end = blocks.get(blocks.indexOf(endBlock) + 1).getLabel();
				} else {
					LabelNode label = new LabelNode();
					m.visitLabel(label.getLabel());
					BasicBlock newExit = new BasicBlock(cfg, endBlock.getNumericId() + 1, label);
					cfg.addVertex(newExit);
					cfg.addEdge(endBlock, new DummyEdge<>(endBlock, newExit));
					end = label.getLabel();
				}
			} else {
				end = im.getLabel();
			}
			Label handler = er.getHandler().getLabel();
			m.visitTryCatchBlock(start, end, handler, type.getInternalName());
		}
		m.visitEnd();
	}
}
