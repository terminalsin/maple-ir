package org.mapleir.stdlib.collections.graph;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class FastUndirectedGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> implements FastGraph<N, E>{

	protected final Map<N, Set<E>> map;
	
	public FastUndirectedGraph() {
		map = createMap();
	}

	@Override
	public Set<N> vertices() {
		return map.keySet();
	}

	@Override
	public void addVertex(N n) {
		if(!map.containsKey(n)) {
			map.put(n, new HashSet<>());
		}
	}

	@Override
	public void removeVertex(N n) {
		Set<E> set = map.remove(n);
		for(E e : set) {
			map.get(e.dst).remove(e);
		}
	}

	@Override
	public boolean containsVertex(N n) {
		return map.containsKey(n);
	}

	@Override
	public void addEdge(N n, E e) {
		if(!map.containsKey(n)) {
			map.put(n, new HashSet<>());
		}
		map.get(n).add(e);
		
		N dst = e.dst;
		if(!map.containsKey(dst)) {
			map.put(dst, new HashSet<>());
		}
		map.get(dst).add(e);
	}

	@Override
	public void removeEdge(N n, E e) {
		if(map.containsKey(n)) {
			map.get(n).remove(e);
		}
		N dst = e.dst;
		if(map.containsKey(dst)) {
			map.get(dst).remove(e);
		}
	}

	@Override
	public boolean containsEdge(N n, E e) {
		return map.containsKey(n) && map.get(n).contains(e);
	}

	@Override
	public Set<E> getEdges(N n) {
		return map.get(n);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public void replace(N old, N n) {
		Set<E> succs = getEdges(old);
		addVertex(n);
		for(E succ : new HashSet<>(succs)) {
			E newEdge = clone(succ, old, n);
			removeEdge(old, succ);
			addEdge(n, newEdge);
		}
		removeVertex(old);
	}

	@Override
	public void clear() {
		map.clear();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("map {\n");
		for(Entry<N, Set<E>> e : map.entrySet()) {
			sb.append("   ").append(e.getKey()).append("  ").append(e.getValue()).append("\n");
		}
		sb.append("}");
		return sb.toString();
	}
}