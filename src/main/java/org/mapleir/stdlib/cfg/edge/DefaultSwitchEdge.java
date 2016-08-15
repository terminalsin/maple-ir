package org.mapleir.stdlib.cfg.edge;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.tree.AbstractInsnNode;

public class DefaultSwitchEdge<N extends FastGraphVertex> extends FlowEdge<N> {
	
	public final AbstractInsnNode insn;

	public DefaultSwitchEdge(N src, N dst, AbstractInsnNode insn) {
		super(DEFAULT_SWITCH, src, dst);
		this.insn = insn;
	}
	
	@Override
	public String toGraphString() {
		return "Default case";
	}
	
	@Override
	public String toString() {
		return String.format("DefaultSwitch #%s -> #%s", src.getId(), dst.getId());
	}
	
	@Override
	public String toInverseString() {
		return String.format("DefaultSwitch #%s <- #%s", dst.getId(), src.getId());
	}

	@Override
	public DefaultSwitchEdge<N> clone(N src, N dst) {
		return new DefaultSwitchEdge<>(src, dst, insn);
	}
}