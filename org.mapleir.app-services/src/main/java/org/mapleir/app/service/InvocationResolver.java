package org.mapleir.app.service;

import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public interface InvocationResolver {

	MethodNode resolveStaticCall(String owner, String name, String desc);
	
	MethodNode resolveVirtualInitCall(String owner, String desc);

	Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict);

	default Set<MethodNode> resolveVirtualCalls(MethodNode m, boolean strict) {
		return resolveVirtualCalls(m.owner.name, m.name, m.desc, strict);
	}
	
	boolean hasReceiverMethod(String owner, String name, String desc, boolean isStatic);
	
	FieldNode findStaticField(String owner, String name, String desc);
	
	FieldNode findVirtualField(String owner, String name, String desc);
	
	default FieldNode findField(String owner, String name, String desc, boolean isStatic) {
		if(isStatic) {
			return findStaticField(owner, name, desc);
		} else {
			return findVirtualField(owner, name, desc);
		}
	}

	// FIXME: revise
	/**
	 * Find methods matching the name and desc in all branches related to the class. Note this is much broader
	 * than actual resolution, and is best used for renaming purposes.
	 * @param cn base class
	 * @param name method name
	 * @param desc method descriptor
	 * @param exact if true, match return types exactly; otherwise congruent types are also matched
	 * @return all matching methods
	 */
	Set<MethodNode> getHierarchyMethodChain(ClassNode cn, String name, String desc, boolean exact);
}
