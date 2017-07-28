package org.mapleir.deob.interproc.geompa;

import java.util.HashMap;
import java.util.Map;

public final class MethodContext implements MapleMethodOrMethodContext {
	private static final Map<MethodContext, MethodContext> map = new HashMap<>();
	
	private MapleMethod method;
	private Context context;

	private MethodContext(MapleMethod method, Context context) {
		this.method = method;
		this.context = context;
	}

	@Override
	public MapleMethod method() {
		return method;
	}
	
	@Override
	public Context context() {
		return context;
	}

	@Override
	public int hashCode() {
		return method.hashCode() + context.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MethodContext) {
			MethodContext other = (MethodContext) o;
			return method.equals(other.method) && context.equals(other.context);
		}
		return false;
	}

	public static MapleMethodOrMethodContext v(MapleMethod method, Context context) {
		if (context == null)
			return method;
		MethodContext probe = new MethodContext(method, context);
		MethodContext ret = map.get(probe);
		if (ret == null) {
			map.put(probe, probe);
			return probe;
		}
		return ret;
	}

	@Override
	public String toString() {
		return "Method " + method + " in context " + context;
	}
}